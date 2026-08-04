package dev.artwidget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** Applies the current art as the system wallpaper when the user has turned that on. */
object Wallpaper {

    const val STYLE_LINE = "line"
    const val STYLE_ARC = "arc"
    const val STYLE_FADE = "fade"
    const val STYLE_WAVE = "wave"
    const val STYLE_SCALLOP = "scallop"
    const val STYLE_ZIGZAG = "zigzag"
    const val STYLE_DIAGONAL = "diagonal"
    const val STYLE_MOUNTAINS = "mountains"
    val STYLES = listOf(
        STYLE_LINE, STYLE_ARC, STYLE_FADE, STYLE_WAVE,
        STYLE_SCALLOP, STYLE_ZIGZAG, STYLE_DIAGONAL, STYLE_MOUNTAINS
    )

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
        val art = compose(ArtRenderer.render(spec, w, h), spec.colors[0], solidPct, style, spec.seed)
        WallpaperManager.getInstance(context).setBitmap(art)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Paints the bottom [solidPct] percent of [art] in [color] — a calm area that keeps
     * home-screen icons legible — joined by the chosen divide style. Hard-edged styles
     * get a soft shadow above the edge so the divide reads as a sheet over the art
     * rather than a crop. [seed] keeps randomised edges (mountains) stable per art.
     * Draws into [art].
     */
    fun compose(art: Bitmap, color: Int, solidPct: Int, style: String, seed: Long = 0L): Bitmap {
        if (solidPct <= 0) return art
        val w = art.width.toFloat()
        val h = art.height.toFloat()
        val splitY = h * (100 - solidPct.coerceAtMost(90)) / 100f
        val canvas = Canvas(art)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        if (style == STYLE_FADE) {
            // A tall band with smoothstep-eased alpha, so both ends of the fade land gently.
            val band = minOf(h * 0.3f, splitY)
            val n = 9
            val cols = IntArray(n) { i ->
                val t = i / (n - 1f)
                val s = t * t * (3 - 2 * t)
                ((s * 255).toInt() shl 24) or (color and 0x00FFFFFF)
            }
            val pos = FloatArray(n) { it / (n - 1f) }
            val fade = Paint().apply {
                shader = LinearGradient(0f, splitY - band, 0f, splitY, cols, pos, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, splitY - band, w, splitY, fade)
            canvas.drawRect(0f, splitY, w, h, paint)
            return art
        }

        paint.setShadowLayer(h * 0.012f, 0f, -h * 0.005f, 0x40000000)
        canvas.drawPath(dividePath(style, w, h, splitY, seed), paint)
        return art
    }

    /** Builds the solid region: a top edge from x=0 to x=w in [style], closed at the bottom. */
    private fun dividePath(style: String, w: Float, h: Float, splitY: Float, seed: Long): Path {
        val p = Path()
        when (style) {
            STYLE_ARC -> {
                val rise = h * 0.04f
                p.moveTo(0f, splitY)
                p.quadTo(w / 2f, splitY - 2 * rise, w, splitY)
            }
            STYLE_WAVE -> {
                val amp = h * 0.015f
                val periods = 2.5f
                p.moveTo(0f, splitY)
                var x = 0f
                while (x < w) {
                    p.lineTo(x, splitY + amp * sin(2f * PI.toFloat() * periods * x / w))
                    x += w / 120f
                }
                p.lineTo(w, splitY)
            }
            STYLE_SCALLOP -> {
                val bumps = 6
                val r = w / (2f * bumps)
                p.moveTo(0f, splitY)
                for (i in 0 until bumps) {
                    p.arcTo(RectF(2 * r * i, splitY - r, 2 * r * (i + 1), splitY + r), 180f, 180f)
                }
            }
            STYLE_ZIGZAG -> {
                val amp = h * 0.02f
                val peaks = 10
                p.moveTo(0f, splitY)
                for (i in 1..peaks) {
                    val x = w * i / peaks
                    val xMid = x - w / (2f * peaks)
                    p.lineTo(xMid, splitY - amp)
                    p.lineTo(x, splitY + amp)
                }
            }
            STYLE_DIAGONAL -> {
                val drop = h * 0.035f
                p.moveTo(0f, splitY + drop)
                p.lineTo(w, splitY - drop)
            }
            STYLE_MOUNTAINS -> {
                val rnd = Random(seed)
                val amp = h * 0.035f
                val segs = 18
                p.moveTo(0f, splitY - amp * rnd.nextFloat())
                for (i in 1..segs) {
                    p.lineTo(w * i / segs, splitY - amp * rnd.nextFloat())
                }
            }
            else -> { // line
                p.moveTo(0f, splitY)
                p.lineTo(w, splitY)
            }
        }
        p.lineTo(w, h)
        p.lineTo(0f, h)
        p.close()
        return p
    }
}
