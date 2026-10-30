package com.abhishek.antiscroll

import android.util.Log
import com.abhishek.antiscroll.rules.engine.Effect
import com.abhishek.antiscroll.rules.engine.EffectHandler

/**
 * Routes each effect to the handler that can carry it out. The runner is created with the app,
 * before the service exists, so the service plugs its handlers in when it connects and unplugs
 * them when it stops.
 */
class EffectDispatcher : EffectHandler {

    var screens: EffectHandler? = null
    var navigation: EffectHandler? = null

    override fun handle(effect: Effect) {
        val target = when (effect) {
            Effect.GoHome, Effect.GoBack -> navigation
            is Effect.ShowPause, is Effect.ShowIntention, is Effect.ShowBlock, is Effect.ShowChallenge,
            Effect.ShowLeaving, Effect.Dismiss,
            -> screens
        }
        if (target == null) Log.w(TAG, "no handler connected for $effect") else target.handle(effect)
    }

    private companion object {
        const val TAG = "AntiScroll"
    }
}
