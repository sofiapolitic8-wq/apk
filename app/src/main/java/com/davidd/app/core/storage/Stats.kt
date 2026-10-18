package com.davidd.app.core.storage

import android.content.Context
import android.content.SharedPreferences

/** Best score, run count and total play time. Written only when a run ends or the app pauses. */
object Stats {
    private var prefs: SharedPreferences? = null
    var best = 0
    var runs = 0
    var totalMs = 0L

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("davidd_stats", Context.MODE_PRIVATE)
        prefs = p
        best = p.getInt("best", 0).coerceAtLeast(0)
        runs = p.getInt("runs", 0).coerceAtLeast(0)
        totalMs = p.getLong("totalMs", 0L).coerceAtLeast(0L)
    }

    fun save() {
        prefs?.edit()?.putInt("best", best)?.putInt("runs", runs)?.putLong("totalMs", totalMs)?.apply()
    }

    fun reset() {
        best = 0; runs = 0; totalMs = 0L
        save()
    }

    /** Last probe report from the page engine, kept so the Status page can show it later. */
    fun saveProbe(ctx: Context, json: String) {
        ctx.applicationContext.getSharedPreferences("davidd_probe", Context.MODE_PRIVATE).edit().putString("last", json).apply()
    }

    fun loadProbe(ctx: Context): String? =
        ctx.applicationContext.getSharedPreferences("davidd_probe", Context.MODE_PRIVATE).getString("last", null)
}
