package com.abhishek.antiscroll.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.settings.SettingsStore
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Saves [AppSettings] (enforcement, the daily total, the surface limits) in its own file, apart
 * from the usage counters. Writes run in order, off the main thread.
 */
class DataStoreSettingsStore(context: Context) : SettingsStore {

    private val dataStore = context.applicationContext.settingsDataStore
    private val writes = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    override fun load(): AppSettings = try {
        val prefs = runBlocking { dataStore.data.first() }
        val defaults = AppSettings()
        AppSettings(
            enforcementEnabled = prefs[ENFORCEMENT] ?: defaults.enforcementEnabled,
            dailyTotalMinutes = prefs[DAILY_TOTAL_MINUTES] ?: defaults.dailyTotalMinutes,
            surfaceMinutes = prefs[SURFACE_MINUTES]?.let(SurfaceMinutesCodec::decode)
                // Saved before limits became per-surface: carry the Reels limit over.
                ?: prefs[LEGACY_REELS_MINUTES]?.let { mapOf(Surface.REELS to it) }
                ?: defaults.surfaceMinutes,
        )
    } catch (e: Exception) {
        // Unreadable or out-of-range settings fall back to the defaults: counting only, 90 and 30 minutes.
        Log.w(TAG, "could not load settings", e)
        AppSettings()
    }

    override fun save(settings: AppSettings) {
        writes.launch {
            try {
                dataStore.edit {
                    it[ENFORCEMENT] = settings.enforcementEnabled
                    it[DAILY_TOTAL_MINUTES] = settings.dailyTotalMinutes
                    it[SURFACE_MINUTES] = SurfaceMinutesCodec.encode(settings.surfaceMinutes)
                    it.remove(LEGACY_REELS_MINUTES)
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not save settings", e)
            }
        }
    }

    private companion object {
        const val TAG = "AntiScroll"
        val ENFORCEMENT = booleanPreferencesKey("enforcement_enabled")
        val DAILY_TOTAL_MINUTES = intPreferencesKey("daily_total_minutes")
        val SURFACE_MINUTES = stringPreferencesKey("surface_minutes")
        val LEGACY_REELS_MINUTES = intPreferencesKey("reels_minutes")
    }
}
