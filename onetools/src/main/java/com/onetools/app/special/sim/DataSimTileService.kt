package com.onetools.app.special.sim

import android.Manifest
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.onetools.app.R
import com.onetools.app.special.privilege.SpecialPrivilege
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataSimTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (!hasPhoneStatePermission()) {
                showMessage(getString(R.string.special_qs_permission))
                refreshTile()
                return@launch
            }
            val sims = runCatching {
                DataSimController.getActiveSims(this@DataSimTileService)
            }.getOrElse { error ->
                Log.w(TAG, "Unable to read active SIMs", error)
                showMessage(getString(R.string.special_data_switch_failed, error.message ?: ""))
                refreshTile()
                return@launch
            }
            when {
                !SpecialPrivilege.isReady() -> {
                    showMessage(getString(R.string.special_qs_permission))
                    refreshTile()
                }
                sims.isEmpty() -> {
                    showMessage(getString(R.string.special_data_switch_no_sim))
                    refreshTile()
                }
                sims.size == 1 -> {
                    showMessage(getString(R.string.special_qs_single_sim))
                    refreshTile()
                }
                else -> {
                    val current = DataSimController.getDefaultDataSubId()
                    val target = sims.sortedBy { it.slotIndex }.firstOrNull { it.subId != current }
                    if (target == null) {
                        showMessage(getString(R.string.special_data_switch_invalid))
                        refreshTile()
                        return@launch
                    }
                    updateTile(Tile.STATE_INACTIVE, getString(R.string.special_qs_switching))
                    when (
                        val result = DataSimController.switchDefaultDataSubId(
                            this@DataSimTileService,
                            target.subId,
                        )
                    ) {
                        is SpecialSimSwitchResult.Success -> {
                            refreshTile()
                            showMessage(
                                result.warning
                                    ?: getString(R.string.special_data_switch_success, target.slotIndex + 1),
                            )
                        }
                        is SpecialSimSwitchResult.Failed -> {
                            refreshTile()
                            showMessage(getString(R.string.special_data_switch_failed, result.reason))
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
            updateTile(Tile.STATE_UNAVAILABLE, getString(R.string.special_qs_permission))
            return
        }
        val sims = runCatching { DataSimController.getActiveSims(this) }.getOrDefault(emptyList())
        val defaultSubId = DataSimController.getDefaultDataSubId()
        val current = sims.firstOrNull { it.subId == defaultSubId }
        when {
            !SpecialPrivilege.isReady() ->
                updateTile(Tile.STATE_UNAVAILABLE, getString(R.string.special_qs_permission))
            sims.isEmpty() ->
                updateTile(Tile.STATE_UNAVAILABLE, getString(R.string.special_data_switch_no_sim))
            sims.size == 1 ->
                updateTile(Tile.STATE_UNAVAILABLE, getString(R.string.special_qs_single_sim))
            current == null ->
                updateTile(Tile.STATE_INACTIVE, getString(R.string.special_qs_current_unknown))
            else -> updateTile(
                Tile.STATE_ACTIVE,
                getString(R.string.special_qs_current_sim, current.slotIndex + 1, current.shortName),
            )
        }
    }

    private fun hasPhoneStatePermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun updateTile(state: Int, subtitle: String) {
        qsTile?.let { tile ->
            tile.label = getString(R.string.special_qs_title)
            tile.state = state
            tile.subtitle = subtitle
            tile.updateTile()
        }
    }

    private suspend fun showMessage(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@DataSimTileService, message, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TAG = "OneTools-QSTile"
    }
}
