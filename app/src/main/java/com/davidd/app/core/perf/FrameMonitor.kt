package com.davidd.app.core.perf

import android.view.Choreographer

/** Counts display frames, but only while started (the HUD's FPS line). No timers, no allocation per frame. */
class FrameMonitor(private val onFps: (Int) -> Unit) : Choreographer.FrameCallback {
    private var running = false
    private var windowStart = 0L
    private var frames = 0

    fun start() {
        if (running) return
        running = true
        windowStart = 0L
        frames = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (windowStart == 0L) windowStart = frameTimeNanos
        frames++
        val dt = frameTimeNanos - windowStart
        if (dt >= 500_000_000L) {
            onFps((frames * 1_000_000_000L / dt).toInt())
            windowStart = frameTimeNanos
            frames = 0
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}
