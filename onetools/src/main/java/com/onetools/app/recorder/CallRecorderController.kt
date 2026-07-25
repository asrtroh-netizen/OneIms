package com.onetools.app.recorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.onetools.app.channel.ShizukuChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Orchestrates consent → on-demand overlay prompt → Shizuku UserService capture.
 * Never auto-records without an explicit tap on the floating button.
 */
class CallRecorderController(private val context: Context) {
    private val client = ShellRecorderClient(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val monitor = CallStateMonitor(context.applicationContext) { phase ->
        onCallPhase(phase)
    }
    private val currentFile = AtomicReference<java.io.File?>(null)
    private var promptOverlay: RecordPromptOverlay? = null

    /** When true, OFFHOOK shows floating record button (does not start recording). */
    @Volatile var promptOnCallEnabled: Boolean = false
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

    fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun startMonitoring() {
        if (!hasPhonePermission()) {
            lastStatus = "缺少 READ_PHONE_STATE"
            return
        }
        ensurePromptOverlay()
        monitor.start(executor)
        lastStatus = "通话接通后将弹出录音按钮"
    }

    fun stopMonitoring() {
        monitor.stop()
        promptOverlay?.hide()
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
            promptOverlay?.setRecording(true)
            file.absolutePath
        }
    }

    fun stopManual(): Result<Unit> = runCatching {
        client.stop().getOrThrow()
        val f = currentFile.getAndSet(null)
        lastStatus = if (f != null) "已保存 ${f.name}" else "已停止"
        promptOverlay?.setRecording(false)
    }

    fun toggleFromOverlay() {
        if (currentFile.get() != null) {
            stopManual()
        } else {
            startManual().onFailure { lastStatus = "开录失败: ${it.message}" }
        }
    }

    fun probeOemMatrix(): Result<String> = client.probeSources()

    fun dispose() {
        stopMonitoring()
        promptOverlay?.hide()
        promptOverlay = null
        client.unbind()
        executor.shutdownNow()
    }

    private fun ensurePromptOverlay() {
        if (promptOverlay != null) return
        promptOverlay = RecordPromptOverlay(context.applicationContext) {
            toggleFromOverlay()
        }
    }

    private fun onCallPhase(phase: CallPhase) {
        if (!promptOnCallEnabled) return
        when (phase) {
            CallPhase.OFFHOOK -> {
                ensurePromptOverlay()
                if (!canDrawOverlay()) {
                    lastStatus = "需要悬浮窗权限才能弹出录音按钮"
                    return
                }
                promptOverlay?.show(isRecording = currentFile.get() != null)
                lastStatus = "已接通 · 点击悬浮按钮开始录音"
            }
            CallPhase.IDLE -> {
                if (currentFile.get() != null) {
                    stopManual()
                }
                promptOverlay?.hide()
                lastStatus = "通话结束"
            }
            CallPhase.RINGING -> lastStatus = "来电振铃…"
        }
    }
}
