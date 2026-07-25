package com.onetools.app.meter

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.runBlocking

/** QS tile: start/stop notification metering (Pixel Meter–style). */
class MeterNotificationTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (SpeedMonitorService.isRunning) {
            SpeedMonitorService.stop(this)
        } else {
            SpeedMonitorService.start(this)
        }
        refresh()
        // Nudge overlay prefs visibility when starting.
        sendBroadcast(Intent(ACTION_METER_STATE_CHANGED).setPackage(packageName))
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (SpeedMonitorService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(com.onetools.app.R.string.meter_tile_notif)
            updateTile()
        }
    }

    companion object {
        const val ACTION_METER_STATE_CHANGED = "com.onetools.app.meter.STATE"
    }
}

/** QS tile: toggle floating overlay (requires overlay permission + running sampler). */
class MeterOverlayTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val settings = MeterSettings(applicationContext)
        val next = runBlocking {
            val cur = settings.snapshot()
            val enable = !cur.overlayEnabled
            settings.setOverlayEnabled(enable)
            enable
        }
        if (next && !SpeedMonitorService.isRunning) {
            SpeedMonitorService.start(this)
        }
        sendBroadcast(
            Intent(MeterNotificationTileService.ACTION_METER_STATE_CHANGED).setPackage(packageName),
        )
        // Ask service to apply overlay immediately.
        startService(
            Intent(this, SpeedMonitorService::class.java).setAction(SpeedMonitorService.ACTION_APPLY_PREFS),
        )
        refresh()
    }

    private fun refresh() {
        val on = runBlocking { MeterSettings(applicationContext).snapshot().overlayEnabled }
        qsTile?.apply {
            state = if (on && SpeedMonitorService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(com.onetools.app.R.string.meter_tile_overlay)
            updateTile()
        }
    }
}
