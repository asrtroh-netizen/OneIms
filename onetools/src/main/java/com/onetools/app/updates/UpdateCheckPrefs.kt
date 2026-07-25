package com.onetools.app.updates

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updateCheckStore by preferencesDataStore("one_update_check_prefs")

data class UpdateCheckPrefsSnapshot(
    val enabled: Boolean,
    /** Hours between background checks; Obtainium-like default 6h. */
    val intervalHours: Int,
)

class UpdateCheckPrefs(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("auto_check_enabled")
    private val intervalKey = intPreferencesKey("auto_check_hours")

    val snapshotFlow: Flow<UpdateCheckPrefsSnapshot> = context.updateCheckStore.data.map { p ->
        UpdateCheckPrefsSnapshot(
            enabled = p[enabledKey] ?: true,
            intervalHours = (p[intervalKey] ?: DEFAULT_HOURS).coerceIn(MIN_HOURS, MAX_HOURS),
        )
    }

    suspend fun snapshot(): UpdateCheckPrefsSnapshot = snapshotFlow.first()

    suspend fun setEnabled(value: Boolean) {
        context.updateCheckStore.edit { it[enabledKey] = value }
        UpdateCheckScheduler.resync(context)
    }

    suspend fun setIntervalHours(hours: Int) {
        context.updateCheckStore.edit {
            it[intervalKey] = hours.coerceIn(MIN_HOURS, MAX_HOURS)
        }
        UpdateCheckScheduler.resync(context)
    }

    companion object {
        const val DEFAULT_HOURS = 6
        const val MIN_HOURS = 1
        const val MAX_HOURS = 72
    }
}
