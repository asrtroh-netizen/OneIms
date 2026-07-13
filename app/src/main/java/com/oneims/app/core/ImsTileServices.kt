package com.oneims.app.core

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.oneims.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pixel IMS 同类快捷磁贴的独立实现：一个只读显示 IMS 注册状态，一个显式重放上次配置。
 * 两者都复用 OneIms 的 broker 与重应用审计链，不在 TileService 中复制特权调用。
 */
class ImsStatusTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshStatus() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch { refreshStatus() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshStatus() {
        val tile = qsTile ?: return
        val subId = ConfigStore.lastApplied(this)?.subId
            ?: ImsController.listSims(this).firstOrNull()?.subscriptionId
        if (subId == null) {
            updateTile(
                tile,
                Tile.STATE_UNAVAILABLE,
                getString(R.string.tile_ims_unknown),
            )
            return
        }
        val info = runCatching {
            SystemApiBroker.queryImsRegistration(subId)
        }.getOrNull()
        when {
            info == null || !info.querySucceeded -> updateTile(
                tile,
                Tile.STATE_UNAVAILABLE,
                getString(R.string.tile_ims_unknown),
            )
            info.registered -> updateTile(
                tile,
                Tile.STATE_ACTIVE,
                getString(R.string.tile_ims_registered),
            )
            else -> updateTile(
                tile,
                Tile.STATE_INACTIVE,
                getString(R.string.tile_ims_not_registered),
            )
        }
    }
}

class VolteProfileTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        val hasProfile = ConfigStore.lastApplied(this) != null
        qsTile?.let { tile ->
            updateTile(
                tile,
                if (hasProfile) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE,
                getString(
                    if (hasProfile) {
                        R.string.tile_volte_label
                    } else {
                        R.string.tile_no_saved_profile
                    },
                ),
            )
        }
    }

    override fun onClick() {
        super.onClick()
        if (ConfigStore.lastApplied(this) == null) {
            qsTile?.let { tile ->
                updateTile(
                    tile,
                    Tile.STATE_UNAVAILABLE,
                    getString(R.string.tile_no_saved_profile),
                )
            }
            return
        }
        scope.launch {
            val result = ReapplyManager.reapply(
                this@VolteProfileTileService,
                ReapplyTrigger.QUICK_SETTINGS_TILE,
            )
            qsTile?.let { tile ->
                updateTile(
                    tile,
                    if (result.success) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE,
                    getString(
                        if (result.success) {
                            R.string.tile_volte_applied
                        } else {
                            R.string.tile_volte_failed
                        },
                    ),
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private fun updateTile(tile: Tile, state: Int, subtitle: String) {
    tile.state = state
    tile.subtitle = subtitle
    tile.updateTile()
}
