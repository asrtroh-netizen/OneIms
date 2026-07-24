package com.onetools.app.updates

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.oneMemberStore by preferencesDataStore("one_member")

/** Membership bearer token for private One Index CDN. */
class MembershipTokenStore(private val context: Context) {
    private val key = stringPreferencesKey("bearer")

    val tokenFlow: Flow<String> = context.oneMemberStore.data.map { it[key].orEmpty() }

    suspend fun getToken(): String = tokenFlow.first()

    suspend fun setToken(value: String) {
        context.oneMemberStore.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) prefs.remove(key) else prefs[key] = trimmed
        }
    }
}
