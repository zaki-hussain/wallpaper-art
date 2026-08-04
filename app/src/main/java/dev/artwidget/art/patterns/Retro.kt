package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.artwidget.art.Oklab
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.ceil
import kotlin.math.min

private fun weightedIndex(rng: Rng, weights: DoubleArray): Int {
    val roll = rng.double()
    var acc = 0.0
    for (i in weights.indices) {
        acc += weights[i]
        if (roll < acc) return i
    }
    return weights.size - 1
}

private fun foregroundWeights(count: Int): DoubleArray = when (count) {
    1 -> doubleArrayOf(1.0)
    2 -> doubleArrayOf(0.74, 0.26)
    3 -> doubleArrayOf(0.62, 0.26, 0.12)
    else -> doubleArrayOf(0.58, 0.22, 0.12, 0.08)
}

/** Lightness-shifted copy of a colour, for shading faces of one material. */
private fun shade(c: Int, dl: Double): Int {
    val lch = Oklab.argbToOklch(c)
    return Oklab.oklchToArgb((lch[0] + dl).coerceIn(0.05, 0.97), lch[1], lch[2])
}

/**
 * An isometric wall of cubes (rhombille tiling). Each cube keeps one palette colour;
 * its three faces are lightness shades of it, so the light always comes from above.
 */
object CubesPattern : Pattern {
    override val id = "cubes"
    override val label = "Cubes"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val a = size * rng.range(0.055f, 0.10f)     // half cube width
        val b = a / 1.7320508f                      // iso quarter height
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        fun face(vararg pts: Float) {
            path.reset()
            path.moveTo(pts[0], pts[1])
            for (k in 2 until pts.size step 2) path.lineTo(pts[k], pts[k + 1])
            path.close()
            canvas.drawPath(path, paint)
        }

        val ny = ceil(h / (3f * b)).toInt() + 2
        val nx = ceil(w / (2f * a)).toInt() + 2
        for (j in -1..ny) {
            val y = j * 3f * b
            val xOff = if (j % 2 == 0) 0f else a
            for (i in -1..nx) {
                val x = i * 2f * a + xOff
                val c = fg[weightedIndex(rng, weights)]
                // Top, left, right faces of a cube whose top point is (x, y).
                paint.color = shade(c, 0.10)
                face(x, y, x + a, y + b, x, y + 2 * b, x - a, y + b)
                paint.color = shade(c, -0.02)
                face(x - a, y + b, x, y + 2 * b, x, y + 4 * b, x - a, y + 3 * b)
                paint.color = shade(c, -0.10)
                face(x + a, y + b, x, y + 2 * b, x, y + 4 * b, x + a, y + 3 * b)
            }
        }
    }
}

/** Memphis-style confetti: sticks, discs, rings and triangles tossed on a jittered grid. */
object ConfettiPattern : Pattern {
    override val id = "confetti"
    override val label = "Confetti"
    override val colorRange = 4..6

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val t = size * rng.range(0.10f, 0.15f)
        val nx = ceil(w / t).toInt() + 1
        val ny = ceil(h / t).toInt() + 1
        val emptyChance = rng.range(0.15, 0.30)
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        for (j in 0 until ny) {
            for (i in 0 until nx) {
                if (rng.chance(emptyChance)) continue
                val cx = (i + 0.5f) * t + rng.range(-0.25f, 0.25f) * t
                val cy = (j + 0.5f) * t + rng.range(-0.25f, 0.25f) * t
                paint.color = fg[weightedIndex(rng, weights)]
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(rng.range(0f, 360f))
                val s = t * rng.range(0.16f, 0.26f)
                when (rng.int(4)) {
                    0 -> canvas.drawCircle(0f, 0f, s * 0.8f, paint)
                    1 -> {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = s * 0.45f
                        canvas.drawCircle(0f, 0f, s * 0.8f, paint)
                        paint.style = Paint.Style.FILL
                    }
                    2 -> {
                        // Rounded stick.
                        paint.strokeCap = Paint.Cap.ROUND
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = s * 0.5f
                        canvas.drawLine(-s, 0f, s, 0f, paint)
                        paint.style = Paint.Style.FILL
                    }
                    else -> {
                        path.reset()
                        path.moveTo(0f, -s)
                        path.lineTo(s, s * 0.8f)
                        path.lineTo(-s, s * 0.8f)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
                canvas.restore()
            }
        }
    }
}

/**
 * Homage to the square: a coarse grid of tiles, each holding nested squares that
 * shrink toward a shared gravity corner, colours rotating tile by tile.
 */
object NestedPattern : Pattern {
    override val id = "nested"
    override val label = "Nested"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val n = rng.int(2..4)
        val t = size / n
        val nx = ceil(w / t - 1e-4f).toInt().coerceAtLeast(1)
        val ny = ceil(h / t - 1e-4f).toInt().coerceAtLeast(1)
        val x0 = (w - nx * t) * 0.5f
        val y0 = (h - ny * t) * 0.5f
        // Gravity: where the nested squares sink to, shared per artwork.
        val gx = rng.range(0.25f, 0.75f)
        val gy = rng.range(0.55f, 0.85f)
        val levels = rng.int(3..4)
        val cycle = colors.drop(1) + colors[0]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (j in 0 until ny) {
            for (i in 0 until nx) {
                val phase = rng.int(cycle.size)
                for (k in 0 until levels) {
                    paint.color = cycle[(phase + k) % cycle.size]
                    val f = 1f - k / levels.toFloat()
                    val s = t * f
                    val left = x0 + i * t + (t - s) * gx
                    val top = y0 + j * t + (t - s) * gy
                    canvas.drawRect(left, top, left + s, top + s, paint)
                }
            }
        }
    }
}

/**
 * A large blossom: rings of elliptical petals around an off-centre heart, outer ring
 * behind inner, each ring rotated half a petal so they interleave.
 */
object PetalsPattern : Pattern {
    override val id = "petals"
    override val label = "Petals"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cx = w * rng.range(0.38f, 0.62f)
        val cy = h * rng.range(0.38f, 0.62f)
        val rings = min(colors.size - 1, rng.int(2..3))
        val petals = rng.int(6..11)
        val maxLen = size * rng.range(0.52f, 0.72f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val oval = RectF()
        for (ring in 0 until rings) {
            paint.color = colors[1 + ring]
            paint.alpha = 235
            val len = maxLen * (1f - 0.30f * ring)
            val pw = len * rng.range(0.30f, 0.42f)
            val offset = 360f / petals * (ring * 0.5f)
            for (p in 0 until petals) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(360f * p / petals + offset)
                oval.set(-pw / 2f, -len, pw / 2f, 0f)
                canvas.drawOval(oval, paint)
                canvas.restore()
            }
        }
        paint.alpha = 255
        paint.color = colors[0]
        canvas.drawCircle(cx, cy, maxLen * rng.range(0.10f, 0.16f), paint)
    }
}
