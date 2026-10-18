package com.davidd.app.features.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.davidd.app.core.storage.ControlsCfg
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Touch surface drawn over the game. One view, no child views, nothing allocated while drawing or touching.
 * Modes: floating stick, fixed stick, touch pad. Buttons: boost + four assignable action buttons.
 * Redraws only when something changes.
 */
class ControlsView(ctx: Context) : View(ctx) {

    interface Listener {
        /** Direction the snake should head (|v| <= 1). Only called when the direction changes. */
        fun onAim(vx: Float, vy: Float)
        fun onBoost(on: Boolean)
        fun onAction(act: Int)
        fun onEditDone()
    }

    var listener: Listener? = null
    /** Called after a drag in edit mode so the owner can save. */
    var onLayoutChanged: (() -> Unit)? = null

    private var cc = ControlsCfg()
    private var editing = false

    private val d = ctx.resources.displayMetrics.density
    private val accent = 0xFF8CF5A5.toInt()

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val chip = RectF()

    private val bx = FloatArray(Layout.COUNT)
    private val by = FloatArray(Layout.COUNT)
    private val br = FloatArray(Layout.COUNT)
    private var stickR = 0f

    private var stickId = -1
    private var ox = 0f
    private var oy = 0f
    private var kx = 0f
    private var ky = 0f
    private var padId = -1
    private var padX = 0f
    private var padY = 0f
    private var tx = 0f
    private var ty = 1f
    private val slotOf = IntArray(Layout.COUNT) { -1 }
    private var dragSlot = -1
    private var dragId = -1
    private var boostHeld = false
    private var lastVx = 2f
    private var lastVy = 2f

    fun setConfig(c: ControlsCfg) {
        cc = c
        rebuild()
        invalidate()
    }

    fun setEditing(on: Boolean) {
        editing = on
        releaseAll()
        invalidate()
    }

