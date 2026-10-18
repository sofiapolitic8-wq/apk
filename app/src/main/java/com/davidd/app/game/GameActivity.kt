package com.davidd.app.game

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.davidd.app.core.perf.FrameMonitor
import com.davidd.app.core.perf.Tier
import com.davidd.app.core.storage.ControlsCfg
import com.davidd.app.core.storage.ProfileStore
import com.davidd.app.core.storage.Stats
import com.davidd.app.features.controls.ControlsView
import com.davidd.app.features.hud.HudView
import com.davidd.app.ui.EditorActivity
import com.davidd.app.ui.Immersive
import com.davidd.app.ui.Ui
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Hosts the game page and everything drawn over it.
 * Cost rules: the page engine only works while an overlay is on; the HUD timer only runs while a match is
 * being played and the HUD is visible; the FPS counter only runs when the FPS line is on; steering is throttled
 * to the tier's input rate; nothing polls while the activity is paused.
 */
class GameActivity : Activity(), GameBridge.Host, QuickSheet.Host, ControlsView.Listener {

    private lateinit var root: FrameLayout
    private var web: WebView? = null
    private lateinit var controls: ControlsView
    private lateinit var hud: HudView
    private lateinit var sheet: QuickSheet
    private lateinit var menuBtn: View
    private lateinit var errorLayer: View

    private val main = Handler(Looper.getMainLooper())
    private var playing = false
    private var score = -1
    private var runStart = 0L
    private var acctStart = 0L       // start of the span not yet added to total play time
    private var rendererDead = false
    private var fps = -1
    private var engineLoaded = false
    private var resumed = false

    // steering throttle
    private var aimVx = 0f
    private var aimVy = 0f
    private var aimDirty = false
    private var aimScheduled = false
    private var lastAimAt = 0L

    private val monitor = FrameMonitor { v -> fps = v }

