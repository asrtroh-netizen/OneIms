package com.oneims.app.core

import android.Manifest
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.oneims.app.R
import com.oneims.app.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataSimSwitchTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (!hasPhoneStatePermission()) {
                showMessage(getString(R.string.qs_tile_permission_required))
                refreshTile()
                return@launch
            }
            val sims = runCatching {
                DataSimSwitchManagerImpl.getActiveSims(this@DataSimSwitchTileService)
            }.getOrElse { error ->
                Log.w(TAG, "Unable to read active SIMs", error)
                showMessage(getString(R.string.data_switch_failed, OperationErrors.describe(error)))
                refreshTile()
                return@launch
            }
            when {
                !ShizukuManager.isRunning() || !ShizukuManager.isGranted() -> {
                    showMessage(getString(R.string.qs_tile_permission_required))
                    refreshTile()
                }
                sims.isEmpty() -> {
                    showMessage(getString(R.string.data_switch_no_sim))
                    refreshTile()
                }
                sims.size == 1 -> {
                    showMessage(getString(R.string.qs_tile_single_sim))
                    refreshTile()
                }
                else -> {
                    val current = DataSimSwitchManagerImpl.getDefaultDataSubId()
                    val target = sims
                        .sortedBy(SimCardInfo::slotIndex)
                        .firstOrNull { it.subId != current }
                    if (target == null) {
                        showMessage(getString(R.string.data_switch_invalid_target))
                        refreshTile()
                        return@launch
                    }
                    updateTile(
                        state = Tile.STATE_INACTIVE,
                        subtitle = getString(R.string.qs_tile_switching),
                    )
                    Log.i(
                        TAG,
                        "switch requested currentSubId=$current targetSubId=${target.subId}",
                    )
                    when (
                        val result = DataSimSwitchManagerImpl.switchDefaultDataSubId(
                            this@DataSimSwitchTileService,
                            target.subId,
                        )
                    ) {
                        is DataSimSwitchResult.Success -> {
                            refreshTile()
                            showMessage(
                                result.warning ?: getString(
                                    R.string.data_switch_success,
                                    target.slotIndex + 1,
                                ),
                            )
                        }
                        is DataSimSwitchResult.Failed -> {
                            Log.w(TAG, "switch failed reason=${result.reason}")
                            refreshTile()
                            showMessage(getString(R.string.data_switch_failed, result.reason))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        if (!hasPhoneStatePermission()) {
            updateTile(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = getString(R.string.qs_tile_permission_required),
            )
            return
        }
        val sims = runCatching {
            DataSimSwitchManagerImpl.getActiveSims(this)
        }.getOrDefault(emptyList())
        val defaultSubId = DataSimSwitchManagerImpl.getDefaultDataSubId()
        val current = sims.firstOrNull { it.subId == defaultSubId }
        when {
            !ShizukuManager.isRunning() || !ShizukuManager.isGranted() -> updateTile(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = getString(R.string.qs_tile_permission_required),
            )
            sims.isEmpty() -> updateTile(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = getString(R.string.data_switch_no_sim),
            )
            sims.size == 1 -> updateTile(
                state = Tile.STATE_UNAVAILABLE,
                subtitle = getString(R.string.qs_tile_single_sim),
            )
            current == null -> updateTile(
                state = Tile.STATE_INACTIVE,
                subtitle = getString(R.string.qs_tile_current_unknown),
            )
            else -> updateTile(
                state = Tile.STATE_ACTIVE,
                subtitle = getString(
                    R.string.qs_tile_current_sim,
                    current.slotIndex + 1,
                    current.shortName,
                ),
            )
        }
    }

    private fun hasPhoneStatePermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun updateTile(state: Int, subtitle: String) {
        qsTile?.let { tile ->
            tile.label = getString(R.string.qs_tile_title)
            tile.state = state
            tile.subtitle = subtitle
            tile.updateTile()
        }
    }

    private suspend fun showMessage(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@DataSimSwitchTileService, message, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TAG = "OneIMS-QSTile"
    }
}
