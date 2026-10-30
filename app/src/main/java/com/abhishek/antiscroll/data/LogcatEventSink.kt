package com.abhishek.antiscroll.data

import android.util.Log
import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.events.EventSink

/** Writes engine events to Logcat. The JSONL file log joins it in step 6. */
class LogcatEventSink : EventSink {
    override fun onEvent(event: EngineEvent) {
        Log.i(TAG, "event: $event")
    }

    private companion object {
        const val TAG = "AntiScroll"
    }
}
