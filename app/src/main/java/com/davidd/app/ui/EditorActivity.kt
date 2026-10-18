package com.davidd.app.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.davidd.app.core.storage.ProfileStore
import com.davidd.app.features.controls.ControlsView

/** Drag the buttons where your thumbs want them. Same full-screen landscape surface as the game. */
class EditorActivity : Activity(), ControlsView.Listener {
    private lateinit var view: ControlsView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ProfileStore.init(this)
        Immersive.enter(window)
        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        val hint = Ui.label(this, "Drag buttons to move them", 13f, Ui.DIM)
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view = ControlsView(this)
        view.listener = this
        view.onLayoutChanged = { ProfileStore.touch() }
        view.setConfig(ProfileStore.config().controls)
        view.setEditing(true)
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.enter(window)
    }

    override fun onPause() {
        ProfileStore.flush()
        super.onPause()
    }

    override fun onEditDone() { finish() }
    override fun onAim(vx: Float, vy: Float) {}
    override fun onBoost(on: Boolean) {}
    override fun onAction(act: Int) {}
}
