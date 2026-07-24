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
 * Clean-room Shizuku UserService: shell UID audio capture.
 * Prefers dual UPLINK+DOWNLINK stereo; falls back through OEM ladder.
 */
class ShellRecorderService : IShellRecorder.Stub {
    private var hostContext: Context? = null
    private var uplink: AudioRecord? = null
    private var downlink: AudioRecord? = null
    private var mono: AudioRecord? = null
    private var writerThread: Thread? = null
    @Volatile private var recording = false
    @Volatile private var lastError: String? = null
    @Volatile private var activeSource: String = ""

    constructor()
    constructor(context: Context) {
        hostContext = context
    }

    fun destroy() {
        runCatching { stopRecording() }
    }

    override fun ping(): String = "onetools-shell-recorder uid=${android.os.Process.myUid()}"

    override fun probeSources(): String {
        val ids = listOf(
            MediaRecorder.AudioSource.VOICE_UPLINK to "VOICE_UPLINK",
            MediaRecorder.AudioSource.VOICE_DOWNLINK to "VOICE_DOWNLINK",
            MediaRecorder.AudioSource.VOICE_CALL to "VOICE_CALL",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
            MediaRecorder.AudioSource.MIC to "MIC",
        )
        return ids.joinToString(";") { (id, name) ->
            val rec = openRecord(id, start = false)
            val ok = rec != null
            runCatching { rec?.release() }
            "$name=${if (ok) "ok" else "fail"}"
        }
    }

    override fun startRecording(absolutePath: String, preferredSource: Int): Int {
        if (recording) {
            lastError = "already recording"
            return 1
        }
        lastError = null
        val out = File(absolutePath)
        out.parentFile?.mkdirs()

        // 1) Dual uplink+downlink stereo (best effort both sides)
        val up = openRecord(MediaRecorder.AudioSource.VOICE_UPLINK, start = true)
        val down = openRecord(MediaRecorder.AudioSource.VOICE_DOWNLINK, start = true)
        if (up != null && down != null) {
            uplink = up
            downlink = down
            activeSource = "STEREO_UPLINK+DOWNLINK"
            return beginWrite(out, stereo = true)
        }
        runCatching { up?.stop(); up?.release() }
        runCatching { down?.stop(); down?.release() }
        uplink = null
        downlink = null

        // 2) Mono ladder
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
            opened = openRecord(source, start = true)
            if (opened != null) {
                usedName = name
                break
            }
        }
        if (opened == null) {
            lastError = "no audio source available under shell uid"
            return 2
        }
        mono = opened
        activeSource = usedName
        return beginWrite(out, stereo = false)
    }

    private fun beginWrite(out: File, stereo: Boolean): Int {
        recording = true
        writerThread = Thread({
            try {
                if (stereo) {
                    writeStereoWav(uplink!!, downlink!!, out)
                } else {
                    writeMonoWav(mono!!, out)
                }
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "record failed", t)
            } finally {
                recording = false
                releaseAll()
            }
        }, "onetools-wav-writer").also { it.start() }
        return 0
    }

    override fun stopRecording() {
        recording = false
        runCatching { uplink?.stop() }
        runCatching { downlink?.stop() }
        runCatching { mono?.stop() }
        runCatching { writerThread?.join(5_000) }
        writerThread = null
        releaseAll()
    }

    override fun isRecording(): Boolean = recording

    override fun lastError(): String = lastError.orEmpty()

    override fun activeSourceName(): String = activeSource

    private fun releaseAll() {
        runCatching { uplink?.release() }
        runCatching { downlink?.release() }
        runCatching { mono?.release() }
        uplink = null
        downlink = null
        mono = null
    }

    private fun openRecord(source: Int, start: Boolean): AudioRecord? {
        val sampleRate = SAMPLE_RATE
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
                return@runCatching null
            }
            if (start) {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    record.release()
                    return@runCatching null
                }
            }
            record
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

    private fun writeMonoWav(record: AudioRecord, out: File) {
        RandomAccessFile(out, "rw").use { raf ->
            raf.write(ByteArray(44))
            val buf = ShortArray(2048)
            var totalBytes = 0
            while (recording) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until n) bytes.putShort(buf[i])
                    raf.write(bytes.array())
                    totalBytes += n * 2
                } else if (n < 0) {
                    lastError = "AudioRecord.read=$n"
                    break
                }
            }
            raf.seek(0)
            writeWavHeader(raf, totalBytes, SAMPLE_RATE, channels = 1, bits = 16)
        }
    }

    private fun writeStereoWav(up: AudioRecord, down: AudioRecord, out: File) {
        RandomAccessFile(out, "rw").use { raf ->
            raf.write(ByteArray(44))
            val upBuf = ShortArray(1024)
            val downBuf = ShortArray(1024)
            var totalBytes = 0
            while (recording) {
                val nu = up.read(upBuf, 0, upBuf.size)
                val nd = down.read(downBuf, 0, downBuf.size)
                if (nu < 0 || nd < 0) {
                    lastError = "stereo read u=$nu d=$nd"
                    break
                }
                val frames = minOf(nu, nd)
                if (frames <= 0) continue
                val bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until frames) {
                    // L = uplink (near), R = downlink (far)
                    bytes.putShort(upBuf[i])
                    bytes.putShort(downBuf[i])
                }
                raf.write(bytes.array())
                totalBytes += frames * 4
            }
            raf.seek(0)
            writeWavHeader(raf, totalBytes, SAMPLE_RATE, channels = 2, bits = 16)
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
        buffer.putShort(1)
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
        private const val SAMPLE_RATE = 44_100
    }
}
