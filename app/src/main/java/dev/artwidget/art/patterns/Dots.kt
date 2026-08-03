package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import dev.artwidget.art.Noise
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Weighted index pick; primary colour dominates, later colours are progressively rarer. */
private fun pickWeighted(rng: Rng, weights: DoubleArray): Int {
    val roll = rng.double()
    var acc = 0.0
    for (i in weights.indices) {
        acc += weights[i]
        if (roll < acc) return i
    }
    return weights.size - 1
}

private fun fgWeights(count: Int): DoubleArray = when (count) {
    1 -> doubleArrayOf(1.0)
    2 -> doubleArrayOf(0.72, 0.28)
    3 -> doubleArrayOf(0.62, 0.26, 0.12)
    else -> doubleArrayOf(0.56, 0.22, 0.13, 0.09)
}

/**
 * A calm grid of dots — square or hex per seed — whose sizes breathe with fbm noise so
 * whole regions fall quiet. Colour is assigned spatially: soft bands along a random
 * direction with an organic, noise-wobbled boundary.
 */
object DotsPattern : Pattern {
    override val id = "dots"
    override val label = "Dots"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val sizeNoise = Noise(rng.fork())
        val zoneNoise = Noise(rng.fork())

        val spacing = (size * rng.range(0.05f, 0.09f)).coerceAtLeast(1e-4f)
        val hexRows = rng.chance(0.5)
        val rowH = if (hexRows) spacing * 0.8660254f else spacing
        val baseR = spacing * rng.range(0.22f, 0.38f)
        val jitter = spacing * rng.range(0.03f, 0.09f)
        val sizeScale = rng.range(1.2, 2.6)
        val quiet = rng.range(0.08, 0.2) // portion of the noise range that collapses to zero

        // Spatial colour bands along a random direction, wobbled by noise.
        val bandAngle = rng.range(0.0, Math.PI * 2)
        val dirX = cos(bandAngle).toFloat()
        val dirY = sin(bandAngle).toFloat()
        val span = (abs(dirX) * w + abs(dirY) * h).coerceAtLeast(1e-4f)
        val projMin = min(0f, dirX * w) + min(0f, dirY * h)
        val wobbleAmp = rng.range(0.10, 0.20)
        val wobbleScale = rng.range(1.0, 1.8)

        val fg = colors.drop(1)
        val b1: Double
        val b2: Double
        when (fg.size) {
            1 -> { b1 = 2.0; b2 = 3.0 }
            2 -> { b1 = rng.range(0.42, 0.58); b2 = 3.0 }
            else -> { b1 = rng.range(0.52, 0.66); b2 = rng.range(0.82, 0.90) }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL

        var row = 0
        var y = -spacing
        while (y < h + spacing) {
            val rowOffset = if (hexRows && row % 2 == 1) spacing * 0.5f else 0f
            var x = -spacing + rowOffset
            while (x < w + spacing) {
                val px = x + rng.range(-jitter, jitter)
                val py = y + rng.range(-jitter, jitter)

                // Radius from fbm, stretched to fill its range. Most dots stay present
                // (radius breathing between ~40% and 100%); only values under the quiet
                // cut vanish, leaving occasional calm holes in the grid.
                val stretched = (sizeNoise.fbm(px / size * sizeScale, py / size * sizeScale, 3) * 1.7)
                    .coerceIn(-1.0, 1.0)
                val v = (stretched + 1.0) * 0.5
                var f = ((v - quiet) / (1.0 - quiet)).coerceIn(0.0, 1.0)
                f = f * f * (3.0 - 2.0 * f)
                val r = if (v < quiet) 0f else baseR * (0.4f + 0.6f * f.toFloat())
                if (r > size * 0.0012f) {
                    var t = ((px * dirX + py * dirY - projMin) / span).toDouble()
                    t += zoneNoise.fbm(px / size * wobbleScale, py / size * wobbleScale, 3) * wobbleAmp
                    val idx = when {
                        t < b1 -> 0
                        t < b2 -> min(1, fg.size - 1)
                        else -> min(2, fg.size - 1)
                    }
                    paint.color = fg[idx]
                    canvas.drawCircle(px, py, r, paint)
                }
                x += spacing
            }
            y += rowH
            row++
        }
    }
}

/**
 * Classic print halftone: a centred grid of dots whose radii follow a smooth ramp,
 * linear along a random direction or radial from a random corner. A third colour, if
 * present, runs as a quieter interleaved ramp from the opposite end.
 */
object HalftonePattern : Pattern {
    override val id = "halftone"
    override val label = "Halftone"
    override val colorRange = 2..3

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val spacing = (size * rng.range(0.045f, 0.075f)).coerceAtLeast(1e-4f)
        val radial = rng.chance(0.45)
        val gamma = rng.range(0.75, 1.55)
        val invert = rng.chance(0.5)

