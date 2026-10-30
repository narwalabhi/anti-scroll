package com.abhishek.antiscroll.data

import android.util.Log
import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.events.EventSink
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Appends each event as one JSON line, off the main thread and in order. Counts and timestamps only. */
class JsonlEventLog(
    private val file: File,
    private val writer: Executor = Executors.newSingleThreadExecutor(),
) : EventSink {

    override fun onEvent(event: EngineEvent) {
        val line = EngineEventJson.encode(event) + "\n"
        writer.execute {
            try {
                file.appendText(line)
            } catch (e: Exception) {
                Log.w(TAG, "could not write event log", e)
            }
        }
    }

    private companion object {
        const val TAG = "AntiScroll"
    }
}
