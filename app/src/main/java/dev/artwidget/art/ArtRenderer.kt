package dev.artwidget.art

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Renders an ArtSpec to a bitmap at any size. */
object ArtRenderer {

    const val MAX_DIM = 2048

    fun render(spec: ArtSpec, width: Int, height: Int): Bitmap {
        val w = width.coerceIn(16, MAX_DIM)
        val h = height.coerceIn(16, MAX_DIM)
        val pattern = Patterns.byId(spec.pattern) ?: Patterns.all.first()

        // Fit the colour count to the pattern deterministically, so hand-edited or
        // pattern-switched specs always render.
        var colors = spec.colors.ifEmpty { listOf(0xFF808080.toInt()) }
        if (colors.size < pattern.colorRange.first) {
            colors = PaletteGen.extendTo(colors, pattern.colorRange.first, Rng(spec.seed))
        } else if (colors.size > pattern.colorRange.last) {
            colors = colors.take(pattern.colorRange.last)
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors[0])
        pattern.draw(canvas, w.toFloat(), h.toFloat(), colors, Rng(spec.seed))
        return bitmap
    }

    /** Copy with antialiased rounded corners (for the home-screen widget). */
    fun rounded(src: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        return out
    }
}
