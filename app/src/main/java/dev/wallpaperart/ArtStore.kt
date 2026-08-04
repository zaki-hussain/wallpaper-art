package dev.wallpaperart

import android.content.Context
import android.content.res.Configuration
import dev.wallpaperart.art.ArtSpec
import dev.wallpaperart.art.Generator
import dev.wallpaperart.art.Pattern
import dev.wallpaperart.art.Patterns
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

    /** Blur the bottom of the home wallpaper (lock screen stays full art). */
    var blurEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLUR_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_BLUR_ON, value).apply()

    /** Percent of screen height, from the bottom, that gets blurred. */
    var blurPct: Int
        get() = prefs.getInt(KEY_BLUR_PCT, 20)
        set(value) = prefs.edit().putInt(KEY_BLUR_PCT, value.coerceIn(0, 60)).apply()

    /** When true, refreshes keep the current pattern. */
    var patternLocked: Boolean
        get() = prefs.getBoolean(KEY_LOCK_PATTERN, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_PATTERN, value).apply()

    /** When true, refreshes keep the current colours. */
    var colorsLocked: Boolean
        get() = prefs.getBoolean(KEY_LOCK_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_COLORS, value).apply()

    /**
     * The refresh step behind New and the auto-refresh alarm: a new design except the
     * locked aspects.
     */
    fun refreshArt(dark: Boolean?): ArtSpec {
        val next = Generator.nextArt(currentOrCreate(), enabledPatterns(), dark, patternLocked, colorsLocked)
        current = next
        return next
    }

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
        const val KEY_BLUR_ON = "blurOn"
        const val KEY_BLUR_PCT = "blurPct"
        const val KEY_LOCK_PATTERN = "lockPattern"
        const val KEY_LOCK_COLORS = "lockColors"
    }
}

/** Whether the system is in dark mode right now; newly generated palettes match it. */
fun Context.isSystemDark(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
