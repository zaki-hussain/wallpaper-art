package dev.wallpaperart.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.wallpaperart.art.Pattern
import dev.wallpaperart.art.Rng
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

/** Full-width chevron bands; the palette cycles, with the background joining in. */
object ChevronPattern : Pattern {
    override val id = "chevron"
    override val label = "Chevron"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val bandH = size * rng.range(0.07f, 0.13f)
        val amp = bandH * rng.range(0.5f, 1.1f)
        val peaks = ceil(w / (size * rng.range(0.28f, 0.5f))).toInt().coerceAtLeast(2)
        val cycle = rng.shuffled(colors.drop(1)) +
            (if (rng.chance(0.5)) listOf(colors[0]) else emptyList())

        fun zigY(base: Float, i: Int) = base + (if (i % 2 == 0) -amp else amp) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        var base = -amp
        var band = 0
        while (base < h + amp) {
            paint.color = cycle[band % cycle.size]
            path.reset()
            path.moveTo(0f, zigY(base, 0))
            for (i in 1..peaks) path.lineTo(w * i / peaks, zigY(base, i))
            for (i in peaks downTo 0) path.lineTo(w * i / peaks, zigY(base + bandH, i))
            path.close()
            canvas.drawPath(path, paint)
            base += bandH
            band++
        }
    }
}

/** Harlequin diamond tiling: chequered rhombi with an occasional accent swap. */
object DiamondsPattern : Pattern {
    override val id = "diamonds"
    override val label = "Diamonds"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cw = size * rng.range(0.11f, 0.19f)
        val ch = cw * rng.range(1.25f, 1.8f)
        val nx = ceil(w / cw).toInt() + 2
        val ny = ceil(h / (ch / 2f)).toInt() + 2
        val accentChance = if (colors.size >= 4) rng.range(0.04, 0.10) else 0.0
        val a = colors[1]
        val b = if (colors.size >= 3) colors[2] else colors[0]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        for (j in -1..ny) {
            val cy = j * ch / 2f
            val xOff = if (j % 2 == 0) 0f else cw / 2f
            for (i in -1..nx) {
                val cx = i * cw + xOff
                paint.color = when {
                    accentChance > 0 && rng.chance(accentChance) -> colors[3]
                    j % 2 == 0 -> a
                    else -> b
                }
                path.reset()
                path.moveTo(cx, cy - ch / 2f)
                path.lineTo(cx + cw / 2f, cy)
                path.lineTo(cx, cy + ch / 2f)
                path.lineTo(cx - cw / 2f, cy)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }
}

/** Translucent plaid: broad warp and weft bands whose crossings deepen, plus pinstripes. */
object WeavePattern : Pattern {
    override val id = "weave"
    override val label = "Plaid"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        repeat(rng.int(3..5)) {
            paint.color = fg[weightedIndex(rng, weights)]
            paint.alpha = rng.int(120..200)
            val bw = size * rng.range(0.05f, 0.16f)
            val x = rng.range(-bw, w)
            canvas.drawRect(x, 0f, x + bw, h, paint)
        }
        repeat(rng.int(3..5)) {
            paint.color = fg[weightedIndex(rng, weights)]
            paint.alpha = rng.int(120..200)
            val bh = size * rng.range(0.05f, 0.16f)
            val y = rng.range(-bh, h)
            canvas.drawRect(0f, y, w, y + bh, paint)
        }
        repeat(rng.int(1..3)) {
            paint.color = fg[fg.size - 1]
            paint.alpha = 255
            val t = size * rng.range(0.006f, 0.012f)
            if (rng.chance(0.5)) {
                val x = rng.range(0f, w)
                canvas.drawRect(x, 0f, x + t, h, paint)
            } else {
                val y = rng.range(0f, h)
                canvas.drawRect(0f, y, w, y + t, paint)
            }
        }
    }
}

/** A jittered grid of plus signs, softly rotated, some cells left empty. */
object CrossesPattern : Pattern {
    override val id = "crosses"
    override val label = "Crosses"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val t = size * rng.range(0.12f, 0.20f)
        val nx = ceil(w / t).toInt() + 1
        val ny = ceil(h / t).toInt() + 1
        val s = t * rng.range(0.40f, 0.55f)
        val arm = s * rng.range(0.30f, 0.42f)
        val emptyChance = rng.range(0.10, 0.30)
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)
        val maxTilt = rng.range(0f, 10f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (j in 0 until ny) {
            for (i in 0 until nx) {
                if (rng.chance(emptyChance)) continue
                val cx = (i + 0.5f) * t + rng.range(-t, t) * 0.06f
                val cy = (j + 0.5f) * t + rng.range(-t, t) * 0.06f
                paint.color = fg[weightedIndex(rng, weights)]
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(rng.range(-maxTilt, maxTilt))
                canvas.drawRect(-s / 2f, -arm / 2f, s / 2f, arm / 2f, paint)
                canvas.drawRect(-arm / 2f, -s / 2f, arm / 2f, s / 2f, paint)
                canvas.restore()
            }
        }
    }
}

/** Diagonal bands with staircase edges, from colouring a square grid by its diagonals. */
object StepsPattern : Pattern {
    override val id = "steps"
    override val label = "Steps"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val t = size * rng.range(0.05f, 0.09f)
        val nx = ceil(w / t).toInt() + 1
        val ny = ceil(h / t).toInt() + 1
        val bandCells = rng.int(2..4)
        val flip = rng.chance(0.5)
        val cycle = rng.shuffled(colors.drop(1)) +
            (if (rng.chance(0.6)) listOf(colors[0]) else emptyList())

        // No antialiasing: cells are axis-aligned and AA edges would show as seams.
        val paint = Paint()
        for (j in 0 until ny) {
            for (i in 0 until nx) {
                val d = if (flip) i + j else i + (ny - j)
                val band = (d / bandCells) % cycle.size
                paint.color = cycle[band]
                canvas.drawRect(i * t, j * t, (i + 1) * t + 0.5f, (j + 1) * t + 0.5f, paint)
            }
        }
    }
}

/** Staggered columns of soft capsules, like a bead curtain. */
object PillsPattern : Pattern {
    override val id = "pills"
    override val label = "Pills"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cw = size * rng.range(0.10f, 0.16f)
        val pw = cw * rng.range(0.55f, 0.75f)
        val cols = ceil(w / cw).toInt() + 1
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        for (i in 0 until cols) {
            val cx = (i + 0.5f) * cw
            var y = -rng.range(0f, cw * 2f)
            while (y < h) {
                val ph = cw * rng.range(1.1f, 2.6f)
                paint.color = fg[weightedIndex(rng, weights)]
                rect.set(cx - pw / 2f, y, cx + pw / 2f, y + ph)
                canvas.drawRoundRect(rect, pw / 2f, pw / 2f, paint)
                y += ph + cw * rng.range(0.25f, 0.55f)
            }
        }
    }
}
