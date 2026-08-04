package dev.artwidget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import java.util.concurrent.Executors

/**
 * Renders the current art and applies it as the system wallpaper. The home screen can
 * optionally blur its bottom (keeps dock icons legible); the lock screen always gets
 * the full art.
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
        apply(context, store.currentOrCreate(), if (store.blurEnabled) store.blurPct else 0)
    }

    /**
     * Renders [spec] at portrait screen resolution and sets it as the wallpaper.
     * Blocking — call off the main thread. Returns false on failure.
     */
    fun apply(context: Context, spec: ArtSpec, blurPct: Int = 0): Boolean = try {
        val dm = context.resources.displayMetrics
        val w = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val h = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(320)
        val art = ArtRenderer.render(spec, w, h)
        val wm = WallpaperManager.getInstance(context)
        if (blurPct > 0) {
            wm.setBitmap(art, null, true, WallpaperManager.FLAG_LOCK)
            val home = blurBottom(art.copy(Bitmap.Config.ARGB_8888, true), spec.colors[0], blurPct)
            wm.setBitmap(home, null, true, WallpaperManager.FLAG_SYSTEM)
        } else {
            wm.setBitmap(art)
        }
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Frosts the bottom [blurPct] percent of [art]: the art continues underneath but
     * properly blurred, so dock icons sit on calm texture. The blur eases in over a
     * short ramp below the start line (no hard edge), and a faint wash of [tint]
     * lifts icon contrast. Draws into [art].
     */
    fun blurBottom(art: Bitmap, tint: Int, blurPct: Int): Bitmap {
        if (blurPct <= 0) return art
        val w = art.width.toFloat()
        val h = art.height.toFloat()
        val splitY = h * (100 - blurPct.coerceAtMost(90)) / 100f
        val band = minOf(h * 0.06f, (h - splitY) * 0.4f).coerceAtLeast(1f)
        val soft = blurred(art)
        val canvas = Canvas(art)

        // Blurred layer masked by a vertical alpha ramp: invisible at the start line,
        // fully frosted a short way below it.
        val mask = LinearGradient(
            0f, splitY, 0f, splitY + band,
            0x00FFFFFF, 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP
        )
        val frost = Paint().apply {
            shader = ComposeShader(
                BitmapShader(soft, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
                mask, PorterDuff.Mode.DST_IN
            )
        }
        canvas.drawRect(0f, splitY, w, h, frost)

        // A whisper of the palette background over the frost, same ramp.
        val wash = Paint().apply {
            shader = LinearGradient(
                0f, splitY, 0f, splitY + band,
                tint and 0x00FFFFFF, (0x30 shl 24) or (tint and 0x00FFFFFF),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, splitY, w, h, wash)
        return art
    }

    /**
     * Strong, smooth full-image blur: three separable box-blur passes (≈ gaussian) on
     * a quarter-scale copy, then a filtered upscale. Runs once per wallpaper set.
     */
    private fun blurred(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val sw = (w / 4).coerceAtLeast(1)
        val sh = (h / 4).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        val tmp = IntArray(sw * sh)
        val radius = (minOf(sw, sh) / 22).coerceAtLeast(3)
        repeat(3) {
            boxBlurH(px, tmp, sw, sh, radius)
            boxBlurV(tmp, px, sw, sh, radius)
        }
        val out = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(out, w, h, true)
    }

    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = 2 * radius + 1
        for (y in 0 until h) {
            val row = y * w
            var r = 0
            var g = 0
            var b = 0
            for (x in -radius..radius) {
                val c = src[row + x.coerceIn(0, w - 1)]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = src[row + (x + radius + 1).coerceAtMost(w - 1)]
                val sub = src[row + (x - radius).coerceAtLeast(0)]
                r += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }

    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = 2 * radius + 1
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (y in -radius..radius) {
                val c = src[y.coerceIn(0, h - 1) * w + x]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = src[(y + radius + 1).coerceAtMost(h - 1) * w + x]
                val sub = src[(y - radius).coerceAtLeast(0) * w + x]
                r += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
