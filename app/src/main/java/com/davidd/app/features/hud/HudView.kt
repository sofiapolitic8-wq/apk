package com.davidd.app.features.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.davidd.app.core.storage.HudCfg

/** Small readout (score / best / time / runs / fps). Redraws only when a value really changed. Never takes touches. */
class HudView(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000 }
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A98AC.toInt(); typeface = Typeface.DEFAULT_BOLD }
    private val valueP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EEF5.toInt(); typeface = Typeface.MONOSPACE }
    private val rect = RectF()

    private var cfg = HudCfg()
    private val labels = arrayOfNulls<String>(5)
    private val values = arrayOfNulls<String>(5)
    private var lines = 0

    private var score = -1
    private var best = -1
    private var timeSec = -1
    private var runs = -1
    private var fps = -1

    init {
        isClickable = false
        isFocusable = false
    }

    fun setConfig(c: HudCfg) {
        cfg = c
        rebuild()
    }

    /** Pass -1 for "not available". Only changed values cause a redraw. */
    fun update(score: Int, best: Int, timeSec: Int, runs: Int, fps: Int) {
        if (score == this.score && best == this.best && timeSec == this.timeSec && runs == this.runs && fps == this.fps) return
        this.score = score; this.best = best; this.timeSec = timeSec; this.runs = runs; this.fps = fps
        rebuild()
    }

    private fun rebuild() {
        var n = 0
        if (cfg.score) { labels[n] = "SCORE"; values[n] = if (score >= 0) score.toString() else "-"; n++ }
        if (cfg.best) { labels[n] = "BEST"; values[n] = if (best >= 0) best.toString() else "-"; n++ }
        if (cfg.time) { labels[n] = "TIME"; values[n] = if (timeSec >= 0) clock(timeSec) else "-"; n++ }
        if (cfg.runs) { labels[n] = "RUNS"; values[n] = if (runs >= 0) runs.toString() else "-"; n++ }
        if (cfg.fps) { labels[n] = "FPS"; values[n] = if (fps >= 0) fps.toString() else "-"; n++ }
        lines = n
        requestLayout()
        invalidate()
    }

    private fun clock(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return (if (m < 10) "0" else "") + m + ":" + (if (s < 10) "0" else "") + s
    }

    private fun lineH() = 18f * d * cfg.scale

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        if (lines == 0) { setMeasuredDimension(0, 0); return }
        val w = (116f * d * cfg.scale).toInt()
        val h = (lines * lineH() + 10f * d * cfg.scale).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(c: Canvas) {
        if (lines == 0) return
        val s = cfg.scale
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(rect, 8f * d, 8f * d, bg)
        labelP.textSize = 10f * d * s
        valueP.textSize = 13f * d * s
        val lh = lineH()
        var y = 5f * d * s + lh * 0.72f
        for (i in 0 until lines) {
            c.drawText(labels[i] ?: "", 8f * d * s, y, labelP)
            val v = values[i] ?: ""
            c.drawText(v, width - 8f * d * s - valueP.measureText(v), y, valueP)
            y += lh
        }
    }
}
