package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.artwidget.art.Noise
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Streamlines traced through a Perlin flow field. */
object FlowPattern : Pattern {
    override val id = "flow"
    override val label = "Flow field"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val noise = Noise(rng.fork())
        val size = min(w, h)
        val scale = rng.range(0.9, 1.9)
        val curl = rng.range(1.6, 2.6)
        val baseAngle = rng.range(0.0, Math.PI * 2)
        val spacing = size * rng.range(0.03f, 0.052f)
        val stepLen = size * 0.005f
        val steps = rng.int(50..110)
        val strokeW = size * rng.range(0.0028f, 0.0055f)

        val fg = colors.drop(1)
        // Primary colour dominates; later colours appear progressively less often.
        val weights = when (fg.size) {
            1 -> doubleArrayOf(1.0)
            2 -> doubleArrayOf(0.72, 0.28)
            else -> doubleArrayOf(0.62, 0.26, 0.12)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeW
        paint.strokeCap = Paint.Cap.ROUND

        val path = Path()
        val margin = size * 0.12f
        var y = -margin
        while (y < h + margin) {
            var x = -margin
            while (x < w + margin) {
                val sx = x + rng.range(-spacing, spacing) * 0.4f
                val sy = y + rng.range(-spacing, spacing) * 0.4f
                path.reset()
                path.moveTo(sx, sy)
                var px = sx
                var py = sy
                repeat(steps) {
                    val n = noise.fbm(px / size * scale, py / size * scale, 3)
                    val angle = baseAngle + n * Math.PI * curl
                    px += (cos(angle) * stepLen).toFloat()
                    py += (sin(angle) * stepLen).toFloat()
                    path.lineTo(px, py)
                }
                val roll = rng.double()
                var acc = 0.0
                var idx = 0
                for (i in weights.indices) {
                    acc += weights[i]
                    if (roll < acc) {
                        idx = i
                        break
                    }
                }
                paint.color = fg[idx]
                canvas.drawPath(path, paint)
                x += spacing
            }
            y += spacing
        }
    }
}
