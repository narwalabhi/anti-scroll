package com.abhishek.antiscroll.rules.events

/** Anything that wants to know what happened: the event log now, summaries later. */
interface EventSink {
    fun onEvent(event: EngineEvent)
}
