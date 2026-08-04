package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import dev.artwidget.art.Oklab
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

/** Distance from (x, y) to the farthest canvas corner. */
private fun farthestCorner(x: Float, y: Float, w: Float, h: Float): Float =
    max(max(hypot(x, y), hypot(w - x, y)), max(hypot(x, h - y), hypot(w - x, h - y)))

/**
 * Nested semicircle bands rising from an anchor on the bottom (or top) edge, like a
 * boho sunrise. Bands cycle through the palette; a slight per-ring wobble on some
 * seeds keeps it hand-made rather than mechanical.
 */
object ArchesPattern : Pattern {
    override val id = "arches"
    override val label = "Arches"
    override val colorRange = 3..6

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val cx = w * rng.range(0.30f, 0.70f)
        val bottom = rng.chance(0.75)
        val cy = if (bottom) h else 0f
        val maxR = farthestCorner(cx, cy, w, h) * 1.02f
        val bands = rng.int(5..9)
        val bandW = maxR / bands
        val wobble = if (rng.chance(0.4)) bandW * 0.05f else 0f
        // Cycle foregrounds; the background joins the cycle so some rings breathe.
        val cycle = rng.shuffled(colors.drop(1)) + listOf(colors[0])

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until bands) {
            paint.color = cycle[i % cycle.size]
            val r = (bands - i) * bandW
            canvas.drawCircle(
                cx + rng.range(-wobble, wobble),
                cy + rng.range(-wobble, wobble),
                r, paint
            )
        }
    }
}

/**
 * A thick Archimedean spiral stroked from a centre point out past the corners; with a
 * third colour a second arm interleaves half a turn behind, barber-pole style.
 */
object SpiralPattern : Pattern {
    override val id = "spiral"
    override val label = "Spiral"
    override val colorRange = 2..3

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cx = w * rng.range(0.35f, 0.65f)
        val cy = h * rng.range(0.35f, 0.65f)
        val gap = size * rng.range(0.07f, 0.13f)
        val maxR = farthestCorner(cx, cy, w, h) + gap

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = gap * rng.range(0.30f, 0.44f)
        paint.strokeCap = Paint.Cap.ROUND
        val turn = (2 * Math.PI).toFloat()
        val arms = if (colors.size >= 3) 2 else 1
        val flip = if (rng.chance(0.5)) 1f else -1f

        for (arm in 0 until arms) {
            paint.color = colors[1 + arm]
            val path = Path()
            var theta = arm * turn / arms
            var r = gap * theta / turn
            path.moveTo(cx, cy)
            while (r < maxR) {
                val step = 0.18f * gap / max(r, gap)   // ~constant arc length
                theta += step
                r = gap * theta / turn
                path.lineTo(
                    cx + r * kotlin.math.cos(theta * flip),
                    cy + r * sin(theta * flip)
                )
            }
            canvas.drawPath(path, paint)
        }
    }
}

/**
 * Overlapping translucent discs in soft focus, large ones behind small ones — the
 * out-of-focus highlights of a photograph.
 */
object BokehPattern : Pattern {
    override val id = "bokeh"
    override val label = "Bokeh"
    override val colorRange = 3..6

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)
        val n = rng.int(16..30)

        class Disc(val x: Float, val y: Float, val r: Float, val c: Int, val a: Int)

        val discs = (0 until n).map {
            val t = rng.float()
            val r = size * (0.05f + 0.18f * t * t)   // bias toward small
            Disc(
                rng.range(-r, w + r), rng.range(-r, h + r), r,
                fg[weightedIndex(rng, weights)], rng.int(40..110)
            )
        }.sortedByDescending { it.r }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (d in discs) {
            paint.color = d.c
            paint.alpha = d.a
            canvas.drawCircle(d.x, d.y, d.r, paint)
        }
    }
}

/**
 * Layered hill silhouettes built from summed sine curves. Layers close to the
 * background's lightness sit far away, high-contrast ones come forward — a cheap
 * atmospheric perspective that makes any palette read as a landscape.
 */
object HillsPattern : Pattern {
    override val id = "hills"
    override val label = "Hills"
    override val colorRange = 3..6

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val bgL = Oklab.argbToOklch(colors[0])[0]
        val layers = colors.drop(1).sortedBy { abs(Oklab.argbToOklch(it)[0] - bgL) }
        val n = layers.size

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        layers.forEachIndexed { k, color ->
            val base = h * (0.30f + 0.62f * (k + 1) / (n + 1f))
            val a1 = h * rng.range(0.03f, 0.09f)
            val a2 = h * rng.range(0.01f, 0.04f)
            val f1 = rng.range(0.8f, 1.8f) * (2 * Math.PI).toFloat() / w
            val f2 = rng.range(2.2f, 4.0f) * (2 * Math.PI).toFloat() / w
            val p1 = rng.range(0f, (2 * Math.PI).toFloat())
            val p2 = rng.range(0f, (2 * Math.PI).toFloat())

            path.reset()
            path.moveTo(0f, base + a1 * sin(p1) + a2 * sin(p2))
            var x = 0f
            while (x < w) {
                x += w / 90f
                path.lineTo(x, base + a1 * sin(f1 * x + p1) + a2 * sin(f2 * x + p2))
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            paint.color = color
            canvas.drawPath(path, paint)
        }
    }
}

/**
 * Tilted glowing pillars with soft edges, layered like curtains of light. On dark
 * palettes it reads as an aurora, on light ones as washes of tissue paper.
 */
object AuroraPattern : Pattern {
    override val id = "aurora"
    override val label = "Aurora"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val fg = colors.drop(1)
        val bands = rng.int(4..7)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()

        for (i in 0 until bands) {
            val color = fg[i % fg.size]
            val bw = w * rng.range(0.16f, 0.38f)
            val cx = w * rng.range(0.05f, 0.95f)
            val alpha = rng.int(90..170)
            val edge = (color and 0x00FFFFFF)
            val core = (alpha shl 24) or edge
            canvas.save()
            canvas.translate(cx, h / 2f)
            canvas.rotate(rng.range(-14f, 14f))
            paint.shader = LinearGradient(
                -bw / 2f, 0f, bw / 2f, 0f,
                intArrayOf(edge, core, edge),
                floatArrayOf(0f, rng.range(0.35f, 0.65f), 1f),
                Shader.TileMode.CLAMP
            )
            rect.set(-bw / 2f, -h, bw / 2f, h)
            canvas.drawRect(rect, paint)
            canvas.restore()
        }
        paint.shader = null
    }
}
