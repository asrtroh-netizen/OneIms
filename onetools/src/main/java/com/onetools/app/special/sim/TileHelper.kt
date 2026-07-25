package com.onetools.app.special.sim

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import com.onetools.app.R

object TileHelper {
    fun openTileEditor(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return runCatching {
                val statusBarManager = checkNotNull(
                    context.getSystemService(StatusBarManager::class.java),
                )
                statusBarManager.requestAddTileService(
                    ComponentName(context, DataSimTileService::class.java),
                    context.getString(R.string.special_qs_title),
                    Icon.createWithResource(context, R.drawable.ic_qs_data_sim),
                    context.mainExecutor,
                ) { result ->
                    if (
                        result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                        result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    ) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.special_qs_manual_guide),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }.isSuccess
        }
        val intent = Intent("android.settings.QUICK_SETTINGS_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
