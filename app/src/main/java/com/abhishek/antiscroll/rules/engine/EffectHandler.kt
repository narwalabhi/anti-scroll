package com.abhishek.antiscroll.rules.engine

/** Carries out an [Effect]: the overlay for screens, the service for navigation. */
fun interface EffectHandler {
    fun handle(effect: Effect)
}
