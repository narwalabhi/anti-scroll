package com.abhishek.antiscroll.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.StatsStore
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = "stats")

/**
 * Keeps each calendar date's [DayStats] as one JSON entry, keyed by date, in its own file. Saves
 * run in the background one at a time; reads wait for the file.
 */
class DataStoreStatsStore(context: Context) : StatsStore {

    private val dataStore = context.applicationContext.statsDataStore
    private val writes = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    override fun load(date: LocalDate): DayStats? = try {
        runBlocking { dataStore.data.first()[keyFor(date)] }?.let(DayStatsJson::decode)
    } catch (e: Exception) {
        Log.w(TAG, "could not load stats for $date", e)
        null
    }

    override fun save(stats: DayStats) {
        val json = DayStatsJson.encode(stats)
        writes.launch {
            try {
                dataStore.edit { it[keyFor(stats.date)] = json }
            } catch (e: Exception) {
                Log.w(TAG, "could not save stats for ${stats.date}", e)
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
        Log.w(TAG, "could not read saved dates", e)
        null
    }

    private fun keyFor(date: LocalDate) = stringPreferencesKey("$KEY_PREFIX$date")

    private companion object {
        const val TAG = "AntiScroll"
        const val KEY_PREFIX = "date_" // ISO dates sort as text, so the smallest key is the earliest date
    }
}
