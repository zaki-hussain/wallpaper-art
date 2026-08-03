package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import dev.artwidget.art.Oklab
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A single flat colour. The quietest widget possible. */
object SolidPattern : Pattern {
    override val id = "solid"
    override val label = "Solid"
    override val colorRange = 1..1

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        // Background is pre-filled; nothing else to do.
    }
}

/** Smooth linear gradient at a random angle, optionally with a third mid stop. */
object GradientPattern : Pattern {
    override val id = "gradient"
    override val label = "Gradient"
    override val colorRange = 2..3

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val angle = rng.range(0f, (2 * Math.PI).toFloat())
        val cx = w / 2
        val cy = h / 2
        val r = sqrt(w * w + h * h) / 2
        val dx = cos(angle) * r
        val dy = sin(angle) * r
        // Monotonic lightness order avoids pale bands mid-gradient.
        val ordered = colors.sortedBy { Oklab.argbToOklch(it)[0] }
        val positions = when (ordered.size) {
            2 -> floatArrayOf(0f, 1f)
            else -> floatArrayOf(0f, rng.range(0.35f, 0.65f), 1f)
        }
        val (cols, stops) = Oklab.smoothStops(ordered, positions)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, cols, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
