package com.onebattery.app.battery

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.onebattery.app.channel.ShizukuChannel
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** App-side binder to [ShellBatteryService] via Shizuku UserService. */
class ShellBatteryClient(private val context: Context) {
    private val service = AtomicReference<IBatteryShell?>(null)
    private var connection: ServiceConnection? = null

    fun bind(timeoutMs: Long = 8_000): Result<IBatteryShell> = runCatching {
        require(ShizukuChannel.isServiceReady()) { "需要 Shizuku 通道就绪" }
        service.get()?.let { return@runCatching it }

        val latch = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service.set(IBatteryShell.Stub.asInterface(binder))
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service.set(null)
            }
        }
        connection = conn
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellBatteryService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("batshell")
            .tag("onetools-batshell")
            .version(1)
        Shizuku.bindUserService(args, conn)
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            error("绑定 BatteryShell UserService 超时")
        }
        service.get() ?: error("UserService binder null")
    }

    fun unbind() {
        val conn = connection ?: return
        runCatching {
            Shizuku.unbindUserService(
                Shizuku.UserServiceArgs(
                    ComponentName(context.packageName, ShellBatteryService::class.java.name),
                ).tag("onetools-batshell").version(1),
                conn,
                true,
            )
        }
        connection = null
        service.set(null)
    }

    fun dumpBatteryStats(maxChars: Int): Result<String> = runCatching {
        val svc = bind().getOrThrow()
        val text = svc.dumpBatteryStats(maxChars)
        if (text.isBlank()) {
            error(svc.lastError().ifBlank { "dumpsys 返回空" })
        }
        text
    }
}
