package com.abhishek.antiscroll.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.abhishek.antiscroll.rules.health.HealthRecord
import com.abhishek.antiscroll.rules.health.HealthStore
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.healthDataStore: DataStore<Preferences> by preferencesDataStore(name = "health")

/** Keeps the [HealthRecord] as one JSON entry in its own file. Saves run in the background, in order. */
class DataStoreHealthStore(context: Context) : HealthStore {

    private val dataStore = context.applicationContext.healthDataStore
    private val writes = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    override fun load(): HealthRecord = try {
        runBlocking { dataStore.data.first()[KEY] }?.let(HealthRecordJson::decode) ?: HealthRecord()
    } catch (e: Exception) {
        Log.w(TAG, "could not load tracking health", e)
        HealthRecord()
    }

    override fun save(record: HealthRecord) {
        val json = HealthRecordJson.encode(record)
        writes.launch {
            try {
                dataStore.edit { it[KEY] = json }
            } catch (e: Exception) {
                Log.w(TAG, "could not save tracking health", e)
            }
        }
    }

    private companion object {
        const val TAG = "AntiScroll"
        val KEY = stringPreferencesKey("record")
    }
}
