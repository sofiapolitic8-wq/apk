package com.davidd.app.game

import android.webkit.JavascriptInterface

/** The only door from the page into the app: three tiny calls, arriving on a WebView thread. */
class GameBridge(private val host: Host) {
    interface Host {
        fun onPlaying(p: Boolean)
        fun onScore(v: Int)
        fun onStatus(json: String)
    }

    @JavascriptInterface
    fun playing(p: Boolean) { host.onPlaying(p) }

    @JavascriptInterface
    fun score(v: Int) { host.onScore(v) }

    @JavascriptInterface
    fun status(json: String) {
        if (json.length <= 2048) host.onStatus(json)
    }
}
