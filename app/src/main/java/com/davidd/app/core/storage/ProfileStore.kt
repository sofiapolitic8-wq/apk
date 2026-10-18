package com.davidd.app.core.storage

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Profiles and the active one, kept as one small JSON file.
 * Read once, then served from memory. Changes call touch(): saves are debounced and written atomically
 * (temp file, backup, rename). A damaged file falls back to the backup, then to built-in profiles.
 * Call from the main thread only.
 */
object ProfileStore {
    private const val FILE = "profiles.json"
    private const val MAX_PROFILES = 12
    private const val MAX_NAME = 24
    private const val SAVE_DELAY_MS = 500L

    private var dir: File? = null
    private val names = ArrayList<String>()
    private val configs = ArrayList<Config>()
    private var active = 0
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var io: Handler? = null
    private val flushTask = Runnable { writeNow() }

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        if (!load(File(dir, FILE)) && !load(File(dir, "$FILE.bak"))) loadBuiltIns()
    }

    fun config(): Config = configs[active]
    fun activeIndex(): Int = active
    fun activeName(): String = names[active]
    fun count(): Int = names.size
    fun nameAt(i: Int): String = names[i]

    fun select(i: Int) {
        if (i < 0 || i >= names.size || i == active) return
        active = i
        touch()
    }

    /** Copies the active profile under a new name. Returns the new index, or -1 if full. */
    fun saveAsNew(rawName: String): Int {
        if (names.size >= MAX_PROFILES) return -1
        val name = uniqueName(cleanName(rawName))
        names.add(name)
        configs.add(configs[active].copy())
        active = names.size - 1
        touch()
        return active
    }

    fun rename(i: Int, rawName: String) {
        if (i < 0 || i >= names.size) return
        val n = cleanName(rawName)
        if (n == names[i]) return
        names[i] = uniqueName(n)
        touch()
    }

    /** Deletes a profile. The last one cannot be deleted. */
    fun delete(i: Int): Boolean {
        if (names.size <= 1 || i < 0 || i >= names.size) return false
        names.removeAt(i)
        configs.removeAt(i)
        if (active >= names.size) active = names.size - 1
        else if (i < active) active--
        touch()
        return true
    }

    /** Puts the active profile back to its preset (Default for custom profiles). */
    fun resetActive() {
        val preset = if (Presets.names.contains(names[active])) names[active] else Presets.DEFAULT
        configs[active] = Presets.build(preset)
        touch()
    }

    /** Call after changing anything inside config(). */
    fun touch() {
        for (l in ArrayList(listeners)) l()
        main.removeCallbacks(flushTask)
        main.postDelayed(flushTask, SAVE_DELAY_MS)
    }

    fun addListener(l: () -> Unit) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Write right now (screen leaving, app pausing). */
    fun flush() {
        main.removeCallbacks(flushTask)
        writeNow()
    }

    private fun cleanName(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        val n = if (t.length > MAX_NAME) t.substring(0, MAX_NAME) else t
        return if (n.isEmpty()) "Custom" else n
    }

    private fun uniqueName(base: String): String = uniqueIn(base, names)

    private fun uniqueIn(base: String, existing: List<String>): String {
        if (!existing.contains(base)) return base
        var i = 2
        while (existing.contains("$base $i")) i++
        return "$base $i"
    }

    private fun loadBuiltIns() {
        names.clear(); configs.clear()
        for (n in Presets.names) { names.add(n); configs.add(Presets.build(n)) }
        active = 0
    }

    private fun load(f: File): Boolean {
        return try {
            if (!f.isFile || f.length() == 0L || f.length() > 512 * 1024) return false
            val root = JSONObject(f.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("profiles") ?: return false
            val n = ArrayList<String>()
            val c = ArrayList<Config>()
            for (i in 0 until minOf(arr.length(), MAX_PROFILES)) {
                val p = arr.optJSONObject(i) ?: continue
                n.add(uniqueIn(cleanName(p.optString("name", "")), n))
                c.add(Config.fromJson(p.optJSONObject("cfg")))
            }
            if (n.isEmpty()) return false
            names.clear(); names.addAll(n)
            configs.clear(); configs.addAll(c)
            active = clampI(root.optInt("active", 0), 0, names.size - 1)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeNow() {
        val d = dir ?: return
        val json: String = try {
            val arr = JSONArray()
            for (i in 0 until names.size) arr.put(JSONObject().put("name", names[i]).put("cfg", configs[i].toJson()))
            JSONObject().put("v", 1).put("active", active).put("profiles", arr).toString()
        } catch (e: Exception) {
            return
        }
        var h = io
        if (h == null) {
            val t = HandlerThread("davidd-io", android.os.Process.THREAD_PRIORITY_BACKGROUND)
            t.start()
            h = Handler(t.looper)
            io = h
        }
        h.post { writeFile(d, json) }
    }

    private fun writeFile(d: File, json: String) {
        try {
            val f = File(d, FILE)
            val tmp = File(d, "$FILE.tmp")
            val bak = File(d, "$FILE.bak")
            tmp.writeText(json, Charsets.UTF_8)
            if (f.isFile) f.copyTo(bak, overwrite = true)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        } catch (e: Exception) {
            // Out of space / read-only disk: keep playing, retry on the next change.
        }
    }
}
