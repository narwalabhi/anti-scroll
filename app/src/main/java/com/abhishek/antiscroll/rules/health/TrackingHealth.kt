package com.abhishek.antiscroll.rules.health

import java.time.Duration
import java.time.Instant

/** Why counting stopped for a while. */
enum class GapCause {
    /** The accessibility service was turned off (it said goodbye). */
    SERVICE_OFF,

    /** The process died without saying goodbye: killed by the system, or the app was reinstalled. */
    STOPPED_BY_SYSTEM,
}

/** A stretch when nothing was counted; [end] is null while it lasts. */
data class TrackingGap(val start: Instant, val end: Instant?, val cause: GapCause) {
    val isOngoing: Boolean get() = end == null
}

/**
 * What's known about the accessibility service's life: whether it was last seen running, when it
 * was last seen alive, and the recent gaps. Timestamps only.
 */
data class HealthRecord(
    val running: Boolean = false,
    val lastAliveAt: Instant? = null,
    val gaps: List<TrackingGap> = emptyList(),
) {
    val ongoingGap: TrackingGap? get() = gaps.lastOrNull()?.takeIf { it.isOngoing }
}

/** Reads and writes the [HealthRecord]. */
interface HealthStore {
    fun load(): HealthRecord
    fun save(record: HealthRecord)
}

/**
 * Folds the service's life into a [HealthRecord] (docs/LLD.md, Fallback watcher). Pure: the caller
 * passes the time. A start after a stop closes the gap the stop opened; a start while the record
 * still says running means the process died unannounced, so a gap from the last sign of life is
 * added. Gaps shorter than [minGap] are dropped, and those over [keep] old are pruned.
 */
class TrackingHealth(
    private val keep: Duration = Duration.ofDays(7),
    private val minGap: Duration = Duration.ofMinutes(1),
) {

    /**
     * [appUpdatedAt]: when the app was last installed or updated. An update kills the process without
     * a goodbye too; when it falls between the last sign of life and now, that's the death, not a gap.
     */
    fun started(record: HealthRecord, at: Instant, appUpdatedAt: Instant? = null): HealthRecord {
        val open = record.ongoingGap
        val lastAlive = record.lastAliveAt
        val updatedSince = appUpdatedAt != null && lastAlive != null && !appUpdatedAt.isBefore(lastAlive) && !appUpdatedAt.isAfter(at)
        val gaps = when {
            open != null -> record.gaps.dropLast(1) + listOfNotNull(closed(open, at))
            record.running && lastAlive != null && !updatedSince ->
                record.gaps + listOfNotNull(closed(TrackingGap(record.lastAliveAt, null, GapCause.STOPPED_BY_SYSTEM), at))
            else -> record.gaps
        }
        return HealthRecord(running = true, lastAliveAt = at, gaps = prune(gaps, at))
    }

    /** A sign of life: the heartbeat. */
    fun alive(record: HealthRecord, at: Instant): HealthRecord = record.copy(running = true, lastAliveAt = at)

    /** The service said goodbye: turned off, so a gap starts now. */
    fun stopped(record: HealthRecord, at: Instant): HealthRecord {
        if (record.ongoingGap != null) return record.copy(running = false)
        val gaps = record.gaps + TrackingGap(at, null, GapCause.SERVICE_OFF)
        return HealthRecord(running = false, lastAliveAt = at, gaps = prune(gaps, at))
    }

    /** The gap ended at [at], or null if it was too short to matter (or the clock moved back). */
    private fun closed(gap: TrackingGap, at: Instant): TrackingGap? =
        if (Duration.between(gap.start, at) >= minGap) gap.copy(end = at) else null

    private fun prune(gaps: List<TrackingGap>, now: Instant): List<TrackingGap> {
        val cutoff = now.minus(keep)
        return gaps.filter { it.isOngoing || it.end!!.isAfter(cutoff) }
    }
}

/** Applies [TrackingHealth] to the saved record and saves each change. */
class HealthRecorder(private val health: TrackingHealth, private val store: HealthStore) {

    var record: HealthRecord = store.load()
        private set

    fun started(at: Instant, appUpdatedAt: Instant? = null) = update(health.started(record, at, appUpdatedAt))

    fun alive(at: Instant) = update(health.alive(record, at))

    fun stopped(at: Instant) = update(health.stopped(record, at))

    /** Adds a restored backup's gaps to the ones recorded since; whether the service runs is kept. */
    fun restore(backup: HealthRecord) =
        update(record.copy(gaps = (backup.gaps + record.gaps).distinct().sortedBy { it.start }))

    /** Forgets every gap (Erase all data); whether the service runs is kept. */
    fun clearGaps() = update(record.copy(gaps = emptyList()))

    private fun update(next: HealthRecord) {
        if (next == record) return
        record = next
        store.save(next)
    }
}
