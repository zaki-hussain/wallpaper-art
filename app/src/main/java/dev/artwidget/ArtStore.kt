package dev.artwidget

import android.content.Context
import android.content.res.Configuration
import dev.artwidget.art.ArtSpec
import dev.artwidget.art.Generator
import dev.artwidget.art.Pattern
import dev.artwidget.art.Patterns
import org.json.JSONArray

/** SharedPreferences-backed state: current art, saved arts, and the pattern filter. */
class ArtStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("art", Context.MODE_PRIVATE)

    var current: ArtSpec?
        get() = prefs.getString(KEY_CURRENT, null)?.let { ArtSpec.fromJson(it) }
        set(value) = prefs.edit().putString(KEY_CURRENT, value?.toJson()).apply()

    fun currentOrCreate(): ArtSpec =
        current ?: Generator.newArt(enabledPatterns()).also { current = it }

    /** Auto-refresh interval in milliseconds; 0 = off. */
    var refreshIntervalMillis: Long
        get() = prefs.getLong(KEY_INTERVAL, 0L)
        set(value) = prefs.edit().putLong(KEY_INTERVAL, value).apply()

    /** When true, every art change is also applied as the system wallpaper. */
    var wallpaperEnabled: Boolean
        get() = prefs.getBoolean(KEY_WALLPAPER, false)
        set(value) = prefs.edit().putBoolean(KEY_WALLPAPER, value).apply()

    fun saved(): List<ArtSpec> {
        val raw = prefs.getString(KEY_SAVED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { ArtSpec.fromJson(arr.getString(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns false when an identical art is already saved. */
    fun addSaved(spec: ArtSpec): Boolean {
        val list = saved()
        if (list.any { it == spec }) return false
        writeSaved(listOf(spec) + list)
        return true
    }

    fun removeSaved(spec: ArtSpec) = writeSaved(saved().filter { it != spec })

    private fun writeSaved(list: List<ArtSpec>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SAVED, arr.toString()).apply()
    }

    /**
     * The pattern filter is stored as the set of *disabled* ids, so patterns added in
     * app updates are enabled by default.
     */
    fun disabledIds(): Set<String> = prefs.getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()

    fun setDisabledIds(ids: Set<String>) = prefs.edit().putStringSet(KEY_DISABLED, ids).apply()

    fun enabledPatterns(): List<Pattern> {
        val disabled = disabledIds()
        return Patterns.all.filter { it.id !in disabled }.ifEmpty { Patterns.all }
    }

    private companion object {
        const val KEY_CURRENT = "current"
        const val KEY_SAVED = "saved"
        const val KEY_DISABLED = "disabled"
        const val KEY_INTERVAL = "interval"
        const val KEY_WALLPAPER = "wallpaper"
    }
}

/** Whether the system is in dark mode right now; newly generated palettes match it. */
fun Context.isSystemDark(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
