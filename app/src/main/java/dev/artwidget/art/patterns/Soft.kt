package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.artwidget.art.Noise
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Replaces a colour's alpha channel, keeping its RGB. */
private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/**
 * Picks an index into a foreground-colour list, weighted so the primary colour
 * dominates and later accents appear progressively less often.
 */
private fun weightedIndex(rng: Rng, count: Int): Int {
    val weights = when (count) {
        1 -> doubleArrayOf(1.0)
        2 -> doubleArrayOf(0.72, 0.28)
        3 -> doubleArrayOf(0.62, 0.26, 0.12)
        else -> doubleArrayOf(0.55, 0.24, 0.13, 0.08)
    }
    val roll = rng.double()
    var acc = 0.0
    for (i in weights.indices) {
        acc += weights[i]
        if (roll < acc) return i
    }
    return 0
}

/**
 * Hand-placed-feeling scatter over a jittered 3x3 grid, greedily picking cells that
 * maximise the minimum distance to those already chosen, so small counts still spread
 * across the whole canvas instead of clustering in one corner. Returns [fx, fy]
 * fractions of w/h; jitter can push points slightly beyond the canvas.
 */
private fun spreadAnchors(rng: Rng, count: Int, jitter: Float): List<FloatArray> {
    val fractions = floatArrayOf(1f / 6f, 0.5f, 5f / 6f)
    val cells = ArrayList<FloatArray>(9)
    for (gy in fractions) for (gx in fractions) cells.add(floatArrayOf(gx, gy))
    val pool = rng.shuffled(cells).toMutableList()
    val chosen = ArrayList<FloatArray>(count)
    chosen.add(pool.removeAt(0))
    while (chosen.size < count && pool.isNotEmpty()) {
        var bestIdx = 0
        var bestDist = -1f
        for (i in pool.indices) {
            var minD = Float.MAX_VALUE
            for (c in chosen) {
                val dx = pool[i][0] - c[0]
                val dy = pool[i][1] - c[1]
                val d = dx * dx + dy * dy
                if (d < minD) minD = d
            }
            if (minD > bestDist) {
                bestDist = minD
                bestIdx = i
            }
        }
        chosen.add(pool.removeAt(bestIdx))
    }
    return chosen.map {
        floatArrayOf(it[0] + rng.range(-jitter, jitter), it[1] + rng.range(-jitter, jitter))
    }
}

/** A single soft radial gradient from an off-centre focal point. */
object RadialPattern : Pattern {
    override val id = "radial"
    override val label = "Radial glow"
    override val colorRange = 2..3

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val diag = sqrt(w * w + h * h)
        val fx = w * rng.range(0.15f, 0.85f)
        val fy = h * rng.range(0.15f, 0.85f)
        val radius = diag * rng.range(0.7f, 1.3f)