        // Linear ramp setup.
        val angle = rng.range(0.0, Math.PI * 2)
        val dirX = cos(angle).toFloat()
        val dirY = sin(angle).toFloat()
        val span = (abs(dirX) * w + abs(dirY) * h).coerceAtLeast(1e-4f)
        val projMin = min(0f, dirX * w) + min(0f, dirY * h)

        // Radial ramp setup: a random corner.
        val cornerX = if (rng.chance(0.5)) 0f else w
        val cornerY = if (rng.chance(0.5)) 0f else h
        val maxDist = hypot(w.toDouble(), h.toDouble()).toFloat().coerceAtLeast(1e-4f)

        fun ramp(px: Float, py: Float): Float {
            var t = if (radial) {
                hypot((px - cornerX).toDouble(), (py - cornerY).toDouble()).toFloat() / maxDist
            } else {
                (px * dirX + py * dirY - projMin) / span
            }
            t = t.coerceIn(0f, 1f)
            if (invert) t = 1f - t
            return t.toDouble().pow(gamma).toFloat()
        }

        // Centre the grid so margins read as intentional at any aspect ratio.
        val nx = floor(w / spacing).toInt().coerceAtLeast(1)
        val ny = floor(h / spacing).toInt().coerceAtLeast(1)
        val x0 = (w - (nx - 1) * spacing) * 0.5f
        val y0 = (h - (ny - 1) * spacing) * 0.5f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val minVisible = spacing * 0.02f

        // Optional secondary ramp: quieter, interleaved at half-cell offsets, growing
        // from the opposite end so it lives where the primary dots are small.
        if (colors.size >= 3) {
            paint.color = colors[2]
            for (j in 0 until ny - 1) {
                val py = y0 + (j + 0.5f) * spacing
                for (i in 0 until nx - 1) {
                    val px = x0 + (i + 0.5f) * spacing
                    val r = spacing * (0.02f + 0.20f * (1f - ramp(px, py)))
                    if (r > minVisible) canvas.drawCircle(px, py, r, paint)
                }
            }
        }

        paint.color = colors[1]
        for (j in 0 until ny) {
            val py = y0 + j * spacing
            for (i in 0 until nx) {
                val px = x0 + i * spacing
                val r = spacing * (0.03f + 0.43f * ramp(px, py))
                if (r > minVisible) canvas.drawCircle(px, py, r, paint)
            }
        }
    }
}

/**
 * Sparse terrazzo chips: small rounded 3-6-gons and squashed ellipses scattered on a
 * jittered grid so spacing stays even, coverage stays low, and the ground shows through.
 */
object TerrazzoPattern : Pattern {
    override val id = "terrazzo"
    override val label = "Terrazzo"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val spacing = (size * rng.range(0.095f, 0.135f)).coerceAtLeast(1e-4f)
        val jitter = spacing * rng.range(0.12f, 0.20f)
        val skipChance = rng.range(0.08, 0.20)
        val ellipseChance = rng.range(0.25, 0.55)

        val fg = colors.drop(1)
        val weights = fgWeights(fg.size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val path = Path()

        var y = -spacing * 0.5f
        while (y < h + spacing) {
            var x = -spacing * 0.5f
            while (x < w + spacing) {
                if (rng.chance(skipChance)) {
                    x += spacing
                    continue
                }
                val cx = x + rng.range(-jitter, jitter)
                val cy = y + rng.range(-jitter, jitter)
                val u = rng.float()
                val r = size * (0.015f + 0.035f * u * u)
                paint.color = fg[pickWeighted(rng, weights)]

                if (rng.chance(ellipseChance)) {
                    // Squashed ellipse, randomly rotated.
                    val squash = rng.range(0.45f, 0.75f)
                    val deg = rng.range(0f, 360f)
                    paint.pathEffect = null
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.rotate(deg)
                    canvas.drawOval(-r, -r * squash, r, r * squash, paint)
                    canvas.restore()
                } else {
                    // Irregular 3-6-gon with rounded joins.
                    val n = rng.int(3..6)
                    val a0 = rng.range(0.0, Math.PI * 2)
                    val step = Math.PI * 2 / n
                    path.reset()
                    for (k in 0 until n) {
                        val a = a0 + k * step + rng.range(-0.30, 0.30) * step
                        val rr = r * rng.range(0.70f, 1.25f)
                        val vx = cx + (cos(a) * rr).toFloat()
                        val vy = cy + (sin(a) * rr).toFloat()
                        if (k == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                    }
                    path.close()
                    paint.pathEffect = CornerPathEffect(r * 0.45f)
                    canvas.drawPath(path, paint)
                }
                x += spacing
            }
            y += spacing
        }
        paint.pathEffect = null
    }
}
