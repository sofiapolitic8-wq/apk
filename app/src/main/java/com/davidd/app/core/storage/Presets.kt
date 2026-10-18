package com.davidd.app.core.storage

import com.davidd.app.core.perf.Tier

/** The profiles every install starts with. */
object Presets {
    const val DEFAULT = "Default"
    const val COMPETITIVE = "Competitive"
    const val LOW_END = "Low-End"

    val names: List<String> = listOf(DEFAULT, COMPETITIVE, LOW_END)

    fun build(name: String): Config {
        val c = Config()
        when (name) {
            COMPETITIVE -> {
                c.tier = Tier.BALANCED
                c.overlays.ring = true
                c.overlays.targets = true
                c.overlays.largest = true
                c.overlays.radar = true
                c.hud.time = true
                c.hud.runs = true
                c.controls.actions[2] = ControlsCfg.ACT_RADAR
                c.controls.actions[3] = ControlsCfg.ACT_RING
            }
            LOW_END -> {
                c.tier = Tier.PERFORMANCE
                c.hud.best = false
                c.controls.haptics = false
                c.controls.opacity = 0.5f
            }
            else -> c.tier = Tier.PERFORMANCE   // Default favours performance: no overlays until asked for.
        }
        return c
    }
}