        val cols: IntArray
        val stops: FloatArray
        if (colors.size >= 3) {
            // Accent core melting through the primary into the background —
            // or, occasionally, a primary core with an accent halo.
            cols = if (rng.chance(0.3)) {
                intArrayOf(colors[1], colors[2], colors[0])
            } else {
                intArrayOf(colors[2], colors[1], colors[0])
            }
            stops = floatArrayOf(0f, rng.range(0.28f, 0.55f), 1f)
        } else {
            // Two colours: a small flat core, then a long fade to the background.
            cols = intArrayOf(colors[1], colors[1], colors[0])
            stops = floatArrayOf(0f, rng.range(0.04f, 0.18f), 1f)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(fx, fy, radius, cols, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        // Optional faint second glow roughly opposite the focal point, for depth.
        if (rng.chance(0.6)) {
            val gx = (w - fx) + w * rng.range(-0.12f, 0.12f)
            val gy = (h - fy) + h * rng.range(-0.12f, 0.12f)
            val gr = diag * rng.range(0.3f, 0.55f)
            val gc = if (colors.size >= 3) colors[2] else colors[1]
            val ga = rng.int(36..80)
            paint.shader = RadialGradient(
                gx, gy, gr,
                intArrayOf(withAlpha(gc, ga), gc and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(gx, gy, gr, paint)
        }
    }
}

/** Overlapping translucent radial glows that melt into a soft colour field. */
object MeshPattern : Pattern {
    override val id = "mesh"
    override val label = "Gradient mesh"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val size = min(w, h)
        val fg = colors.drop(1)
        val count = rng.int(3..6)
        val anchors = spreadAnchors(rng, count, 0.22f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            // Cycle through the foreground colours, primary first (and therefore
            // most often), so accents stay sparing. Strong core alpha keeps hues
            // luminous instead of milky where glows overlap.
            val c = fg[i % fg.size]
            val cx = w * anchors[i][0]
            val cy = h * anchors[i][1]
            val r = size * rng.range(0.45f, 1.05f)
            val a = rng.int(150..225)
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(withAlpha(c, a), withAlpha(c, (a * 0.6f).toInt()), c and 0x00FFFFFF),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
    }
}

/** A few large organic shapes with smooth noise-warped outlines and flat fills. */
object BlobsPattern : Pattern {
    override val id = "blobs"
    override val label = "Blobs"
    override val colorRange = 3..5

    private const val MAX_POINTS = 14

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val noise = Noise(rng.fork())
        val size = min(w, h)
        val fg = colors.drop(1)
        val count = rng.int(3..5)
        val anchors = spreadAnchors(rng, count, 0.16f)

        // Centre + base radius per blob; sorted so the largest is drawn first
        // (at the back) and carries the primary colour.
        val specs = ArrayList<FloatArray>(count)
        for (i in 0 until count) {
            specs.add(
                floatArrayOf(
                    w * anchors[i][0],
                    h * anchors[i][1],
                    size * rng.range(0.2f, 0.4f)
                )
            )
        }
        specs.sortByDescending { it[2] }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val path = Path()
        val xs = FloatArray(MAX_POINTS)
        val ys = FloatArray(MAX_POINTS)

        for (b in specs.indices) {
            val cx = specs[b][0]
            val cy = specs[b][1]
            val baseR = specs[b][2]
            val m = rng.int(8..MAX_POINTS)
            val freq = rng.range(0.8, 1.6)
            val amp = rng.range(0.22f, 0.35f)
            val ox = rng.range(0.0, 64.0)
            val oy = rng.range(0.0, 64.0)
            val squashX = rng.range(0.88f, 1.12f)
            val squashY = rng.range(0.88f, 1.12f)

            // Sample the outline: a circle in noise space gives a smooth,
            // naturally closed radius modulation.
            for (i in 0 until m) {
                val t = i * 2.0 * Math.PI / m
                val nv = noise.noise(ox + cos(t) * freq, oy + sin(t) * freq).toFloat()
                val r = baseR * (1f + amp * nv)
                xs[i] = cx + cos(t).toFloat() * r * squashX
                ys[i] = cy + sin(t).toFloat() * r * squashY
            }

            // Closed Catmull-Rom spline as cubic Beziers: C1-continuous, no corners.
            path.reset()
            path.moveTo(xs[0], ys[0])
            for (i in 0 until m) {
                val i0 = (i - 1 + m) % m
                val i2 = (i + 1) % m
                val i3 = (i + 2) % m
                val c1x = xs[i] + (xs[i2] - xs[i0]) / 6f
                val c1y = ys[i] + (ys[i2] - ys[i0]) / 6f
                val c2x = xs[i2] - (xs[i3] - xs[i]) / 6f
                val c2y = ys[i2] - (ys[i3] - ys[i]) / 6f
                path.cubicTo(c1x, c1y, c2x, c2y, xs[i2], ys[i2])
            }
            path.close()

            // Cycle colours for the first few blobs so neighbours always differ,
            // then fall back to weighted picks.
            val colorIndex = if (b < fg.size) b else weightedIndex(rng, fg.size)
            paint.color = withAlpha(fg[colorIndex], rng.int(224..240))
            canvas.drawPath(path, paint)
        }
    }
}
