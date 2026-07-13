package com.oneims.app.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.app.StatusBarManager
import android.widget.Toast
import com.oneims.app.R

object QuickSettingsTileHelper {

    fun openTileEditor(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return runCatching {
                val statusBarManager = checkNotNull(
                    context.getSystemService(StatusBarManager::class.java),
                )
                statusBarManager.requestAddTileService(
                    ComponentName(context, DataSimSwitchTileService::class.java),
                    context.getString(R.string.qs_tile_title),
                    Icon.createWithResource(context, R.drawable.ic_qs_data_sim),
                    context.mainExecutor,
                ) { result ->
                    if (
                        result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
                        result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                    ) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.qs_tile_manual_guide),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }.isSuccess
        }

        val intent = Intent("android.settings.QUICK_SETTINGS_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
        }.isSuccess
    }
}
