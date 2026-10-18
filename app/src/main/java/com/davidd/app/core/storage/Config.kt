package com.davidd.app.core.storage

import com.davidd.app.core.perf.Tier
import org.json.JSONArray
import org.json.JSONObject

internal fun clampI(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v

internal fun clampF(v: Double, lo: Float, hi: Float, dflt: Float): Float {
    if (v.isNaN() || v.isInfinite()) return dflt
    val f = v.toFloat()
    return if (f < lo) lo else if (f > hi) hi else f
}

/** Steering, buttons and haptics. */
class ControlsCfg {
    var mode = MODE_FLOATING
    var sensitivity = 1f
    var deadzone = 0.10f
    var stickScale = 1f
    var buttonScale = 1f
    var opacity = 0.60f
    var leftHanded = false
    var boostLock = false
    var haptics = true
    var always = false
    val actions = IntArray(SLOTS)
    /** Slot id -> normalised centre [x, y]. Missing = built-in position. */
    val layout = HashMap<String, FloatArray>()

    init {
        actions[0] = ACT_ZOOM_IN
        actions[1] = ACT_ZOOM_OUT
    }

    fun toJson(): JSONObject {
        val acts = JSONArray()
        for (a in actions) acts.put(a)
        val lay = JSONObject()
        for ((k, v) in layout) lay.put(k, JSONArray().put(v[0].toDouble()).put(v[1].toDouble()))
        return JSONObject()
            .put("mode", mode).put("sensitivity", sensitivity.toDouble()).put("deadzone", deadzone.toDouble())
            .put("stickScale", stickScale.toDouble()).put("buttonScale", buttonScale.toDouble())
            .put("opacity", opacity.toDouble()).put("leftHanded", leftHanded).put("boostLock", boostLock)
            .put("haptics", haptics).put("always", always).put("actions", acts).put("layout", lay)
    }

    fun load(o: JSONObject?) {
        if (o == null) return
        mode = clampI(o.optInt("mode", mode), 0, 2)
        sensitivity = clampF(o.optDouble("sensitivity", sensitivity.toDouble()), 0.5f, 2f, 1f)
        deadzone = clampF(o.optDouble("deadzone", deadzone.toDouble()), 0f, 0.4f, 0.1f)
        stickScale = clampF(o.optDouble("stickScale", stickScale.toDouble()), 0.6f, 1.6f, 1f)
        buttonScale = clampF(o.optDouble("buttonScale", buttonScale.toDouble()), 0.6f, 1.6f, 1f)
        opacity = clampF(o.optDouble("opacity", opacity.toDouble()), 0.2f, 1f, 0.6f)
        leftHanded = o.optBoolean("leftHanded", leftHanded)
        boostLock = o.optBoolean("boostLock", boostLock)
        haptics = o.optBoolean("haptics", haptics)
        always = o.optBoolean("always", always)
        val acts = o.optJSONArray("actions")
        if (acts != null) {
            for (i in 0 until SLOTS) actions[i] = clampI(acts.optInt(i, actions[i]), 0, ACT_COUNT - 1)
        }
        layout.clear()
        val lay = o.optJSONObject("layout")
        if (lay != null) {
            val keys = lay.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = lay.optJSONArray(k) ?: continue
                val x = clampF(a.optDouble(0, Double.NaN), 0f, 1f, -1f)
                val y = clampF(a.optDouble(1, Double.NaN), 0f, 1f, -1f)
                if (x >= 0f && y >= 0f) layout[k] = floatArrayOf(x, y)
            }
        }
    }

    companion object {
        const val MODE_FLOATING = 0
        const val MODE_FIXED = 1
        const val MODE_PAD = 2
        const val SLOTS = 4
        const val ACT_NONE = 0
        const val ACT_ZOOM_IN = 1
        const val ACT_ZOOM_OUT = 2
        const val ACT_RING = 3
        const val ACT_RADAR = 4
        const val ACT_GUIDE = 5
        const val ACT_TARGETS = 6
        const val ACT_LARGEST = 7
        const val ACT_HUD = 8
        const val ACT_MENU = 9
        const val ACT_COUNT = 10
    }
}

