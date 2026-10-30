package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface

/**
 * Surface limits as one saved string, `REELS=30,FEED=20`. Entries naming a surface that no longer
 * exists, or that don't parse, are dropped rather than failing the whole setting.
 */
object SurfaceMinutesCodec {

    fun encode(minutes: Map<Surface, Int>): String =
        minutes.entries.sortedBy { it.key }.joinToString(",") { "${it.key.name}=${it.value}" }

    fun decode(text: String): Map<Surface, Int> =
        text.split(',').mapNotNull { entry ->
            val (name, value) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val surface = Surface.entries.find { it.name == name.trim() } ?: return@mapNotNull null
            val minutes = value.trim().toIntOrNull() ?: return@mapNotNull null
            surface to minutes
        }.toMap()
}
