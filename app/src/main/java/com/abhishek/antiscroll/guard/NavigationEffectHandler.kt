package com.abhishek.antiscroll.guard

import android.accessibilityservice.AccessibilityService
import com.abhishek.antiscroll.rules.engine.Effect
import com.abhishek.antiscroll.rules.engine.EffectHandler

/**
 * Carries out navigation with Android's own actions only, Home and Back, so no app's layout can
 * break it. (Tapping Instagram's tabs was tried on 2026-10-29 and dropped as fragile; docs/LLD.md.)
 */
class NavigationEffectHandler(private val service: AccessibilityService) : EffectHandler {
    override fun handle(effect: Effect) {
        when (effect) {
            Effect.GoHome -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Effect.GoBack -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            else -> Unit // screens are the overlay's job
        }
    }
}
