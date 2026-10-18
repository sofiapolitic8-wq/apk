package com.davidd.app.features.controls

import com.davidd.app.core.storage.ControlsCfg

/** Slot ids and built-in positions (normalised 0..1 of the screen). Mirrored for left-handed play. */
object Layout {
    const val BOOST = 0
    const val A1 = 1
    const val A2 = 2
    const val A3 = 3
    const val A4 = 4
    const val STICK = 5
    const val COUNT = 6

    private val ids = arrayOf("boost", "a1", "a2", "a3", "a4", "stick")
    private val defX = floatArrayOf(0.88f, 0.72f, 0.92f, 0.80f, 0.62f, 0.14f)
    private val defY = floatArrayOf(0.70f, 0.86f, 0.44f, 0.24f, 0.80f, 0.70f)

    fun id(slot: Int): String = ids[slot]

    fun x(c: ControlsCfg, slot: Int): Float {
        val p = c.layout[ids[slot]]
        if (p != null) return p[0]
        return if (c.leftHanded) 1f - defX[slot] else defX[slot]
    }

    fun y(c: ControlsCfg, slot: Int): Float {
        val p = c.layout[ids[slot]]
        return if (p != null) p[1] else defY[slot]
    }

    fun set(c: ControlsCfg, slot: Int, nx: Float, ny: Float) {
        val x = if (nx < 0f) 0f else if (nx > 1f) 1f else nx
        val y = if (ny < 0f) 0f else if (ny > 1f) 1f else ny
        c.layout[ids[slot]] = floatArrayOf(x, y)
    }

    fun reset(c: ControlsCfg) { c.layout.clear() }
}
