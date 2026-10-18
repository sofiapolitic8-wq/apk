package com.davidd.app.game

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.davidd.app.core.storage.ProfileStore
import com.davidd.app.ui.Ui

/**
 * In-game side panel. It exists only while open: views are built when shown and dropped when hidden,
 * so a closed panel costs no memory and no layout passes.
 */
class QuickSheet(ctx: Context, private val host: Host) : FrameLayout(ctx) {

    interface Host {
        fun onSettingsChanged()
        fun onEditLayout()
        fun onExit()
        fun onClose()
    }

    init {
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
        setOnClickListener { host.onClose() }
    }

    fun show() {
        rebuild()
        visibility = View.VISIBLE
    }

    fun hide() {
        visibility = View.GONE
        removeAllViews()
    }

    private fun changed() {
        ProfileStore.touch()
        host.onSettingsChanged()
    }

    private fun rebuild() {
        removeAllViews()
        val c = context
        val cfg = ProfileStore.config()

        val panel = Ui.vertical(c)
        panel.setBackgroundColor(Ui.BG)
        panel.setPadding(Ui.dp(c, 16f), Ui.dp(c, 12f), Ui.dp(c, 16f), Ui.dp(c, 12f))
        panel.isClickable = true

        val top = Ui.horizontal(c)
        top.addView(Ui.label(c, "Quick settings", 17f, Ui.TEXT, true), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Ui.button(c, "Close", false) { host.onClose() })
        panel.addView(top)

        panel.addView(Ui.header(c, "Profile"))
        val chips = Ui.horizontal(c)
        for (i in 0 until ProfileStore.count()) {
            val chip = Ui.chip(c, ProfileStore.nameAt(i), i == ProfileStore.activeIndex()) {
                ProfileStore.select(i)
                host.onSettingsChanged()
                rebuild()
            }
            val p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.rightMargin = Ui.dp(c, 8f)
            chips.addView(chip, p)
        }
        val chipScroll = HorizontalScrollView(c)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chips)
        panel.addView(chipScroll)

        panel.addView(Ui.header(c, "Performance"))
        panel.addView(Ui.segmented(c, listOf("Performance", "Balanced", "Quality"), cfg.tier) { cfg.tier = it; changed() })

        panel.addView(Ui.header(c, "In-game overlays"))
        val card = Ui.card(c)
        card.addView(Ui.toggleRow(c, "Danger ring", null, cfg.overlays.ring) { cfg.overlays.ring = it; changed() })
        card.addView(Ui.divider(c))
        card.addView(Ui.toggleRow(c, "Heading guide", null, cfg.overlays.guide) { cfg.overlays.guide = it; changed() })
        card.addView(Ui.divider(c))
        card.addView(Ui.toggleRow(c, "Target arrows", null, cfg.overlays.targets) { cfg.overlays.targets = it; changed() })
        card.addView(Ui.divider(c))
        card.addView(Ui.toggleRow(c, "Biggest snake", null, cfg.overlays.largest) { cfg.overlays.largest = it; changed() })
        card.addView(Ui.divider(c))
        card.addView(Ui.toggleRow(c, "Food radar", null, cfg.overlays.radar) { cfg.overlays.radar = it; changed() })
        panel.addView(card)

        panel.addView(Ui.header(c, "Screen"))
        val card2 = Ui.card(c)
        card2.addView(Ui.toggleRow(c, "Show HUD", null, cfg.hud.enabled) { cfg.hud.enabled = it; changed() })
        card2.addView(Ui.divider(c))
        card2.addView(Ui.toggleRow(c, "Controls always on", "Use if the touch controls do not appear in a match", cfg.controls.always) { cfg.controls.always = it; changed() })
        panel.addView(card2)

        panel.addView(Ui.gap(c, 14f))
        panel.addView(Ui.button(c, "Edit button layout", false) { host.onEditLayout() })
        panel.addView(Ui.gap(c, 8f))
        panel.addView(Ui.button(c, "Leave match", false) { host.onExit() })

        val scroll = ScrollView(c)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(panel)
        addView(scroll, LayoutParams(Ui.dp(c, 340f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
    }
}
