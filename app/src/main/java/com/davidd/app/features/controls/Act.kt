package com.davidd.app.features.controls

import com.davidd.app.core.storage.ControlsCfg

/** What a configurable button can do. Numbers are stored in profiles, so only ever append. */
object Act {
    private val names = arrayOf(
        "Nothing", "Zoom in", "Zoom out", "Danger ring", "Food radar",
        "Heading guide", "Target arrows", "Biggest snake", "Show / hide HUD", "Quick menu"
    )
    private val shorts = arrayOf("", "+", "\u2212", "RNG", "RDR", "GDE", "TGT", "BIG", "HUD", "MNU")

    fun name(a: Int): String = if (a in 0 until ControlsCfg.ACT_COUNT) names[a] else names[0]
    fun short(a: Int): String = if (a in 0 until ControlsCfg.ACT_COUNT) shorts[a] else ""
    fun next(a: Int): Int = (a + 1) % ControlsCfg.ACT_COUNT
}