/** The numbers shown on screen while playing. */
class HudCfg {
    var enabled = true
    var score = true
    var best = true
    var time = false
    var runs = false
    var fps = false
    var corner = 0           // 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right
    var scale = 1f

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled).put("score", score).put("best", best).put("time", time)
        .put("runs", runs).put("fps", fps).put("corner", corner).put("scale", scale.toDouble())

    fun load(o: JSONObject?) {
        if (o == null) return
        enabled = o.optBoolean("enabled", enabled)
        score = o.optBoolean("score", score)
        best = o.optBoolean("best", best)
        time = o.optBoolean("time", time)
        runs = o.optBoolean("runs", runs)
        fps = o.optBoolean("fps", fps)
        corner = clampI(o.optInt("corner", corner), 0, 3)
        scale = clampF(o.optDouble("scale", scale.toDouble()), 0.7f, 1.6f, 1f)
    }
}

/** In-game overlays drawn by the page engine. */
class OverlayCfg {
    var guide = false
    var ring = false
    var targets = false
    var largest = false
    var radar = false
    var ringRange = 8f        // in body widths
    var targetCount = 3
    var radarRange = 700f     // world units

    fun any(): Boolean = guide || ring || targets || largest || radar

    fun toJson(): JSONObject = JSONObject()
        .put("guide", guide).put("ring", ring).put("targets", targets).put("largest", largest)
        .put("radar", radar).put("ringRange", ringRange.toDouble()).put("targetCount", targetCount)
        .put("radarRange", radarRange.toDouble())

    fun load(o: JSONObject?) {
        if (o == null) return
        guide = o.optBoolean("guide", guide)
        ring = o.optBoolean("ring", ring)
        targets = o.optBoolean("targets", targets)
        largest = o.optBoolean("largest", largest)
        radar = o.optBoolean("radar", radar)
        ringRange = clampF(o.optDouble("ringRange", ringRange.toDouble()), 2f, 30f, 8f)
        targetCount = clampI(o.optInt("targetCount", targetCount), 1, 6)
        radarRange = clampF(o.optDouble("radarRange", radarRange.toDouble()), 200f, 3000f, 700f)
    }
}

class PerfCfg {
    var blockAds = true
    var keepOn = true
    var motion = false        // small UI transitions

    fun toJson(): JSONObject = JSONObject().put("blockAds", blockAds).put("keepOn", keepOn).put("motion", motion)

    fun load(o: JSONObject?) {
        if (o == null) return
        blockAds = o.optBoolean("blockAds", blockAds)
        keepOn = o.optBoolean("keepOn", keepOn)
        motion = o.optBoolean("motion", motion)
    }
}

/** Everything one profile stores. Plain mutable objects: no observers, no per-field allocations. */
class Config {
    var tier = Tier.PERFORMANCE
    val controls = ControlsCfg()
    val hud = HudCfg()
    val overlays = OverlayCfg()
    val perf = PerfCfg()

    fun toJson(): JSONObject = JSONObject()
        .put("tier", tier)
        .put("controls", controls.toJson())
        .put("hud", hud.toJson())
        .put("overlays", overlays.toJson())
        .put("perf", perf.toJson())

    fun copy(): Config = fromJson(toJson())

    companion object {
        /** Never throws: anything missing or out of range falls back to a safe default. */
        fun fromJson(o: JSONObject?): Config {
            val c = Config()
            if (o == null) return c
            c.tier = clampI(o.optInt("tier", Tier.PERFORMANCE), 0, 2)
            c.controls.load(o.optJSONObject("controls"))
            c.hud.load(o.optJSONObject("hud"))
            c.overlays.load(o.optJSONObject("overlays"))
            c.perf.load(o.optJSONObject("perf"))
            return c
        }
    }
}
