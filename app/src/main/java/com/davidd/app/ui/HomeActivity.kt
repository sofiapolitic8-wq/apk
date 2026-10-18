package com.davidd.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.davidd.app.core.perf.Tier
import com.davidd.app.core.storage.ProfileStore
import com.davidd.app.core.storage.Stats
import com.davidd.app.game.GameActivity
import org.json.JSONObject

/** Start screen: play, pick a profile and tier, see your numbers. Rebuilt on resume, nothing runs in the background. */
class HomeActivity : Activity() {
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
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onPause() {
        ProfileStore.flush()
        super.onPause()
    }

    private fun build() {
        val c = this
        val cfg = ProfileStore.config()
        val col = Ui.vertical(c)
        col.setPadding(Ui.dp(c, 20f), Ui.dp(c, 36f), Ui.dp(c, 20f), Ui.dp(c, 28f))

        val mark = MarkView(c)
        val ms = Ui.dp(c, 84f)
        col.addView(mark, Ui.lp(ms, ms).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(Ui.gap(c, 12f))
        val title = Ui.label(c, "DAVIDD", 30f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        col.addView(title, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(Ui.gap(c, 4f))
        val sub = Ui.label(c, "Touch controls and overlays for Slither", 13f, Ui.DIM)
        sub.gravity = Gravity.CENTER
        col.addView(sub, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(Ui.gap(c, 26f))

        col.addView(Ui.button(c, "Play", true) { startActivity(Intent(c, GameActivity::class.java)) },
            Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(Ui.header(c, "Profile"))
        val chips = Ui.horizontal(c)
        for (i in 0 until ProfileStore.count()) {
            val chip = Ui.chip(c, ProfileStore.nameAt(i), i == ProfileStore.activeIndex()) {
                ProfileStore.select(i)
                build()
            }
            val p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = Ui.dp(c, 8f)
            chips.addView(chip, p)
        }
        val hs = HorizontalScrollView(c)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        col.addView(hs)

        col.addView(Ui.header(c, "Performance"))
        col.addView(Ui.segmented(c, listOf("Performance", "Balanced", "Quality"), cfg.tier) { cfg.tier = it; ProfileStore.touch(); build() })
        val blurb = Ui.label(c, Tier.blurb(cfg.tier), 12f, Ui.DIM)
        blurb.setPadding(Ui.dp(c, 4f), Ui.dp(c, 8f), 0, 0)
        col.addView(blurb)

        col.addView(Ui.header(c, "Your numbers"))
        val card = Ui.card(c)
        card.addView(Ui.row(c, "Best score", null, Ui.label(c, Stats.best.toString(), 15f, Ui.ACCENT, true)))
        card.addView(Ui.divider(c))
        card.addView(Ui.row(c, "Runs", null, Ui.label(c, Stats.runs.toString(), 15f, Ui.ACCENT, true)))
        card.addView(Ui.divider(c))
        card.addView(Ui.row(c, "Time played", null, Ui.label(c, playTime(Stats.totalMs), 15f, Ui.ACCENT, true)))
        col.addView(card)

        col.addView(Ui.header(c, "Overlay data"))
        val st = Ui.card(c)
        st.addView(Ui.row(c, "Game link", "What the last match exposed to the overlays", Ui.label(c, overlayState(), 13f, overlayColor(), true)))
        col.addView(st)

        col.addView(Ui.gap(c, 18f))
        col.addView(Ui.button(c, "Settings", false) { startActivity(Intent(c, SettingsActivity::class.java)) },
            Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.addView(Ui.gap(c, 22f))
        val credit = Ui.label(c, "Made by DAVIDD", 12f, Ui.DIM)
        credit.gravity = Gravity.CENTER
        col.addView(credit, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        scroll.removeAllViews()
        scroll.addView(col)
    }

    private fun probe(): JSONObject? = try {
        Stats.loadProbe(this)?.let { JSONObject(it) }
    } catch (e: Exception) { null }

    private fun overlayState(): String = when (probe()?.optString("overlay")) {
        "ready" -> "Ready"
        "no-data" -> "No data"
        "error" -> "Stopped"
        else -> "Not seen yet"
    }

    private fun overlayColor(): Int = when (probe()?.optString("overlay")) {
        "ready" -> Ui.ACCENT
        "error" -> Ui.DANGER
        "no-data" -> Ui.AMBER
        else -> Ui.DIM
    }

    private fun playTime(ms: Long): String {
        val m = ms / 60000L
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
