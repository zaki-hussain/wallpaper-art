package dev.wallpaperart.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.wallpaperart.art.Noise
import dev.wallpaperart.art.Oklab
import dev.wallpaperart.art.Pattern
import dev.wallpaperart.art.Rng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Linear blend of two ARGB palette colours (component-wise). */
private fun mix(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + (cb - ca) * u).toInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Topographic iso-lines of an fbm field, extracted with marching squares. */
object ContoursPattern : Pattern {
    override val id = "contours"
    override val label = "Contours"
    override val colorRange = 2..3

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val size = min(w, h)
        val longSide = max(w, h)
        if (size <= 0f) return
        val noise = Noise(rng.fork())

        // Sample grid: ~90..140 cells across the long side; coordinates are scaled by
        // the short side so noise features stay round at any aspect ratio.
        val cellsLong = rng.int(90..140)
        val cell = longSide / cellsLong
        val nx = ceil(w / cell).toInt() + 2
        val ny = ceil(h / cell).toInt() + 2
        val scale = rng.range(1.1, 2.3)
        val gain = rng.range(0.42, 0.55)
        val ox = rng.range(0.0, 64.0)
        val oy = rng.range(0.0, 64.0)

        val field = FloatArray(nx * ny)
        var vmin = Float.MAX_VALUE
        var vmax = -Float.MAX_VALUE
        for (j in 0 until ny) {
            val yy = oy + j * cell / size * scale
            for (i in 0 until nx) {
                val xx = ox + i * cell / size * scale
                val v = noise.fbm(xx, yy, 3, 2.0, gain).toFloat()
                field[j * nx + i] = v
                if (v < vmin) vmin = v
                if (v > vmax) vmax = v
            }
        }
        val span = vmax - vmin
        if (span < 1e-5f) return

        val levels = rng.int(6..12)
        val indexEvery = rng.int(4..5)
        val baseStroke = size * rng.range(0.003f, 0.0045f)
        val hasIndexColor = colors.size >= 3

        // Thin lines need more contrast than area fills; push the line colours' OKLab
        // lightness away from the background when the palette is too close.
        val bgL = Oklab.argbToOklch(colors[0])[0]
        fun boosted(c: Int): Int {
            val lch = Oklab.argbToOklch(c)
            val dl = lch[0] - bgL
            if (abs(dl) >= 0.28) return c
            val dir = if (bgL > 0.5) -1.0 else 1.0
            return Oklab.oklchToArgb((bgL + dir * 0.28).coerceIn(0.06, 0.97), lch[1], lch[2])
        }
        val lineColor = boosted(colors[1])
        val indexColor = if (hasIndexColor) boosted(colors[2]) else 0

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        val path = Path()
        for (k in 0 until levels) {
            val iso = vmin + span * (k + 1) / (levels + 1)
            path.reset()

            fun tOf(a: Float, b: Float): Float {
                val d = b - a
                return if (abs(d) < 1e-6f) 0.5f else ((iso - a) / d).coerceIn(0f, 1f)
            }

            fun seg(ax: Float, ay: Float, bx: Float, by: Float) {
                path.moveTo(ax, ay)
                path.lineTo(bx, by)
            }

            for (j in 0 until ny - 1) {
                val y0 = j * cell
                val y1 = y0 + cell
                val row = j * nx
                for (i in 0 until nx - 1) {
                    val v00 = field[row + i]           // top-left
                    val v10 = field[row + i + 1]       // top-right
                    val v01 = field[row + nx + i]      // bottom-left
                    val v11 = field[row + nx + i + 1]  // bottom-right
                    var c = 0
                    if (v00 > iso) c = c or 1
                    if (v10 > iso) c = c or 2
                    if (v11 > iso) c = c or 4
                    if (v01 > iso) c = c or 8
                    if (c == 0 || c == 15) continue
                    val x0 = i * cell
                    val x1 = x0 + cell
                    when (c) {
                        1, 14 -> seg(x0, y0 + cell * tOf(v00, v01), x0 + cell * tOf(v00, v10), y0)
                        2, 13 -> seg(x0 + cell * tOf(v00, v10), y0, x1, y0 + cell * tOf(v10, v11))
                        3, 12 -> seg(x0, y0 + cell * tOf(v00, v01), x1, y0 + cell * tOf(v10, v11))
                        4, 11 -> seg(x1, y0 + cell * tOf(v10, v11), x0 + cell * tOf(v01, v11), y1)
                        6, 9 -> seg(x0 + cell * tOf(v00, v10), y0, x0 + cell * tOf(v01, v11), y1)
                        7, 8 -> seg(x0, y0 + cell * tOf(v00, v01), x0 + cell * tOf(v01, v11), y1)
                        5 -> {
                            seg(x0, y0 + cell * tOf(v00, v01), x0 + cell * tOf(v00, v10), y0)
                            seg(x1, y0 + cell * tOf(v10, v11), x0 + cell * tOf(v01, v11), y1)
                        }
                        10 -> {
                            seg(x0 + cell * tOf(v00, v10), y0, x1, y0 + cell * tOf(v10, v11))
                            seg(x0 + cell * tOf(v01, v11), y1, x0, y0 + cell * tOf(v00, v01))
                        }
                    }
                }
            }

            if (hasIndexColor && (k + 1) % indexEvery == 0) {
                paint.color = indexColor
                paint.strokeWidth = baseStroke * 1.8f
            } else {
                paint.color = lineColor
                paint.strokeWidth = baseStroke
            }
            canvas.drawPath(path, paint)
        }
    }
}