    private val pushTask = Runnable { pushConfigNow() }
    private val aimTask = Runnable { aimScheduled = false; flushAim() }
    private val hudTask = object : Runnable {
        override fun run() {
            refreshHud()
            if (playing && ProfileStore.config().hud.enabled) {
                main.postDelayed(this, 1000L / Tier.hudHz(ProfileStore.config().tier))
            }
        }
    }
    private val settingsListener: () -> Unit = { applyConfig(false) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ProfileStore.init(this)
        Stats.init(this)
        Immersive.enter(window)

        root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        setContentView(root)

        val wv = WebView(this)
        web = wv
        configureWeb(wv)
        root.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        hud = HudView(this)
        root.addView(hud, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        controls = ControlsView(this)
        controls.listener = this
        controls.visibility = View.GONE
        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val mb = Ui.label(this, "\u2261", 20f, Ui.TEXT, true)
        mb.gravity = Gravity.CENTER
        mb.background = Ui.shape(this, 0x66000000, 18f)
        mb.alpha = 0.7f
        mb.setOnClickListener { openSheet() }
        menuBtn = mb
        val s = Ui.dp(this, 40f)
        root.addView(mb, FrameLayout.LayoutParams(s, s, Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = Ui.dp(this, 6f) })

        sheet = QuickSheet(this, this)
        sheet.visibility = View.GONE
        root.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        errorLayer = buildErrorLayer()
        errorLayer.visibility = View.GONE
        root.addView(errorLayer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        applyConfig(true)
        wv.loadUrl(GAME_URL)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configureWeb(wv: WebView) {
        val st = wv.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.allowFileAccess = false
        st.allowContentAccess = false
        st.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        st.mediaPlaybackRequiresUserGesture = false
        st.setSupportZoom(false)
        st.builtInZoomControls = false
        st.cacheMode = WebSettings.LOAD_DEFAULT
        wv.setBackgroundColor(Ui.BG)
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.addJavascriptInterface(GameBridge(this), "DAVIDDBridge")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = !allowedHost(r.url?.host)

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (ProfileStore.config().perf.blockAds && Blocklist.matches(r.url?.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(v: WebView, url: String?, icon: Bitmap?) {
                engineLoaded = false
                errorLayer.visibility = View.GONE
            }

            override fun onPageFinished(v: WebView, url: String?) {
                injectEngine(v)
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) errorLayer.visibility = View.VISIBLE
            }

            override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                // The page's renderer died (often low memory). Keep the app alive and offer a retry.
                rendererDead = true
                resetRun()
                errorLayer.visibility = View.VISIBLE
                return true
            }
        }
    }

    private fun allowedHost(h: String?): Boolean {
        if (h == null) return false
        val x = h.lowercase()
        return x == "slither.io" || x.endsWith(".slither.io") || x == "slither.com" || x.endsWith(".slither.com")
    }

    private fun buildErrorLayer(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setBackgroundColor(Ui.BG)
        box.isClickable = true
        box.addView(Ui.label(this, "Can't reach the game", 18f, Ui.TEXT, true))
        box.addView(Ui.gap(this, 6f))
        box.addView(Ui.label(this, "Check your connection and try again.", 13f, Ui.DIM))
        box.addView(Ui.gap(this, 16f))
        box.addView(Ui.button(this, "Retry", true) {
            errorLayer.visibility = View.GONE
            reloadPage()
        })
        box.addView(Ui.gap(this, 8f))
        box.addView(Ui.button(this, "Back", false) { finish() })
        return box
    }

    private fun reloadPage() {
        val old = web
        if (old == null || old.parent == null || rendererDead) {
            rendererDead = false
            old?.let { root.removeView(it) }
            // Renderer was lost: build a fresh WebView.
            old?.let { it.destroy() }
            val wv = WebView(this)
            web = wv
            configureWeb(wv)
            root.addView(wv, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            wv.loadUrl(GAME_URL)
        } else {
            old.loadUrl(GAME_URL)
        }
    }

    // ------------------------------------------------------------ engine

    private fun injectEngine(v: WebView) {
        val src = engineSource() ?: return
        v.evaluateJavascript(src, null)
        engineLoaded = true
        pushConfigNow()
    }

    private fun engineSource(): String? {
        cachedEngine?.let { return it }
        return try {
            val s = assets.open("engine.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
            cachedEngine = s
            s
        } catch (e: Exception) {
            null
        }
    }

    private fun js(code: String) {
        val v = web ?: return
        if (!engineLoaded) return
        v.evaluateJavascript(code, null)
    }

    private fun schedulePush() {
        main.removeCallbacks(pushTask)
        main.postDelayed(pushTask, 150L)
    }

    private fun pushConfigNow() {
        if (!engineLoaded) return
        val cfg = ProfileStore.config()
        val o = cfg.overlays.toJson()
        o.put("overlayHz", Tier.overlayHz(cfg.tier))
        o.put("dpr", Tier.overlayDpr(cfg.tier).toDouble())
        o.put("report", true)
        js("window.__davidd&&__davidd.setConfig(" + JSONObject.quote(o.toString()) + ")")
    }

    // ------------------------------------------------------------ config

    /** Applies the active profile to every part of the screen. Cheap enough to call on any setting change. */
    private fun applyConfig(first: Boolean) {
        val cfg = ProfileStore.config()
        controls.setConfig(cfg.controls)
        hud.setConfig(cfg.hud)
        placeHud(cfg.hud.corner)
        hud.visibility = if (playing && cfg.hud.enabled) View.VISIBLE else View.GONE
        updateControlsVisibility()
        if (cfg.perf.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateMonitor()
        updateHudTimer()
        if (!first) schedulePush()
    }

    private fun placeHud(corner: Int) {
        val lp = hud.layoutParams as FrameLayout.LayoutParams
        val g = when (corner) {
            1 -> Gravity.TOP or Gravity.END
            2 -> Gravity.BOTTOM or Gravity.START
            3 -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.TOP or Gravity.START
        }
        val m = Ui.dp(this, 10f)
        if (lp.gravity != g || lp.topMargin != m) {
            lp.gravity = g
            lp.setMargins(m, m, m, m)
            hud.layoutParams = lp
        }
    }

    private fun updateControlsVisibility() {
        val show = (playing || ProfileStore.config().controls.always) && sheet.visibility != View.VISIBLE
        controls.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateMonitor() {
        if (resumed && playing && ProfileStore.config().hud.enabled && ProfileStore.config().hud.fps) monitor.start()
        else { monitor.stop(); fps = -1 }
    }

    private fun updateHudTimer() {
        main.removeCallbacks(hudTask)
        if (resumed && playing && ProfileStore.config().hud.enabled) main.post(hudTask)
    }

    private fun refreshHud() {
        val t = if (playing) ((SystemClock.elapsedRealtime() - runStart) / 1000L).toInt() else -1
        val best = if (score > Stats.best) score else Stats.best
        hud.update(score, best, t, Stats.runs, fps)
    }

    // ------------------------------------------------------------ run tracking (main thread)

    private fun startRun() {
        if (playing) return
        playing = true
        score = -1
        runStart = SystemClock.elapsedRealtime()
        acctStart = runStart
        Stats.runs++
        applyConfig(true)
    }

    private fun endRun() {
        if (!playing) return
        playing = false
        Stats.totalMs += SystemClock.elapsedRealtime() - acctStart
        if (score > Stats.best) Stats.best = score
        Stats.save()
        score = -1
        releaseInput()
        applyConfig(true)
    }

    private fun resetRun() {
        engineLoaded = false
        endRun()
    }

    private fun releaseInput() {
        js("window.__davidd&&__davidd.boost(false)")
    }

    // ------------------------------------------------------------ GameBridge.Host (WebView thread)

    override fun onPlaying(p: Boolean) { runOnUiThread { if (p) startRun() else endRun() } }

    override fun onScore(v: Int) {
        runOnUiThread { if (playing) { score = v; if (v > Stats.best) Stats.best = v } }
    }

    override fun onStatus(json: String) { Stats.saveProbe(this, json) }

    // ------------------------------------------------------------ ControlsView.Listener

    override fun onAim(vx: Float, vy: Float) {
        aimVx = vx; aimVy = vy; aimDirty = true
        val gap = 1000L / Tier.inputHz(ProfileStore.config().tier)
        val since = SystemClock.uptimeMillis() - lastAimAt
        if (since >= gap) flushAim()
        else if (!aimScheduled) { aimScheduled = true; main.postDelayed(aimTask, gap - since) }
    }

    private fun flushAim() {
        if (!aimDirty) return
        aimDirty = false
        lastAimAt = SystemClock.uptimeMillis()
        js("__davidd.aim($aimVx,$aimVy)")
    }

    override fun onBoost(on: Boolean) { js(if (on) "__davidd.boost(true)" else "__davidd.boost(false)") }

    override fun onAction(act: Int) {
        val cfg = ProfileStore.config()
        when (act) {
            ControlsCfg.ACT_ZOOM_IN -> js("__davidd.zoom(1)")
            ControlsCfg.ACT_ZOOM_OUT -> js("__davidd.zoom(-1)")
            ControlsCfg.ACT_RING -> { cfg.overlays.ring = !cfg.overlays.ring; ProfileStore.touch() }
            ControlsCfg.ACT_RADAR -> { cfg.overlays.radar = !cfg.overlays.radar; ProfileStore.touch() }
            ControlsCfg.ACT_GUIDE -> { cfg.overlays.guide = !cfg.overlays.guide; ProfileStore.touch() }
            ControlsCfg.ACT_TARGETS -> { cfg.overlays.targets = !cfg.overlays.targets; ProfileStore.touch() }
            ControlsCfg.ACT_LARGEST -> { cfg.overlays.largest = !cfg.overlays.largest; ProfileStore.touch() }
            ControlsCfg.ACT_HUD -> { cfg.hud.enabled = !cfg.hud.enabled; ProfileStore.touch() }
            ControlsCfg.ACT_MENU -> openSheet()
        }
    }

    override fun onEditDone() {}

    // ------------------------------------------------------------ QuickSheet.Host

    private fun openSheet() {
        releaseInput()
        sheet.show()
        updateControlsVisibility()
    }

    override fun onClose() {
        sheet.hide()
        updateControlsVisibility()
    }

    override fun onSettingsChanged() { applyConfig(false) }

    override fun onEditLayout() {
        sheet.hide()
        startActivity(Intent(this, EditorActivity::class.java))
    }

    override fun onExit() { finish() }

    // ------------------------------------------------------------ lifecycle

    @Deprecated("Back opens the quick menu so a stray swipe does not end a match")
    override fun onBackPressed() {
        if (sheet.visibility == View.VISIBLE) onClose() else openSheet()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.enter(window)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        ProfileStore.addListener(settingsListener)
        web?.onResume()
        web?.resumeTimers()
        applyConfig(false)   // the layout editor may have changed things
    }

    override fun onPause() {
        resumed = false
        ProfileStore.removeListener(settingsListener)
        main.removeCallbacks(aimTask)
        main.removeCallbacks(pushTask)
        aimScheduled = false
        releaseInput()
        monitor.stop()
        main.removeCallbacks(hudTask)
        if (playing) {
            // Count the time so far and keep the run going on return.
            val now = SystemClock.elapsedRealtime()
            Stats.totalMs += now - acctStart
            acctStart = now
        }
        Stats.save()
        ProfileStore.flush()
        web?.onPause()
        web?.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        monitor.stop()
        ProfileStore.removeListener(settingsListener)
        val wv = web
        web = null
        if (wv != null) {
            root.removeView(wv)
            wv.stopLoading()
            wv.removeJavascriptInterface("DAVIDDBridge")
            wv.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val GAME_URL = "https://slither.io/"
        private var cachedEngine: String? = null
    }
}
