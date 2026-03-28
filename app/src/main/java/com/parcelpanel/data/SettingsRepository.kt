package com.parcelpanel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.parcelpanel.model.AppPreferencesModel
import com.parcelpanel.tracking.CarrierCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "parcelpanel_preferences")

class SettingsRepository(
    private val context: Context,
) {
    private val syncIntervalKey = intPreferencesKey("sync_interval_hours")
    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates")
    private val lastUpdateCheckAtKey = longPreferencesKey("last_update_check_at")

    val preferences: Flow<AppPreferencesModel> = context.dataStore.data.map { prefs ->
        val apiKeys = CarrierCatalog.all.mapNotNull { definition ->
            prefs[apiKeyKey(definition.slug)]?.takeIf { it.isNotBlank() }?.let { definition.slug to it }
        }.toMap()
        AppPreferencesModel(
            syncIntervalHours = prefs[syncIntervalKey] ?: 4,
            apiKeys = apiKeys,
            autoCheckUpdates = prefs[autoCheckUpdatesKey] ?: true,
            lastUpdateCheckAt = prefs[lastUpdateCheckAtKey],
        )
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        context.dataStore.edit { prefs ->
            prefs[syncIntervalKey] = hours.coerceIn(2, 12)
        }
    }

    suspend fun setApiKey(slug: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyKey(slug)] = apiKey.trim()
        }
    }

    suspend fun apiKeyFor(slug: String): String? {
        val prefs = context.dataStore.data.first()
        return prefs[apiKeyKey(slug)]
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoCheckUpdatesKey] = enabled
        }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long?) {
        context.dataStore.edit { prefs ->
            if (timestamp == null) {
                prefs.remove(lastUpdateCheckAtKey)
            } else {
                prefs[lastUpdateCheckAtKey] = timestamp
            }
        }
    }

    private fun apiKeyKey(slug: String) = stringPreferencesKey("carrier_api_key_$slug")
}
