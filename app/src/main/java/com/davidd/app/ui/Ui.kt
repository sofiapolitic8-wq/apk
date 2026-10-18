package com.davidd.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * DAVIDD's own small component set. Dark first, compact, flat. Every control is a single custom-drawn view or a
 * plain LinearLayout; no images, no style files, no animation unless a caller adds one.
 */
object Ui {
    val BG = Color.rgb(10, 14, 19)
    val SURFACE = Color.rgb(17, 24, 35)
    val SURFACE2 = Color.rgb(24, 33, 46)
    val LINE = Color.rgb(36, 48, 66)
    val TEXT = Color.rgb(232, 238, 245)
    val DIM = Color.rgb(138, 152, 172)
    val ACCENT = Color.rgb(140, 245, 165)
    val AMBER = Color.rgb(255, 200, 87)
    val DANGER = Color.rgb(255, 107, 107)

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun shape(c: Context, fill: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = radiusDp * c.resources.displayMetrics.density
        if (strokeDp > 0f) g.setStroke(dp(c, strokeDp), strokeColor)
        return g
    }

    fun label(c: Context, s: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        t.includeFontPadding = false
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun vertical(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    fun horizontal(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

    fun gap(c: Context, dpv: Float): View {
        val v = View(c)
        v.layoutParams = lp(1, dp(c, dpv))
        return v
    }

    /** A rounded panel that groups related rows. */
    fun card(c: Context): LinearLayout {
        val l = vertical(c)
        l.background = shape(c, SURFACE, 14f, LINE, 1f)
        l.setPadding(dp(c, 14f), dp(c, 6f), dp(c, 14f), dp(c, 6f))
        return l
    }

    fun header(c: Context, s: String): TextView {
        val t = label(c, s.uppercase(), 11f, DIM, true)
        t.letterSpacing = 0.1f
        t.setPadding(dp(c, 4f), dp(c, 16f), 0, dp(c, 8f))
        return t
    }

    /** Title (+ optional hint) on the left, a control on the right. */
    fun row(c: Context, title: String, hint: String?, control: View): LinearLayout {
        val r = horizontal(c)
        r.setPadding(0, dp(c, 11f), 0, dp(c, 11f))
        val texts = vertical(c)
        texts.addView(label(c, title, 15f, TEXT))
        if (hint != null) {
            val h = label(c, hint, 12f, DIM)
            h.setPadding(0, dp(c, 2f), 0, 0)
            texts.addView(h)
        }
        r.addView(texts, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(control, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return r
    }

    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(LINE)
        v.layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        return v
    }

    fun toggleRow(c: Context, title: String, hint: String?, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val t = ToggleView(c)
        t.checked = initial
        t.onChange = onChange
        val r = row(c, title, hint, t)
        r.setOnClickListener { t.performClick() }
        return r
    }

    fun sliderRow(
        c: Context, title: String, min: Float, max: Float, step: Float, initial: Float,
        fmt: (Float) -> String, onChange: (Float) -> Unit
    ): View {
        val box = vertical(c)
        box.setPadding(0, dp(c, 10f), 0, dp(c, 6f))
        val top = horizontal(c)
        top.addView(label(c, title, 15f, TEXT), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = label(c, fmt(initial), 13f, ACCENT, true)
        top.addView(value)
        box.addView(top)
        val s = SliderView(c)
        s.min = min; s.max = max; s.step = step; s.value = initial
        s.onChange = { v -> value.text = fmt(v); onChange(v) }
        box.addView(s, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 36f)))
        return box
    }

    /** Equal-width options in a pill. */
    fun segmented(c: Context, options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val wrap = horizontal(c)
        wrap.background = shape(c, SURFACE2, 10f, LINE, 1f)
        wrap.setPadding(dp(c, 3f), dp(c, 3f), dp(c, 3f), dp(c, 3f))
        val tabs = ArrayList<TextView>()
        fun paint(sel: Int) {
            for (i in tabs.indices) {
                val on = i == sel
                tabs[i].setTextColor(if (on) BG else DIM)
                tabs[i].background = if (on) shape(c, ACCENT, 8f) else null
            }
        }
        for (i in options.indices) {
            val t = label(c, options[i], 13f, DIM, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(c, 12f), dp(c, 8f), dp(c, 12f), dp(c, 8f))
            t.setOnClickListener { paint(i); onSelect(i) }
            tabs.add(t)
            wrap.addView(t, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(selected)
        return wrap
    }

    fun button(c: Context, text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = label(c, text, 14f, if (primary) BG else TEXT, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(c, 18f), dp(c, 13f), dp(c, 18f), dp(c, 13f))
        t.background = if (primary) shape(c, ACCENT, 12f) else shape(c, SURFACE2, 12f, LINE, 1f)
        t.setOnClickListener { onClick() }
        return t
    }

    /** Small selectable pill, used for profiles. */
    fun chip(c: Context, text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val t = label(c, text, 13f, if (selected) BG else TEXT, true)
        t.setPadding(dp(c, 14f), dp(c, 8f), dp(c, 14f), dp(c, 8f))
        t.background = if (selected) shape(c, ACCENT, 18f) else shape(c, SURFACE2, 18f, LINE, 1f)
        t.setOnClickListener { onClick() }
        return t
    }

    /** Horizontal strip of tab labels. */
    fun tabs(c: Context, names: List<String>, selected: Int, onSelect: (Int) -> Unit): HorizontalScrollView {
        val sv = HorizontalScrollView(c)
        sv.isHorizontalScrollBarEnabled = false
        val row = horizontal(c)
        row.setPadding(dp(c, 12f), dp(c, 6f), dp(c, 12f), dp(c, 6f))
        val chips = ArrayList<TextView>()
        fun paint(sel: Int) {
            for (i in chips.indices) {
                val on = i == sel
                chips[i].setTextColor(if (on) BG else TEXT)
                chips[i].background = if (on) shape(c, ACCENT, 18f) else shape(c, SURFACE2, 18f, LINE, 1f)
            }
        }
        for (i in names.indices) {
            val t = label(c, names[i], 13f, TEXT, true)
            t.setPadding(dp(c, 14f), dp(c, 8f), dp(c, 14f), dp(c, 8f))
            t.setOnClickListener { paint(i); onSelect(i) }
            chips.add(t)
            val p = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = dp(c, 8f)
            row.addView(t, p)
        }
        paint(selected)
        sv.addView(row)
        return sv
    }
}

/** On/off switch, drawn by hand. 46 x 28 dp. */
class ToggleView(ctx: Context) : View(ctx) {
    var checked: Boolean = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var onChange: ((Boolean) -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = true
        setOnClickListener {
            checked = !checked
            onChange?.invoke(checked)
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((46f * d).toInt(), (28f * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.style = Paint.Style.FILL
        track.color = if (checked) Ui.ACCENT else Ui.SURFACE2
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, h / 2f, h / 2f, track)
        if (!checked) {
            track.color = Ui.LINE
            track.style = Paint.Style.STROKE
            track.strokeWidth = 1.5f * d
            rect.set(0.75f * d, 0.75f * d, w - 0.75f * d, h - 0.75f * d)
            c.drawRoundRect(rect, h / 2f, h / 2f, track)
            track.style = Paint.Style.FILL
        }
        knob.color = if (checked) Ui.BG else Ui.DIM
        val r = h / 2f - 5f * d
        val cx = if (checked) w - h / 2f else h / 2f
        c.drawCircle(cx, h / 2f, r, knob)
    }
}

/** Horizontal slider, drawn by hand, with a 36 dp touch height. */
class SliderView(ctx: Context) : View(ctx) {
    var min = 0f
    var max = 1f
    var step = 0f
    var value: Float = 0f
        set(v) { if (field != v) { field = v; invalidate() } }
    var onChange: ((Float) -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun pad() = 12f * d

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun update(x: Float) {
        val span = width - 2f * pad()
        if (span <= 0f || max <= min) return
        var f = (x - pad()) / span
        if (f < 0f) f = 0f
        if (f > 1f) f = 1f
        var v = min + f * (max - min)
        if (step > 0f) v = round(v / step) * step
        if (v < min) v = min
        if (v > max) v = max
        if (v != value) {
            value = v
            onChange?.invoke(v)
        }
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val cy = h / 2f
        val x0 = pad()
        val x1 = width - pad()
        val frac = if (max > min) (value - min) / (max - min) else 0f
        val xv = x0 + (x1 - x0) * frac.coerceIn(0f, 1f)
        val t = 4f * d
        p.color = Ui.SURFACE2
        rect.set(x0, cy - t / 2f, x1, cy + t / 2f)
        c.drawRoundRect(rect, t / 2f, t / 2f, p)
        p.color = Ui.ACCENT
        rect.set(x0, cy - t / 2f, xv, cy + t / 2f)
        c.drawRoundRect(rect, t / 2f, t / 2f, p)
        c.drawCircle(xv, cy, 9f * d, p)
        p.color = Ui.BG
        c.drawCircle(xv, cy, 4f * d, p)
    }
}

/** The DAVIDD mark: one spiral stroke (same drawing as the launcher icon). */
class MarkView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Ui.ACCENT
    }
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        path.reset()
        val cx = w / 2f
        val cy = h / 2f
        val rMax = (if (w < h) w else h) / 2f * 0.92f
        val tMax = 4.3 * Math.PI
        val n = 90
        for (i in 0..n) {
            val t = tMax * i / n
            val r = rMax * (2.0 + 1.42 * t) / (2.0 + 1.42 * tMax)
            val x = cx + (r * cos(t - Math.PI / 2)).toFloat()
            val y = cy + (r * sin(t - Math.PI / 2)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        p.strokeWidth = rMax * 0.13f
    }

    override fun onDraw(c: Canvas) {
        c.drawPath(path, p)
    }
}
