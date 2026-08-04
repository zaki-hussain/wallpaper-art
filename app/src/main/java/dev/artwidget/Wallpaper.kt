package dev.artwidget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import java.util.concurrent.Executors

/**
 * Renders the current art and applies it as the system wallpaper. The home screen can
 * optionally fade to a solid colour at the bottom (keeps dock icons legible); the lock
 * screen always gets the full art.
 */
object Wallpaper {

    private val executor = Executors.newSingleThreadExecutor()

    /** Re-applies the current art as the wallpaper (when enabled), off the caller's thread. */
    fun applyAsync(context: Context) {
        val app = context.applicationContext
        executor.execute { applyIfEnabled(app) }
    }

    /** Stores the next design (honouring locks) and applies it, off the caller's thread. */
    fun newArtAsync(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            try {
                ArtStore(app).refreshArt(app.isSystemDark())
                applyIfEnabled(app)
            } finally {
                onDone()
            }
        }
    }

    fun applyIfEnabled(context: Context) {
        val store = ArtStore(context)
        if (!store.wallpaperEnabled) return
        apply(context, store.currentOrCreate(), if (store.fadeEnabled) store.fadePct else 0)
    }

    /**
     * Renders [spec] at portrait screen resolution and sets it as the wallpaper.
     * Blocking — call off the main thread. Returns false on failure.
     */
    fun apply(context: Context, spec: ArtSpec, fadePct: Int = 0): Boolean = try {
        val dm = context.resources.displayMetrics
        val w = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val h = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val art = ArtRenderer.render(spec, w, h)
        val wm = WallpaperManager.getInstance(context)
        if (fadePct > 0) {
            wm.setBitmap(art, null, true, WallpaperManager.FLAG_LOCK)
            val home = fade(art.copy(Bitmap.Config.ARGB_8888, true), spec.colors[0], fadePct)
            wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM)
        } else {
            wm.setBitmap(art)
        }
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Fades [art] into solid [color] over its bottom [fadePct] percent: fully transparent
     * at the start, fully solid at the bottom edge, eased with smootherstep so there is
     * no visible start or end line at any slider value. Draws into [art].
     */
    fun fade(art: Bitmap, color: Int, fadePct: Int): Bitmap {
        if (fadePct <= 0) return art
        val w = art.width.toFloat()
        val h = art.height.toFloat()
        val splitY = h * (100 - fadePct.coerceAtMost(90)) / 100f
        val n = 17
        val cols = IntArray(n) { i ->
            val t = i / (n - 1f)
            val s = t * t * t * (t * (t * 6 - 15) + 10)
            ((s * 255).toInt() shl 24) or (color and 0x00FFFFFF)
        }
        // The ramp finishes at 95% of the band; CLAMP keeps the last stretch fully
        // solid so the dock area never ghosts.
        val pos = FloatArray(n) { 0.95f * it / (n - 1f) }
        val paint = Paint().apply {
            shader = LinearGradient(0f, splitY, 0f, h, cols, pos, Shader.TileMode.CLAMP)
        }
        Canvas(art).drawRect(0f, splitY, w, h, paint)
        return art
    }
}
