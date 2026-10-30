package com.abhishek.antiscroll.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.UsageStore
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "usage")

/**
 * Keeps each day's counters as one JSON entry, keyed by date, so past days stay available.
 * Saves run in the background one at a time, so a newer snapshot is never overwritten by an
 * older one. The only read, at app start, waits for the file.
 */
class DataStoreUsageStore(context: Context) : UsageStore {

    private val dataStore = context.applicationContext.usageDataStore
    private val writes = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    override fun load(day: LocalDate): DayUsage? = try {
        runBlocking { dataStore.data.first()[keyFor(day)] }?.let(DayUsageJson::decode)
    } catch (e: Exception) {
        // A damaged entry costs one day's counters, not the app.
        Log.w(TAG, "could not load usage for $day", e)
        null
    }

    override fun save(usage: DayUsage) {
        val json = DayUsageJson.encode(usage)
        writes.launch {
            try {
                dataStore.edit { it[keyFor(usage.day)] = json }
            } catch (e: Exception) {
                Log.w(TAG, "could not save usage for ${usage.day}", e)
            }
        }
    }

    override fun firstDay(): LocalDate? = try {
        runBlocking { dataStore.data.first() }.asMap().keys
            .map { it.name }
            .filter { it.startsWith(KEY_PREFIX) }
            .minOrNull()
            ?.removePrefix(KEY_PREFIX)
            ?.let(LocalDate::parse)
    } catch (e: Exception) {
        Log.w(TAG, "could not read saved days", e)
        null
    }

    private fun keyFor(day: LocalDate) = stringPreferencesKey("$KEY_PREFIX$day")

    private companion object {
        const val TAG = "AntiScroll"
        const val KEY_PREFIX = "day_" // ISO dates sort as text, so the smallest key is the earliest day
    }
}
