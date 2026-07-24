package com.onetools.app.recorder

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.onetools.app.channel.ShizukuChannel
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** App-side binder to clean-room [ShellRecorderService] via Shizuku UserService. */
class ShellRecorderClient(private val context: Context) {
    private val service = AtomicReference<IShellRecorder?>(null)
    private var connection: ServiceConnection? = null

    fun isBound(): Boolean = service.get() != null

    fun bind(timeoutMs: Long = 8_000): Result<IShellRecorder> = runCatching {
        require(ShizukuChannel.isServiceReady()) { "需要 Shizuku 通道就绪" }
        service.get()?.let { return@runCatching it }

        val latch = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service.set(IShellRecorder.Stub.asInterface(binder))
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service.set(null)
            }
        }
        connection = conn
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellRecorderService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("callrec")
            .tag("onetools-callrec")
            .version(1)
        Shizuku.bindUserService(args, conn)
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            error("绑定 ShellRecorder UserService 超时")
        }
        service.get() ?: error("UserService binder null")
    }

    fun unbind() {
        val conn = connection ?: return
        runCatching { Shizuku.unbindUserService(
            Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShellRecorderService::class.java.name),
            ).tag("onetools-callrec").version(1),
            conn,
            true,
        ) }
        connection = null
        service.set(null)
    }

    fun startToFile(file: File, preferredSource: Int = 0): Result<String> = runCatching {
        val svc = bind().getOrThrow()
        val code = svc.startRecording(file.absolutePath, preferredSource)
        if (code != 0) error(svc.lastError().ifBlank { "start failed code=$code" })
        svc.activeSourceName().ifBlank { "unknown" }
    }

    fun stop(): Result<Unit> = runCatching {
        service.get()?.stopRecording()
        Unit
    }
}