    private fun slotVisible(slot: Int): Boolean {
        if (editing) return slot != Layout.STICK || cc.mode == ControlsCfg.MODE_FIXED
        return when (slot) {
            Layout.BOOST -> true
            Layout.STICK -> false
            else -> cc.actions[slot - 1] != ControlsCfg.ACT_NONE
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rebuild()
    }

    private fun rebuild() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        stickR = 52f * d * cc.stickScale
        for (s in 0 until Layout.COUNT) {
            bx[s] = Layout.x(cc, s) * w
            by[s] = Layout.y(cc, s) * h
            br[s] = when (s) {
                Layout.BOOST -> 38f * d * cc.buttonScale
                Layout.STICK -> stickR
                else -> 26f * d * cc.buttonScale
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    private fun hit(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (s in 0 until Layout.COUNT) {
            if (s == Layout.STICK || !slotVisible(s)) continue
            val dd = hypot(x - bx[s], y - by[s])
            if (dd <= br[s] * 1.25f && dd < bestD) { best = s; bestD = dd }
        }
        return best
    }

    private fun buzz() {
        if (cc.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (editing) {
            val ch = chipHit(x, y)
            if (ch == 0) { listener?.onEditDone(); return }
            if (ch == 1) { Layout.reset(cc); rebuild(); invalidate(); onLayoutChanged?.invoke(); return }
            var s = hit(x, y)
            if (s < 0 && cc.mode == ControlsCfg.MODE_FIXED && hypot(x - bx[Layout.STICK], y - by[Layout.STICK]) <= stickR * 1.25f) s = Layout.STICK
            if (s >= 0 && dragId < 0) { dragSlot = s; dragId = id }
            return
        }
        val s = hit(x, y)
        if (s >= 0) {
            if (slotOf[s] >= 0) return
            slotOf[s] = id
            buzz()
            if (s == Layout.BOOST) {
                if (cc.boostLock) { boostHeld = !boostHeld; listener?.onBoost(boostHeld) }
                else { boostHeld = true; listener?.onBoost(true) }
            } else {
                listener?.onAction(cc.actions[s - 1])
            }
            invalidate()
            return
        }
        if (cc.mode == ControlsCfg.MODE_PAD) {
            if (padId < 0) { padId = id; padX = x; padY = y; invalidate() }
        } else if (cc.mode == ControlsCfg.MODE_FIXED) {
            if (stickId < 0 && hypot(x - bx[Layout.STICK], y - by[Layout.STICK]) <= stickR * 1.6f) {
                stickId = id; ox = bx[Layout.STICK]; oy = by[Layout.STICK]; kx = x; ky = y; aimStick(x, y); invalidate()
            }
        } else {
            val onStickSide = if (cc.leftHanded) x > width * 0.4f else x < width * 0.6f
            if (stickId < 0 && onStickSide) {
                stickId = id
                ox = clampTo(x, stickR, width - stickR); oy = clampTo(y, stickR, height - stickR)
                kx = ox; ky = oy
                invalidate()
            }
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (editing) {
            if (id == dragId && dragSlot >= 0 && width > 0 && height > 0) {
                Layout.set(cc, dragSlot, x / width, y / height)
                rebuild(); invalidate()
            }
            return
        }
        if (id == stickId) { aimStick(x, y); invalidate() }
        else if (id == padId) {
            val gain = cc.sensitivity * 2.4f / min(width, height).toFloat()
            tx += (x - padX) * gain
            ty += (y - padY) * gain
            padX = x; padY = y
            val len = hypot(tx, ty)
            if (len > 1f) { tx /= len; ty /= len }
            if (len > 0.02f) emitAim(tx / len, ty / len)
            invalidate()
        }
    }

    private fun up(id: Int) {
        if (editing) {
            if (id == dragId) { dragId = -1; dragSlot = -1; onLayoutChanged?.invoke() }
            return
        }
        if (id == stickId) { stickId = -1; invalidate() }
        if (id == padId) { padId = -1; invalidate() }
        for (s in 0 until Layout.COUNT) {
            if (slotOf[s] == id) {
                slotOf[s] = -1
                if (s == Layout.BOOST && !cc.boostLock && boostHeld) { boostHeld = false; listener?.onBoost(false) }
                invalidate()
            }
        }
    }

    private fun releaseAll() {
        stickId = -1; padId = -1; dragId = -1; dragSlot = -1
        for (s in 0 until Layout.COUNT) slotOf[s] = -1
        if (boostHeld) { boostHeld = false; listener?.onBoost(false) }
        invalidate()
    }

    private fun aimStick(x: Float, y: Float) {
        val dx = x - ox
        val dy = y - oy
        val len = hypot(dx, dy)
        val reach = stickR / cc.sensitivity
        if (len < 1f) return
        val f = if (len > stickR) stickR / len else 1f
        kx = ox + dx * f; ky = oy + dy * f
        if (len / reach < cc.deadzone) return
        emitAim(dx / len, dy / len)
    }

    private fun clampTo(v: Float, lo: Float, hi: Float): Float {
        if (hi < lo) return (lo + hi) / 2f
        return if (v < lo) lo else if (v > hi) hi else v
    }

    private fun emitAim(vx: Float, vy: Float) {
        if (abs(vx - lastVx) < 0.005f && abs(vy - lastVy) < 0.005f) return
        lastVx = vx; lastVy = vy
        listener?.onAim(vx, vy)
    }

    private fun chipHit(x: Float, y: Float): Int {
        val w = width.toFloat()
        val cw = 92f * d
        val ch = 36f * d
        val top = 10f * d
        if (y < top || y > top + ch) return -1
        val left = w / 2f - cw - 6f * d
        if (x >= left && x <= left + cw) return 0
        val right = w / 2f + 6f * d
        if (x >= right && x <= right + cw) return 1
        return -1
    }

    override fun onDraw(c: Canvas) {
        val a = cc.opacity
        if (stickId >= 0 || (editing && cc.mode == ControlsCfg.MODE_FIXED)) {
            val sx = if (stickId >= 0) ox else bx[Layout.STICK]
            val sy = if (stickId >= 0) oy else by[Layout.STICK]
            fill.color = argb(a * 0.10f, 255, 255, 255)
            c.drawCircle(sx, sy, stickR, fill)
            stroke.color = argb(a * 0.8f, 255, 255, 255)
            c.drawCircle(sx, sy, stickR, stroke)
            if (stickId >= 0) {
                fill.color = argb(a, 140, 245, 165)
                c.drawCircle(kx, ky, stickR * 0.42f, fill)
            } else {
                text.color = argb(a, 255, 255, 255)
                label(c, "STICK", sx, sy, 12f)
            }
        }
        if (cc.mode == ControlsCfg.MODE_FIXED && !editing && stickId < 0) {
            stroke.color = argb(a * 0.45f, 255, 255, 255)
            c.drawCircle(bx[Layout.STICK], by[Layout.STICK], stickR, stroke)
        }
        if (cc.mode == ControlsCfg.MODE_PAD && padId >= 0) {
            val cx = width / 2f
            val cy = height / 2f
            stroke.color = argb(a * 0.6f, 140, 245, 165)
            c.drawCircle(cx, cy, 34f * d, stroke)
            fill.color = argb(a, 140, 245, 165)
            c.drawCircle(cx + tx * 34f * d, cy + ty * 34f * d, 7f * d, fill)
        }
        for (s in 0 until Layout.COUNT) {
            if (s == Layout.STICK || !slotVisible(s)) continue
            val pressed = slotOf[s] >= 0 || (s == Layout.BOOST && boostHeld)
            fill.color = if (pressed) argb(a * 0.9f, 140, 245, 165) else argb(a * 0.14f, 255, 255, 255)
            c.drawCircle(bx[s], by[s], br[s], fill)
            stroke.color = if (editing) argb(1f, 255, 200, 87) else argb(a * 0.85f, 255, 255, 255)
            c.drawCircle(bx[s], by[s], br[s], stroke)
            val t = if (s == Layout.BOOST) "BOOST" else Act.short(cc.actions[s - 1]).ifEmpty { "\u00b7" }
            text.color = if (pressed) 0xFF0A0E13.toInt() else argb(a, 255, 255, 255)
            label(c, t, bx[s], by[s], if (s == Layout.BOOST) 12f else 14f)
        }
        if (editing) {
            val w = width.toFloat()
            val cw = 92f * d
            val ch = 36f * d
            val top = 10f * d
            chip.set(w / 2f - cw - 6f * d, top, w / 2f - 6f * d, top + ch)
            fill.color = accent
            c.drawRoundRect(chip, 10f * d, 10f * d, fill)
            text.color = 0xFF0A0E13.toInt()
            label(c, "DONE", chip.centerX(), chip.centerY(), 13f)
            chip.set(w / 2f + 6f * d, top, w / 2f + cw + 6f * d, top + ch)
            fill.color = argb(0.2f, 255, 255, 255)
            c.drawRoundRect(chip, 10f * d, 10f * d, fill)
            text.color = 0xFFFFFFFF.toInt()
            label(c, "RESET", chip.centerX(), chip.centerY(), 13f)
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, sp: Float) {
        text.textSize = sp * d
        c.drawText(s, x, y - (text.descent() + text.ascent()) / 2f, text)
    }

    private fun argb(alpha: Float, r: Int, g: Int, b: Int): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
