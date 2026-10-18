package com.davidd.app.game

/** Hosts that only serve advertising or tracking. Requests to them are answered with an empty body. */
object Blocklist {
    private val suffixes = arrayOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adnxs.com", "adsystem.com",
        "amazon-adsystem.com", "pubmatic.com", "rubiconproject.com", "criteo.com", "criteo.net", "taboola.com",
        "outbrain.com", "casalemedia.com", "openx.net", "smartadserver.com", "adform.net", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "scorecardresearch.com", "hotjar.com", "facebook.net"
    )

    fun matches(host: String?): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        for (s in suffixes) if (h == s || h.endsWith(".$s")) return true
        return false
    }
}
