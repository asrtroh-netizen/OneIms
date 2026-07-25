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
import java.util.UUID

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
    private val applyReportLocalKey = booleanPreferencesKey("apply_report_locally")
    private val communityOptInKey = booleanPreferencesKey("community_report_opt_in")
    private val clientIdKey = stringPreferencesKey("report_client_id")

    val notifyOnlyFlow: Flow<Boolean> = context.callerPrefsStore.data.map {
        it[notifyOnlyKey] ?: true
    }

    /** Default true: cheapest path — local geo + onespam only; no commercial lookup API. */
    val noNetworkQueryFlow: Flow<Boolean> = context.callerPrefsStore.data.map {
        it[noNetworkKey] ?: true
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

    /** Phase-1: reporting immediately writes local BLOCK rule + onespam row. Default on. */
    val applyReportLocallyFlow: Flow<Boolean> = context.callerPrefsStore.data.map {
        it[applyReportLocalKey] ?: true
    }

    suspend fun applyReportLocally(): Boolean = applyReportLocallyFlow.first()

    suspend fun setApplyReportLocally(value: Boolean) {
        context.callerPrefsStore.edit { it[applyReportLocalKey] = value }
    }

    /** Phase-2: opt-in to export community report JSON. Default off (privacy). */
    val communityReportOptInFlow: Flow<Boolean> = context.callerPrefsStore.data.map {
        it[communityOptInKey] ?: false
    }

    suspend fun communityReportOptIn(): Boolean = communityReportOptInFlow.first()

    suspend fun setCommunityReportOptIn(value: Boolean) {
        context.callerPrefsStore.edit { it[communityOptInKey] = value }
    }

    /** Opaque install id for anti-spam aggregation; never equals device advertising id. */
    suspend fun reportClientId(): String {
        val existing = context.callerPrefsStore.data.first()[clientIdKey].orEmpty().trim()
        if (existing.isNotEmpty()) return existing
        val created = UUID.randomUUID().toString()
        context.callerPrefsStore.edit { it[clientIdKey] = created }
        return created
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
