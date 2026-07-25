package com.onetools.app.caller

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.callerPrefsStore by preferencesDataStore("one_caller_prefs")

/**
 * Caller runtime prefs — mirrors Telo knobs we need for the spam-pack path
 * (notify-only / offline-only / timeout), clean-room.
 */
class CallerPrefs(private val context: Context) {
    private val notifyOnlyKey = booleanPreferencesKey("notify_only")
    private val noNetworkKey = booleanPreferencesKey("no_network_query")
    private val timeoutSecKey = intPreferencesKey("network_timeout_sec")
    private val syncManifestKey = stringPreferencesKey("spam_sync_manifest_url")

    val notifyOnlyFlow: Flow<Boolean> = context.callerPrefsStore.data.map {
        it[notifyOnlyKey] ?: true
    }

    val noNetworkQueryFlow: Flow<Boolean> = context.callerPrefsStore.data.map {
        it[noNetworkKey] ?: false
    }

    suspend fun notifyOnly(): Boolean = notifyOnlyFlow.first()

    suspend fun noNetworkQuery(): Boolean = noNetworkQueryFlow.first()

    suspend fun networkTimeoutMs(): Long {
        val sec = context.callerPrefsStore.data.first()[timeoutSecKey] ?: DEFAULT_TIMEOUT_SEC
        return sec.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC) * 1000L
    }

    suspend fun spamSyncManifestUrl(): String {
        val custom = context.callerPrefsStore.data.first()[syncManifestKey].orEmpty().trim()
        return custom.ifBlank { com.onetools.app.BuildConfig.ONE_SPAM_SYNC_MANIFEST_URL }
    }

    suspend fun setNotifyOnly(value: Boolean) {
        context.callerPrefsStore.edit { it[notifyOnlyKey] = value }
    }

    suspend fun setNoNetworkQuery(value: Boolean) {
        context.callerPrefsStore.edit { it[noNetworkKey] = value }
    }

    suspend fun setNetworkTimeoutSec(sec: Int) {
        context.callerPrefsStore.edit {
            it[timeoutSecKey] = sec.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC)
        }
    }

    suspend fun setSpamSyncManifestUrl(url: String) {
        context.callerPrefsStore.edit { it[syncManifestKey] = url.trim() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_SEC = 3
        const val MIN_TIMEOUT_SEC = 1
        const val MAX_TIMEOUT_SEC = 10
    }
}
