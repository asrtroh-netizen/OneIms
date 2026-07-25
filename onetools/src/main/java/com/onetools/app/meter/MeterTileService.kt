package com.onetools.app.meter

import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** QS tile: start/stop notification metering — dynamic speed glyph (not a blank white block). */
class MeterNotificationTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (SpeedMonitorService.isRunning) {
            SpeedMonitorService.stop(this)
        } else {
            SpeedMonitorService.start(this)
        }
        // Service start is async — refresh again shortly so ACTIVE state sticks.
        refresh()
        handler.postDelayed({ refresh() }, 400)
        sendBroadcast(Intent(ACTION_METER_STATE_CHANGED).setPackage(packageName))
    }

    private fun refresh() {
        val running = SpeedMonitorService.isRunning
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(com.onetools.app.R.string.meter_tile_notif)
            if (Build.VERSION.SDK_INT >= 29) {
                subtitle = if (running) {
                    getString(com.onetools.app.R.string.meter_tile_notif_on)
                } else {
                    getString(com.onetools.app.R.string.meter_tile_notif_off)
                }
            }
            val bmp = MeterDynamicIcon.create(0, 0, MeterDisplayMode.BOTH)
            icon = Icon.createWithBitmap(bmp)
            updateTile()
        }
    }

    companion object {
        const val ACTION_METER_STATE_CHANGED = "com.onetools.app.meter.STATE"
    }
}

/** QS tile: toggle floating overlay (requires overlay permission + running sampler). */
class MeterOverlayTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        scope.launch {
            val on = MeterSettings(applicationContext).snapshot().overlayEnabled
            handler.post { applyTile(on && SpeedMonitorService.isRunning) }
        }
    }

    override fun onClick() {
        scope.launch {
            val settings = MeterSettings(applicationContext)
            val cur = settings.snapshot()
            val enable = !cur.overlayEnabled
            settings.setOverlayEnabled(enable)
            handler.post {
                if (enable && !SpeedMonitorService.isRunning) {
                    SpeedMonitorService.start(this@MeterOverlayTileService)
                }
                startService(
                    Intent(this@MeterOverlayTileService, SpeedMonitorService::class.java)
                        .setAction(SpeedMonitorService.ACTION_APPLY_PREFS),
                )
                applyTile(enable)
                handler.postDelayed({
                    applyTile(enable && SpeedMonitorService.isRunning)
                }, 400)
                sendBroadcast(
                    Intent(MeterNotificationTileService.ACTION_METER_STATE_CHANGED)
                        .setPackage(packageName),
                )
            }
        }
    }

    private fun applyTile(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(com.onetools.app.R.string.meter_tile_overlay)
            if (Build.VERSION.SDK_INT >= 29) {
                subtitle = if (on) {
                    getString(com.onetools.app.R.string.meter_tile_overlay_on)
                } else {
                    getString(com.onetools.app.R.string.meter_tile_overlay_off)
                }
            }
            val bmp = MeterDynamicIcon.create(0, 0, MeterDisplayMode.DOWN)
            icon = Icon.createWithBitmap(bmp)
            updateTile()
        }
    }
}
