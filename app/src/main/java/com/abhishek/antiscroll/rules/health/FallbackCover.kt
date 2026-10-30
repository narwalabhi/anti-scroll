package com.abhishek.antiscroll.rules.health

/** An app coming to front or leaving it, as Android's usage events report it. Package names only. */
data class AppEvent(val packageName: String, val kind: Kind) {
    enum class Kind { RESUMED, PAUSED }
}

/** The app in front, from usage events: the last one resumed, until it pauses. */
object ForegroundApp {

    fun next(current: String?, event: AppEvent): String? = when (event.kind) {
        AppEvent.Kind.RESUMED -> event.packageName
        AppEvent.Kind.PAUSED -> if (event.packageName == current) null else current
    }

    fun fold(current: String?, events: List<AppEvent>): String? = events.fold(current, ::next)
}

/**
 * Whether the fallback watcher covers the watched app (docs/LLD.md, Fallback watcher): whenever it's
 * in front, except that with enforcement off (the baseline) the user may use it uncounted until it
 * leaves the front. Pure; the watcher keeps the [State].
 */
class FallbackCover(private val watchedPackage: String) {

    data class State(val foreground: String? = null, val uncounted: Boolean = false)

    /** Leaving the watched app ends "uncounted", so the next open is covered again. */
    fun onForeground(state: State, packageName: String?): State =
        if (packageName == watchedPackage) state.copy(foreground = packageName) else State(packageName)

    fun useUncounted(state: State): State = state.copy(uncounted = true)

    fun showsCover(state: State, enforcementEnabled: Boolean): Boolean =
        state.foreground == watchedPackage && (enforcementEnabled || !state.uncounted)

    /** "Use uncounted" is only offered while nothing is enforced. */
    fun offersUncounted(enforcementEnabled: Boolean): Boolean = !enforcementEnabled
}
