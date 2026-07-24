package com.onetools.app.recorder

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room Shizuku UserService: runs as shell UID and opens privileged audio sources.
 * No third-party recorder source was copied.
 */
class ShellRecorderService : IShellRecorder.Stub {
    private var hostContext: Context? = null
    private var audioRecord: AudioRecord? = null
    private var writerThread: Thread? = null
    @Volatile private var recording = false
    @Volatile private var lastError: String? = null
    @Volatile private var activeSource: String = ""

    constructor()
    constructor(context: Context) {
        hostContext = context
    }

    /** Called by Shizuku when tearing down the user service. */
    fun destroy() {
        runCatching { stopRecording() }
    }

    override fun ping(): String = "onetools-shell-recorder uid=${android.os.Process.myUid()}"

    override fun startRecording(absolutePath: String, preferredSource: Int): Int {
        if (recording) {
            lastError = "already recording"
            return 1
        }
        lastError = null
        val out = File(absolutePath)
        out.parentFile?.mkdirs()

        val sources = buildList {
            if (preferredSource > 0) add(preferredSource to nameOf(preferredSource))
            add(MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL")
            add(MediaRecorder.AudioSource.VOICE_DOWNLINK to "VOICE_DOWNLINK")
            add(MediaRecorder.AudioSource.VOICE_UPLINK to "VOICE_UPLINK")
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION")
            add(MediaRecorder.AudioSource.MIC to "MIC")
        }.distinctBy { it.first }

        var opened: AudioRecord? = null
        var usedName = ""
        for ((source, name) in sources) {
            opened = openRecord(source)
            if (opened != null) {
                usedName = name
                break
            }
        }
        if (opened == null) {
            lastError = "no audio source available under shell uid"
            return 2
        }

        audioRecord = opened
        activeSource = usedName
        recording = true
        writerThread = Thread({
            try {
                writeWav(opened, out)
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "record failed", t)
            } finally {
                recording = false
                runCatching { opened.release() }
                audioRecord = null
            }
        }, "onetools-wav-writer").also { it.start() }
        return 0
    }

    override fun stopRecording() {
        recording = false
        runCatching { audioRecord?.stop() }
        runCatching { writerThread?.join(5_000) }
        writerThread = null
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    override fun isRecording(): Boolean = recording

    override fun lastError(): String = lastError.orEmpty()

    override fun activeSourceName(): String = activeSource

    private fun openRecord(source: Int): AudioRecord? {
        val sampleRate = 44_100
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (minBuf <= 0) return null
        return runCatching {
            val builder = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(encoding)
                        .setChannelMask(channel)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf * 2)
            maybeSetShellContext(builder)
            val record = builder.build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    record.release()
                    null
                } else {
                    record
                }
            }
        }.getOrElse {
            lastError = "source=$source ${it.message}"
            null
        }
    }

    private fun maybeSetShellContext(builder: AudioRecord.Builder) {
        if (Build.VERSION.SDK_INT < 31) return
        val ctx = hostContext ?: return
        val shellCtx = runCatching {
            ctx.createPackageContext(
                "com.android.shell",
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrNull() ?: ctx
        runCatching {
            val m = AudioRecord.Builder::class.java.getMethod("setContext", Context::class.java)
            m.invoke(builder, shellCtx)
        }
    }

    private fun writeWav(record: AudioRecord, out: File) {
        RandomAccessFile(out, "rw").use { raf ->
            // placeholder header
            raf.write(ByteArray(44))
            val buf = ByteArray(record.bufferSizeInFrames.coerceAtLeast(2048) * 2)
            var total = 0L
            while (recording) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    raf.write(buf, 0, n)
                    total += n
                } else if (n < 0) {
                    lastError = "AudioRecord.read=$n"
                    break
                }
            }
            // rewrite WAV header
            raf.seek(0)
            writeWavHeader(raf, total.toInt(), sampleRate = 44_100, channels = 1, bits = 16)
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataLen: Int, sampleRate: Int, channels: Int, bits: Int) {
        val byteRate = sampleRate * channels * bits / 8
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataLen)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * bits / 8).toShort())
        buffer.putShort(bits.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataLen)
        raf.write(buffer.array())
    }

    private fun nameOf(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
        MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "SRC_$source"
    }

    companion object {
        private const val TAG = "OneToolsShellRec"
    }
}