/** Overlapping fish-scale rows: semicircular scales with crisp background seams. */
object ScallopsPattern : Pattern {
    override val id = "scallops"
    override val label = "Scallops"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        val size = min(w, h)
        if (size <= 0f) return
        val r = size * rng.range(0.07f, 0.12f)
        // Rows overlap: each row of full circles is partly covered by the row below it
        // (drawn later), leaving the classic upward crescents visible.
        val dy = r * rng.range(0.6f, 0.78f)

        val fg = colors.drop(1)
        val weights = when (fg.size) {
            1 -> doubleArrayOf(1.0)
            2 -> doubleArrayOf(0.72, 0.28)
            else -> doubleArrayOf(0.62, 0.26, 0.12)
        }

        fun weightedIndex(): Int {
            val roll = rng.double()
            var acc = 0.0
            for (i in weights.indices) {
                acc += weights[i]
                if (roll < acc) return i
            }
            return 0
        }

        // Rows are coloured either as blocks (mostly primary, occasional switches)
        // or as a gentle top-to-bottom gradient between two palette colours.
        val useGradient: Boolean
        var gFrom: Int
        var gTo: Int
        if (fg.size >= 2) {
            useGradient = rng.chance(0.55)
            gFrom = fg[0]
            gTo = fg[1 + rng.int(fg.size - 1)]
        } else {
            useGradient = rng.chance(0.5)
            gFrom = fg[0]
            gTo = mix(fg[0], colors[0], rng.range(0.25f, 0.4f))
        }
        if (rng.chance(0.5)) {
            val tmp = gFrom
            gFrom = gTo
            gTo = tmp
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.style = Paint.Style.FILL
        val seam = Paint(Paint.ANTI_ALIAS_FLAG)
        seam.style = Paint.Style.STROKE
        seam.color = colors[0]
        seam.strokeWidth = size * rng.range(0.0022f, 0.0035f)

        // Full circles drawn top row first; each later (lower) row covers the bottoms
        // of the row above, and upper rows show through the cusps between circles —
        // the classic seigaiha fish-scale tiling.
        val y0 = -r * rng.range(0.1f, 0.5f)
        val totalSpan = (h + r - y0).coerceAtLeast(1e-4f)
        var rowBase = fg[weightedIndex()]
        var y = y0
        var row = 0
        while (y < h + r) {
            val base = if (useGradient) {
                val t = ((y - y0) / totalSpan) + rng.range(-0.04f, 0.04f)
                mix(gFrom, gTo, t)
            } else {
                if (row > 0 && rng.chance(0.22)) rowBase = fg[weightedIndex()]
                rowBase
            }
            val accentPool = fg.filter { it != base }

            var x = -r + (if (row % 2 == 1) r else 0f)
            while (x < w + 2f * r) {
                fill.color = if (rng.chance(0.05)) {
                    if (accentPool.isEmpty()) mix(base, colors[0], 0.38f)
                    else accentPool[rng.int(accentPool.size)]
                } else {
                    base
                }
                canvas.drawCircle(x, y, r, fill)
                canvas.drawCircle(x, y, r, seam)
                x += 2f * r
            }
            y += dy
            row++
        }
    }
}
