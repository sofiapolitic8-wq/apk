package com.davidd.app.core.perf

/** The three performance tiers. Everything that costs time or battery reads its budget from here. */
object Tier {
    const val PERFORMANCE = 0
    const val BALANCED = 1
    const val QUALITY = 2

    fun name(t: Int): String = when (t) {
        PERFORMANCE -> "Performance"
        BALANCED -> "Balanced"
        else -> "Quality"
    }

    fun blurb(t: Int): String = when (t) {
        PERFORMANCE -> "Lowest load. Overlays refresh slowly, no extra effects."
        BALANCED -> "Smoother overlays at a moderate cost."
        else -> "Sharp, fast overlays. Needs a stronger phone."
    }

    /** Overlay redraws per second inside the page. */
    fun overlayHz(t: Int): Int = when (t) { PERFORMANCE -> 15; BALANCED -> 30; else -> 60 }

    /** Pixel ratio of the overlay canvas. */
    fun overlayDpr(t: Int): Float = if (t >= QUALITY) 2f else 1f

    /** Most steering updates sent to the page per second. */
    fun inputHz(t: Int): Int = when (t) { PERFORMANCE -> 40; BALANCED -> 60; else -> 90 }

    /** Native HUD refreshes per second at most. */
    fun hudHz(t: Int): Int = when (t) { PERFORMANCE -> 2; BALANCED -> 4; else -> 8 }
}
