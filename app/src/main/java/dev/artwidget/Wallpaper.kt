package dev.artwidget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec

/** Applies the current art as the system wallpaper when the user has turned that on. */
object Wallpaper {

    const val STYLE_LINE = "line"
    const val STYLE_ARC = "arc"
    const val STYLE_FADE = "fade"
    val STYLES = listOf(STYLE_LINE, STYLE_ARC, STYLE_FADE)

    fun applyIfEnabled(context: Context) {
        val store = ArtStore(context)
        if (!store.wallpaperEnabled) return
        apply(context, store.currentOrCreate(), store.wallpaperSolidPct, store.divideStyle)
    }

    /**
     * Renders [spec] at portrait screen resolution and sets it as the wallpaper (home
     * and lock). Blocking — call off the main thread. Returns false on failure.
     */
    fun apply(context: Context, spec: ArtSpec, solidPct: Int = 0, style: String = STYLE_LINE): Boolean = try {
        val dm = context.resources.displayMetrics
        val w = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val h = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val art = compose(ArtRenderer.render(spec, w, h), spec.colors[0], solidPct, style)
        WallpaperManager.getInstance(context).setBitmap(art)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Paints the bottom [solidPct] percent of [art] in [color] — a calm area that keeps
     * home-screen icons legible — joined by the chosen divide style. Draws into [art].
     */
    fun compose(art: Bitmap, color: Int, solidPct: Int, style: String): Bitmap {
        if (solidPct <= 0) return art
        val w = art.width.toFloat()
        val h = art.height.toFloat()
        val splitY = h * (100 - solidPct.coerceAtMost(90)) / 100f
        val canvas = Canvas(art)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (style) {
            STYLE_ARC -> {
                val rise = h * 0.04f
                val path = Path().apply {
                    moveTo(0f, splitY)
                    quadTo(w / 2f, splitY - 2 * rise, w, splitY)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            STYLE_FADE -> {
                val band = minOf(h * 0.12f, splitY)
                val fade = Paint().apply {
                    shader = LinearGradient(
                        0f, splitY - band, 0f, splitY,
                        color and 0x00FFFFFF, color, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, splitY - band, w, splitY, fade)
                canvas.drawRect(0f, splitY, w, h, paint)
            }
            else -> canvas.drawRect(0f, splitY, w, h, paint)
        }
        return art
    }
}
