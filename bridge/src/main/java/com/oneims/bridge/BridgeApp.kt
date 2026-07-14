package com.oneims.bridge

import android.app.Application
import android.util.Log
import java.io.File

class BridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { BridgeStarter.installStartScript(this) }
            .onFailure { Log.w(TAG, "install start.sh failed", it) }
    }

    companion object {
        private const val TAG = "OneBridge"
    }
}

/**
 * 把 assets/start.sh 写到 Android/data/com.oneims.bridge/start.sh，
 * 对齐 OneIMS EmbeddedAdb / adbStartCommand 契约。
 */
object BridgeStarter {
    private const val TAG = "OneBridge"
    private const val SCRIPT_NAME = "start.sh"

    fun installStartScript(app: Application) {
        val parent = app.getExternalFilesDir(null)?.parentFile
            ?: File("/storage/emulated/0/Android/data/${app.packageName}")
        if (!parent.exists()) parent.mkdirs()
        val out = File(parent, SCRIPT_NAME)
        app.assets.open(SCRIPT_NAME).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        out.setReadable(true, false)
        out.setExecutable(true, false)
        Log.i(TAG, "start.sh installed at ${out.absolutePath}")
    }

    fun startScriptPath(packageName: String = "com.oneims.bridge"): String =
        "/storage/emulated/0/Android/data/$packageName/$SCRIPT_NAME"
}
