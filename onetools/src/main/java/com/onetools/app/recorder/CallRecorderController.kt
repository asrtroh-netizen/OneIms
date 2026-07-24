package com.onetools.app.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.onetools.app.channel.ShizukuChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Orchestrates consent → Shizuku UserService capture → call-state auto start/stop.
 */
class CallRecorderController(private val context: Context) {
    private val client = ShellRecorderClient(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val monitor = CallStateMonitor(context.applicationContext) { phase ->
        onCallPhase(phase)
    }
    private val currentFile = AtomicReference<java.io.File?>(null)
    @Volatile var autoEnabled: Boolean = false
    @Volatile var lastStatus: String = "idle"
        private set
    @Volatile var lastSource: String = ""
        private set

    fun hasPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startMonitoring() {
        if (!hasPhonePermission()) {
            lastStatus = "缺少 READ_PHONE_STATE"
            return
        }
        monitor.start(executor)
        lastStatus = "监听通话状态中"
    }

    fun stopMonitoring() {
        monitor.stop()
        runCatching { stopManual() }
        lastStatus = "已停止监听"
    }

    fun startManual(): Result<String> {
        if (!RecorderConsent.isAccepted(context)) return Result.failure(IllegalStateException("未确认法律提示"))
        if (!ShizukuChannel.isServiceReady()) return Result.failure(IllegalStateException("Shizuku 未就绪"))
        if (!hasMicPermission()) return Result.failure(IllegalStateException("缺少 RECORD_AUDIO"))
        val file = RecordingStore.newFile(context)
        return client.startToFile(file, MediaRecorder.AudioSource.VOICE_CALL).map { source ->
            currentFile.set(file)
            lastSource = source
            lastStatus = "录音中 · $source · ${file.name}"
            file.absolutePath
        }
    }

    fun stopManual(): Result<Unit> = runCatching {
        client.stop().getOrThrow()
        val f = currentFile.getAndSet(null)
        lastStatus = if (f != null) "已保存 ${f.name}" else "已停止"
    }

    fun probeOemMatrix(): Result<String> = client.probeSources()

    fun dispose() {
        stopMonitoring()
        client.unbind()
        executor.shutdownNow()
    }

    private fun onCallPhase(phase: CallPhase) {
        if (!autoEnabled) return
        when (phase) {
            CallPhase.OFFHOOK -> {
                if (currentFile.get() == null) {
                    startManual().onFailure { lastStatus = "自动开录失败: ${it.message}" }
                }
            }
            CallPhase.IDLE -> {
                if (currentFile.get() != null) {
                    stopManual()
                }
            }
            CallPhase.RINGING -> lastStatus = "来电振铃…"
        }
    }
}
