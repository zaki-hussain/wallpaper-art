package dev.artwidget

import android.app.WallpaperManager
import android.content.Context
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec

/** Applies the current art as the system wallpaper when the user has turned that on. */
object Wallpaper {

    fun applyIfEnabled(context: Context) {
        val store = ArtStore(context)
        if (store.wallpaperEnabled) apply(context, store.currentOrCreate())
    }

    /**
     * Renders [spec] at portrait screen resolution and sets it as the wallpaper (home
     * and lock). Blocking — call off the main thread. Returns false on failure.
     */
    fun apply(context: Context, spec: ArtSpec): Boolean = try {
        val dm = context.resources.displayMetrics
        val w = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val h = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        WallpaperManager.getInstance(context).setBitmap(ArtRenderer.render(spec, w, h))
        true
    } catch (e: Exception) {
        false
    }
}
