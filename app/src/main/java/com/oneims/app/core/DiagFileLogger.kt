package com.oneims.app.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.oneims.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 详细诊断文件日志：环形 session 日志 + 崩溃落盘 + 一键导出包。
 * 与 UI 内存日志并行；进程被杀后仍可从 filesDir 取证。
 */
object DiagFileLogger {
    private const val TAG = "OneIMS-Diag"
    private const val DIR_NAME = "diag_logs"
    private const val SESSION_FILE = "session.log"
    private const val MAX_SESSION_BYTES = 1_200_000L
    private const val MAX_CRASH_FILES = 5
    private const val TRIM_KEEP_BYTES = 800_000L

    enum class Level { D, I, W, E }

    @Volatile
    private var appContext: Context? = null

    private val ready = AtomicBoolean(false)
    private val crashInstalled = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "oneims-diag-log").apply { isDaemon = true }
    }
    private val timeFmt = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        dir(app).mkdirs()
        if (ready.compareAndSet(false, true)) {
            i("boot", "DiagFileLogger ready · ${OemDeviceCompat.summaryLine()}")
            i(
                "boot",
                "app=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                    "channel=${BuildConfig.CHANNEL_LINE} sdk=${Build.VERSION.SDK_INT} " +
                    "model=${Build.MANUFACTURER}/${Build.MODEL}",
            )
        }
    }

    fun installCrashHandler() {
        if (!crashInstalled.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { persistCrash(thread, error) }
            previous?.uncaughtException(thread, error)
                ?: run {
                    // 无上游 handler 时保持默认杀进程语义
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
        }
        i("boot", "UncaughtExceptionHandler installed")
    }

    fun d(tag: String, message: String, error: Throwable? = null) =
        write(Level.D, tag, message, error)

    fun i(tag: String, message: String, error: Throwable? = null) =
        write(Level.I, tag, message, error)

    fun w(tag: String, message: String, error: Throwable? = null) =
        write(Level.W, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        write(Level.E, tag, message, error)

    fun breadcrumb(event: String) = i("crumb", event)

    fun ui(message: String) = i("UI", message)

    fun tail(maxChars: Int = 48_000): String {
        val ctx = appContext ?: return "(logger not ready)"
        val file = sessionFile(ctx)
        if (!file.exists()) return "(no session log yet)"
        return runCatching {
            val text = file.readText()
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        }.getOrElse { "(read failed: ${it.message})" }
    }

    fun clearSession() {
        val ctx = appContext ?: return
        io.execute {
            runCatching {
                sessionFile(ctx).writeText("")
                i("boot", "session log cleared by user")
            }
        }
    }

    /**
     * 导出诊断包到 cache，返回文件；失败返回 null。
     * 内容含设备头、策略、session 尾、最近崩溃。
     */
    fun exportBundle(context: Context): File? {
        init(context)
        val ctx = appContext ?: context.applicationContext
        val outDir = File(ctx.cacheDir, DIR_NAME).also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(outDir, "oneims-diag-$stamp.txt")
        return runCatching {
            out.writeText(buildExportText(ctx))
            i("export", "wrote ${out.absolutePath} bytes=${out.length()}")
            out
        }.getOrElse { error ->
            Log.e(TAG, "export failed", error)
            null
        }
    }

    fun buildExportText(context: Context): String = buildString {
        appendLine("=== OneIMS diagnostic bundle ===")
        appendLine("time=${timeFmt.get()?.format(Date())}")
        appendLine("oem=${OemDeviceCompat.summaryLine()}")
        appendLine(
            "app=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "channel=${BuildConfig.CHANNEL_LINE}",
        )
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("strategy=${SystemApiBroker.lastStrategy}")
        appendLine("delegate=${SystemApiBroker.supportsDelegate()}")
        appendLine()
        appendLine("--- device summary ---")
        appendLine(runCatching { DeviceInfo.summary(context) }.getOrElse { it.message.orEmpty() })
        appendLine()
        appendLine("--- session.log (tail) ---")
        appendLine(tail(120_000))
        appendLine()
        appendLine("--- recent crashes ---")
        val crashes = crashFiles(context).take(MAX_CRASH_FILES)
        if (crashes.isEmpty()) {
            appendLine("(none)")
        } else {
            for (file in crashes) {
                appendLine("## ${file.name}")
                appendLine(runCatching { file.readText() }.getOrElse { it.message.orEmpty() })
                appendLine()
            }
        }
    }

    private fun write(level: Level, tag: String, message: String, error: Throwable?) {
        val line = formatLine(level, tag, message, error)
        when (level) {
            Level.D -> Log.d(TAG, "[$tag] $message", error)
            Level.I -> Log.i(TAG, "[$tag] $message", error)
            Level.W -> Log.w(TAG, "[$tag] $message", error)
            Level.E -> Log.e(TAG, "[$tag] $message", error)
        }
        val ctx = appContext ?: return
        if (!ready.get()) return
        io.execute {
            runCatching {
                val file = sessionFile(ctx)
                file.appendText(line)
                trimIfNeeded(file)
            }.onFailure { Log.w(TAG, "append failed: ${it.message}") }
        }
    }

    private fun formatLine(
        level: Level,
        tag: String,
        message: String,
        error: Throwable?,
    ): String = buildString {
        append(timeFmt.get()?.format(Date()) ?: "?")
        append(' ')
        append(level.name)
        append('/')
        append(tag)
        append(": ")
        append(message.trim().take(4_000))
        append('\n')
        if (error != null) {
            append(stackOf(error))
            append('\n')
        }
    }

    private fun persistCrash(thread: Thread, error: Throwable) {
        val ctx = appContext ?: return
        val dir = dir(ctx).also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.log")
        val body = buildString {
            appendLine("thread=${thread.name}")
            appendLine("oem=${OemDeviceCompat.summaryLine()}")
            appendLine("app=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
            appendLine("android=${Build.VERSION.RELEASE}/${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("strategy=${SystemApiBroker.lastStrategy}")
            appendLine()
            append(stackOf(error))
        }
        runCatching { file.writeText(body) }
        runCatching {
            sessionFile(ctx).appendText(
                formatLine(Level.E, "crash", "uncaught on ${thread.name}: ${error.message}", error),
            )
        }
        pruneCrashes(ctx)
        // 同步写，崩溃路径不能只丢给后台线程
        Log.e(TAG, "crash persisted ${file.absolutePath}", error)
    }

    private fun pruneCrashes(context: Context) {
        val files = crashFiles(context)
        if (files.size <= MAX_CRASH_FILES) return
        files.drop(MAX_CRASH_FILES).forEach { runCatching { it.delete() } }
    }

    private fun trimIfNeeded(file: File) {
        if (file.length() <= MAX_SESSION_BYTES) return
        val text = file.readText()
        val keep = text.takeLast(TRIM_KEEP_BYTES.toInt())
        file.writeText("…(trimmed)\n$keep")
    }

    private fun stackOf(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun sessionFile(context: Context): File = File(dir(context), SESSION_FILE)

    private fun crashFiles(context: Context): List<File> =
        dir(context)
            .listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
}
