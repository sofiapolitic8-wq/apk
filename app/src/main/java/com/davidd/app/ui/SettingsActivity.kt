package com.davidd.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.davidd.app.core.perf.Tier
import com.davidd.app.core.storage.ControlsCfg
import com.davidd.app.core.storage.ProfileStore
import com.davidd.app.core.storage.Stats
import com.davidd.app.features.controls.Act

/**
 * Every setting, grouped. Edits go straight into the active profile (in memory) and are saved debounced.
 * The page is rebuilt only when the profile changes, never per slider tick.
 */
class SettingsActivity : Activity() {
    private lateinit var scroll: ScrollView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ProfileStore.init(this)
        Stats.init(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.BG)
        scroll.isVerticalScrollBarEnabled = false
        setContentView(scroll)
        build()
    }

    override fun onPause() {
        ProfileStore.flush()
        Stats.save()
        super.onPause()
    }

    private fun pct(v: Float) = "${(v * 100f + 0.5f).toInt()}%"
    private fun x2(v: Float) = String.format(java.util.Locale.US, "%.2fx", v)

    private fun build() {
        val c = this
        val cfg = ProfileStore.config()
        val cc = cfg.controls
        val col = Ui.vertical(c)
        col.setPadding(Ui.dp(c, 16f), Ui.dp(c, 20f), Ui.dp(c, 16f), Ui.dp(c, 28f))

        val top = Ui.horizontal(c)
        top.addView(Ui.label(c, "Settings", 22f, Ui.TEXT, true), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Ui.button(c, "Done", false) { finish() })
        col.addView(top)

        // ---- profiles
        col.addView(Ui.header(c, "Profiles"))
        val chips = Ui.horizontal(c)
        for (i in 0 until ProfileStore.count()) {
            val chip = Ui.chip(c, ProfileStore.nameAt(i), i == ProfileStore.activeIndex()) { ProfileStore.select(i); build() }
            val p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = Ui.dp(c, 8f)
            chips.addView(chip, p)
        }
        val hs = HorizontalScrollView(c)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        col.addView(hs)
        col.addView(Ui.gap(c, 10f))
        val pr1 = Ui.horizontal(c)
        pr1.addView(Ui.button(c, "Save as new", false) {
            ask("Save as new profile", "") { n -> if (ProfileStore.saveAsNew(n) < 0) toast("Profile limit reached"); build() }
        }, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.rightMargin = Ui.dp(c, 8f) })
        pr1.addView(Ui.button(c, "Rename", false) {
            ask("Rename profile", ProfileStore.activeName()) { n -> ProfileStore.rename(ProfileStore.activeIndex(), n); build() }
        }, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(pr1)
        col.addView(Ui.gap(c, 8f))
        val pr2 = Ui.horizontal(c)
        pr2.addView(Ui.button(c, "Reset", false) {
            confirm("Reset \"${ProfileStore.activeName()}\" to its defaults?") { ProfileStore.resetActive(); build() }
        }, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.rightMargin = Ui.dp(c, 8f) })
        pr2.addView(Ui.button(c, "Delete", false) {
            if (ProfileStore.count() <= 1) toast("Keep at least one profile")
            else confirm("Delete \"${ProfileStore.activeName()}\"?") { ProfileStore.delete(ProfileStore.activeIndex()); build() }
        }, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(pr2)

        // ---- performance
        col.addView(Ui.header(c, "Performance"))
        col.addView(Ui.segmented(c, listOf("Performance", "Balanced", "Quality"), cfg.tier) { cfg.tier = it; ProfileStore.touch() })
        val card0 = Ui.card(c)
        card0.addView(Ui.toggleRow(c, "Keep screen on", "While a match is open", cfg.perf.keepOn) { cfg.perf.keepOn = it; ProfileStore.touch() })
        card0.addView(Ui.divider(c))
        card0.addView(Ui.toggleRow(c, "Block ad and tracking hosts", "Skips known ad servers inside the game page", cfg.perf.blockAds) { cfg.perf.blockAds = it; ProfileStore.touch() })
        col.addView(Ui.gap(c, 10f))
        col.addView(card0)

        // ---- controls
        col.addView(Ui.header(c, "Controls"))
        col.addView(Ui.segmented(c, listOf("Floating stick", "Fixed stick", "Touch pad"), cc.mode) { cc.mode = it; ProfileStore.touch() })
        val card1 = Ui.card(c)
        card1.addView(Ui.sliderRow(c, "Sensitivity", 0.5f, 2f, 0.05f, cc.sensitivity, this::x2) { cc.sensitivity = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.sliderRow(c, "Dead zone", 0f, 0.4f, 0.02f, cc.deadzone, this::pct) { cc.deadzone = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.sliderRow(c, "Stick size", 0.6f, 1.6f, 0.05f, cc.stickScale, this::x2) { cc.stickScale = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.sliderRow(c, "Button size", 0.6f, 1.6f, 0.05f, cc.buttonScale, this::x2) { cc.buttonScale = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.sliderRow(c, "Opacity", 0.2f, 1f, 0.05f, cc.opacity, this::pct) { cc.opacity = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.toggleRow(c, "Left-handed layout", "Mirrors the default positions", cc.leftHanded) { cc.leftHanded = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.toggleRow(c, "Boost lock", "Tap to start boosting, tap again to stop", cc.boostLock) { cc.boostLock = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.toggleRow(c, "Haptics", null, cc.haptics) { cc.haptics = it; ProfileStore.touch() })
        card1.addView(Ui.divider(c))
        card1.addView(Ui.toggleRow(c, "Controls always on", "Show them even when a match is not detected", cc.always) { cc.always = it; ProfileStore.touch() })
        col.addView(Ui.gap(c, 10f))
        col.addView(card1)

        col.addView(Ui.header(c, "Action buttons (tap to change)"))
        val card2 = Ui.card(c)
        for (i in 0 until ControlsCfg.SLOTS) {
            val v = Ui.label(c, Act.name(cc.actions[i]), 14f, Ui.ACCENT, true)
            val row = Ui.row(c, "Button ${i + 1}", null, v)
            row.setOnClickListener {
                cc.actions[i] = Act.next(cc.actions[i])
                v.text = Act.name(cc.actions[i])
                ProfileStore.touch()
            }
            if (i > 0) card2.addView(Ui.divider(c))
            card2.addView(row)
        }
        col.addView(card2)
        col.addView(Ui.gap(c, 10f))
        col.addView(Ui.button(c, "Edit button layout", false) { startActivity(Intent(c, EditorActivity::class.java)) },
            Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- hud
        val h = cfg.hud
        col.addView(Ui.header(c, "HUD"))
        val card3 = Ui.card(c)
        card3.addView(Ui.toggleRow(c, "Show HUD", null, h.enabled) { h.enabled = it; ProfileStore.touch() })
        card3.addView(Ui.divider(c))
        card3.addView(Ui.toggleRow(c, "Score", null, h.score) { h.score = it; ProfileStore.touch() })
        card3.addView(Ui.divider(c))
        card3.addView(Ui.toggleRow(c, "Best score", null, h.best) { h.best = it; ProfileStore.touch() })
        card3.addView(Ui.divider(c))
        card3.addView(Ui.toggleRow(c, "Time in match", null, h.time) { h.time = it; ProfileStore.touch() })
        card3.addView(Ui.divider(c))
        card3.addView(Ui.toggleRow(c, "Runs", null, h.runs) { h.runs = it; ProfileStore.touch() })
        card3.addView(Ui.divider(c))
        card3.addView(Ui.toggleRow(c, "FPS", "Runs a frame counter while on", h.fps) { h.fps = it; ProfileStore.touch() })
        card3.addView(Ui.divider(c))
        card3.addView(Ui.sliderRow(c, "HUD size", 0.7f, 1.6f, 0.05f, h.scale, this::x2) { h.scale = it; ProfileStore.touch() })
        col.addView(card3)
        col.addView(Ui.gap(c, 10f))
        col.addView(Ui.segmented(c, listOf("Top left", "Top right", "Bottom left", "Bottom right"), h.corner) { h.corner = it; ProfileStore.touch() })

        // ---- overlays
        val o = cfg.overlays
        col.addView(Ui.header(c, "Overlays"))
        val card4 = Ui.card(c)
        card4.addView(Ui.toggleRow(c, "Danger ring", "Circles snakes inside your reach", o.ring) { o.ring = it; ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.sliderRow(c, "Ring reach", 2f, 30f, 1f, o.ringRange, { "${it.toInt()} widths" }) { o.ringRange = it; ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.toggleRow(c, "Heading guide", "A line along where you are going", o.guide) { o.guide = it; ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.toggleRow(c, "Target arrows", "Nearest snakes; arrows at the screen edge", o.targets) { o.targets = it; ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.sliderRow(c, "Targets shown", 1f, 6f, 1f, o.targetCount.toFloat(), { it.toInt().toString() }) { o.targetCount = it.toInt(); ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.toggleRow(c, "Biggest snake", "Marks the longest snake", o.largest) { o.largest = it; ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.toggleRow(c, "Food radar", "Arcs around you where food is thickest", o.radar) { o.radar = it; ProfileStore.touch() })
        card4.addView(Ui.divider(c))
        card4.addView(Ui.sliderRow(c, "Radar range", 200f, 3000f, 100f, o.radarRange, { "${it.toInt()}" }) { o.radarRange = it; ProfileStore.touch() })
        col.addView(card4)

        // ---- data
        col.addView(Ui.header(c, "Data"))
        col.addView(Ui.button(c, "Reset best score, runs and time", false) {
            confirm("Reset your numbers?") { Stats.reset(); toast("Done") }
        }, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(Ui.gap(c, 8f))
        val about = Ui.label(c, "Made by DAVIDD. Everything is stored on this phone. No accounts, no analytics, no ads.", 12f, Ui.DIM)
        col.addView(about)

        val keep = scroll.scrollY
        scroll.removeAllViews()
        scroll.addView(col)
        scroll.post { scroll.scrollTo(0, keep) }
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    private fun ask(title: String, initial: String, onOk: (String) -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT
        et.setText(initial)
        et.setSelection(et.text.length)
        et.filters = arrayOf(android.text.InputFilter.LengthFilter(24))
        val box = LinearLayout(this)
        box.setPadding(Ui.dp(this, 20f), Ui.dp(this, 8f), Ui.dp(this, 20f), 0)
        box.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).show()
    }
}
