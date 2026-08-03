package dev.artwidget.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.artwidget.art.Noise
import dev.artwidget.art.Pattern
import dev.artwidget.art.Rng
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

/** Per-channel linear blend of two ARGB colours; t is clamped to [0, 1]. */
private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    val aA = (a ushr 24) and 0xFF
    val aR = (a ushr 16) and 0xFF
    val aG = (a ushr 8) and 0xFF
    val aB = a and 0xFF
    val bA = (b ushr 24) and 0xFF
    val bR = (b ushr 16) and 0xFF
    val bG = (b ushr 8) and 0xFF
    val bB = b and 0xFF
    val oA = (aA + ((bA - aA) * tt)).toInt().coerceIn(0, 255)
    val oR = (aR + ((bR - aR) * tt)).toInt().coerceIn(0, 255)
    val oG = (aG + ((bG - aG) * tt)).toInt().coerceIn(0, 255)
    val oB = (aB + ((bB - aB) * tt)).toInt().coerceIn(0, 255)
    return (oA shl 24) or (oR shl 16) or (oG shl 8) or oB
}

/** Parallel bands in a repeating width rhythm, the background taking part as a band. */
object StripesPattern : Pattern {
    override val id = "stripes"
    override val label = "Stripes"
    override val colorRange = 2..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)

        // One angle per seed, biased toward the calm axes.
        val roll = rng.double()
        val angle = when {
            roll < 0.30 -> 0f
            roll < 0.60 -> 90f
            roll < 0.80 -> 45f * rng.sign()
            else -> rng.range(10f, 80f) * rng.sign()
        }

        // A short rhythm of band widths, repeated across the canvas.
        val rhythmLen = rng.int(2..4)
        val widths = FloatArray(rhythmLen) { size * rng.range(0.05f, 0.2f) }

        // Colour sequence over the whole palette. More often than not the background
        // is interleaved between every foreground band so the composition breathes.
        val colSeq: List<Int> = if (rng.chance(0.55)) {
            val out = ArrayList<Int>(colors.size * 2)
            for (c in rng.shuffled(colors.drop(1))) {
                out.add(c)
                out.add(colors[0])
            }
            out
        } else {
            rng.shuffled(colors)
        }

        // Occasionally a thin accent pinstripe rides a band boundary once per cycle.
        val pinstripe = colors.size >= 3 && rng.chance(0.45)
        val pinColor = colors.last()
        val pinW = size * rng.range(0.006f, 0.012f)
        val pinEvery = colSeq.size
        val pinPhase = rng.int(pinEvery)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL

        // Draw in a rotated frame; span the full diagonal so corners stay covered.
        val half = sqrt(w * w + h * h) * 0.5f + size * 0.25f
        val cx = w * 0.5f
        val cy = h * 0.5f
        canvas.save()
        canvas.rotate(angle, cx, cy)

        val cycle = widths.sum()
        var y = cy - half - rng.range(0f, cycle)
        var i = 0
        while (y < cy + half) {
            val bw = widths[i % rhythmLen]
            val c = colSeq[i % colSeq.size]
            if (c != colors[0]) {
                paint.color = c
                canvas.drawRect(cx - half, y, cx + half, y + bw, paint)
            }
            y += bw
            if (pinstripe && i % pinEvery == pinPhase) {
                paint.color = pinColor
                canvas.drawRect(cx - half, y - pinW * 0.5f, cx + half, y + pinW * 0.5f, paint)
            }
            i++
        }
        canvas.restore()
    }
}

/** Serene filled wave bands stacked top to bottom, each edge a sum of two sines. */
object WavesPattern : Pattern {
    override val id = "waves"
    override val label = "Waves"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val layers = rng.int(4..9)

        val topPad = rng.range(0.08f, 0.22f)
        val bottomPad = rng.range(0.02f, 0.1f)
        val spacing = h * (1f - topPad - bottomPad) / (layers - 1)

        // Per-seed: cycle the whole palette (background included) or alternate two colours.
        val cycleAll = rng.chance(0.6)
        val cycleOffset = rng.int(colors.size)
        val pairA: Int
        val pairB: Int
        if (colors.size > 2 && rng.chance(0.5)) {
            pairA = colors[1]
            pairB = colors[2 + rng.int(colors.size - 2)]
        } else {
            pairA = colors[0]
            pairB = colors[1]
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val path = Path()
        val steps = 72

        for (i in 0 until layers) {
            val base = h * topPad + spacing * i + rng.range(-0.25f, 0.25f) * spacing
            val a1 = h * rng.range(0.015f, 0.05f)
            val a2 = h * rng.range(0.015f, 0.05f)
            val wl1 = w * rng.range(0.4f, 1.2f)
            val wl2 = w * rng.range(0.4f, 1.2f)
            val p1 = rng.range(0f, TWO_PI)
            val p2 = rng.range(0f, TWO_PI)

            path.reset()
            for (s in 0..steps) {
                val x = w * s / steps
                val y = base + a1 * sin(TWO_PI * x / wl1 + p1) + a2 * sin(TWO_PI * x / wl2 + p2)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()

            paint.color = if (cycleAll) {
                colors[(i + cycleOffset) % colors.size]
            } else {
                if (i % 2 == 0) pairA else pairB
            }
            canvas.drawPath(path, paint)
        }
    }
}

/** Layered mountain silhouettes fading back-to-front for atmospheric depth. */
object RidgesPattern : Pattern {
    override val id = "ridges"
    override val label = "Ridges"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val noise = Noise(rng.fork())

        var layers = rng.int(3..6)
        val nFg = colors.size - 1
        // Step mode walks the palette in order (needs one colour per layer); otherwise
        // layers are lerped from a background-hazed back to a solid front colour.
        val stepMode = nFg >= 3 && rng.chance(0.45)
        if (stepMode) layers = layers.coerceAtMost(nFg)
        val front = if (rng.chance(0.7)) colors[1] else colors.last()
        val tBack = rng.range(0.22f, 0.38f)

        val startBase = h * rng.range(0.3f, 0.45f)
        val endBase = h * rng.range(0.72f, 0.88f)
        val scale = rng.range(1.2, 2.4)
        val xOff = rng.range(0.0, 64.0)
        val layerKey = 3.31

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val path = Path()
        val steps = 84

        for (li in 0 until layers) {
            val base = startBase + (endBase - startBase) * li / (layers - 1)
            val amp = h * rng.range(0.1f, 0.25f)

            path.reset()
            for (s in 0..steps) {
                val x = w * s / steps
                val n = (noise.fbm(x / w * scale + xOff, li * layerKey, 4) + 1.0) * 0.5
                val y = base - amp * n.toFloat()
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()

            paint.color = if (stepMode) {
                colors[1 + li]
            } else {
                val t = tBack + (1f - tBack) * li / (layers - 1)
                lerpColor(colors[0], front, t)
            }
            canvas.drawPath(path, paint)
        }
    }
}
