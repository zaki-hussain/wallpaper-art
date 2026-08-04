package dev.wallpaperart.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.wallpaperart.art.Pattern
import dev.wallpaperart.art.Rng
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Weighted index pick; primary colour dominates, later colours are progressively rarer. */
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

/**
 * Classic Truchet tiling: every cell holds two stroked quarter-circle arcs in one of the
 * two orientations, so the arcs join across cell borders into long meandering paths.
 * A per-seed orientation bias changes the character of the maze; some seeds add tiny
 * dots at the arc endpoints, like solder points on the lattice.
 */
object TruchetPattern : Pattern {
    override val id = "truchet"
    override val label = "Truchet"
    override val colorRange = 2..3

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cell = (size * rng.range(0.08f, 0.14f)).coerceAtLeast(1e-3f)
        val nx = ceil(w / cell).toInt().coerceAtLeast(1)
        val ny = ceil(h / cell).toInt().coerceAtLeast(1)
        // Centre the lattice; edge cells overhang so arcs run right off the canvas.
        val x0 = (w - nx * cell) * 0.5f
        val y0 = (h - ny * cell) * 0.5f

        val strokeW = cell * rng.range(0.10f, 0.18f)
        val bias = rng.range(0.35, 0.65)               // per-seed leaning toward one orientation
        val accentChance = if (colors.size >= 3) rng.range(0.06, 0.10) else 0.0
        val drawDots = rng.chance(0.4)
        val dotR = strokeW * rng.range(0.55f, 0.75f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeW
        paint.strokeCap = Paint.Cap.BUTT
        val oval = RectF()
        val r = cell * 0.5f

        for (j in 0 until ny) {
            val ty = y0 + j * cell
            for (i in 0 until nx) {
                val tx = x0 + i * cell
                paint.color = if (accentChance > 0.0 && rng.chance(accentChance)) colors[2] else colors[1]
                if (rng.chance(bias)) {
                    // Arcs hugging the top-left and bottom-right corners.
                    oval.set(tx - r, ty - r, tx + r, ty + r)
                    canvas.drawArc(oval, 0f, 90f, false, paint)
                    oval.set(tx + cell - r, ty + cell - r, tx + cell + r, ty + cell + r)
                    canvas.drawArc(oval, 180f, 90f, false, paint)
                } else {
                    // Arcs hugging the top-right and bottom-left corners.
                    oval.set(tx + cell - r, ty - r, tx + cell + r, ty + r)
                    canvas.drawArc(oval, 90f, 90f, false, paint)
                    oval.set(tx - r, ty + cell - r, tx + r, ty + cell + r)
                    canvas.drawArc(oval, 270f, 90f, false, paint)
                }
            }
        }

        if (drawDots) {
            // Arc endpoints sit at the midpoints of every cell edge; mark each once.
            paint.style = Paint.Style.FILL
            paint.color = colors[1]
            for (j in 0..ny) {
                val gy = y0 + j * cell
                for (i in 0 until nx) {
                    canvas.drawCircle(x0 + (i + 0.5f) * cell, gy, dotR, paint)
                }
            }
            for (i in 0..nx) {
                val gx = x0 + i * cell
                for (j in 0 until ny) {
                    canvas.drawCircle(gx, y0 + (j + 0.5f) * cell, dotR, paint)
                }
            }
        }
    }
}

/**
 * Bauhaus study sheet: a square tile grid where each tile holds at most one flat shape
 * flush to its bounds — a quarter disc in a corner, a half disc on a side, an inscribed
 * circle, or a corner triangle. A third or more of the tiles stay empty, and adjacent
 * quarter discs occasionally meet into leaves and waves purely by chance.
 */
object BauhausPattern : Pattern {
    override val id = "bauhaus"
    override val label = "Bauhaus"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val n = rng.int(3..6)
        val t = (size / n).coerceAtLeast(1e-3f)
        // Exact fit on the short side; the long side overflows symmetrically.
        val nx = ceil(w / t - 1e-4f).toInt().coerceAtLeast(1)
        val ny = ceil(h / t - 1e-4f).toInt().coerceAtLeast(1)
        val x0 = (w - nx * t) * 0.5f
        val y0 = (h - ny * t) * 0.5f

        val emptyChance = rng.range(0.30, 0.40)
        val fg = colors.drop(1)
        val weights = foregroundWeights(fg.size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val oval = RectF()
        val path = Path()

        for (j in 0 until ny) {
            val y = y0 + j * t
            for (i in 0 until nx) {
                val x = x0 + i * t
                if (rng.chance(emptyChance)) continue
                paint.color = fg[weightedIndex(rng, weights)]
                val roll = rng.double()
                when {
                    roll < 0.34 -> {
                        // Quarter disc anchored to a random corner, radius = tile side.
                        val corner = rng.int(4)
                        val cx = if (corner == 1 || corner == 2) x + t else x
                        val cy = if (corner >= 2) y + t else y
                        oval.set(cx - t, cy - t, cx + t, cy + t)
                        canvas.drawArc(oval, 90f * corner, 90f, true, paint)
                    }
                    roll < 0.60 -> {
                        // Half disc, flat edge flush against a random side.
                        val r = t * 0.5f
                        when (rng.int(4)) {
                            0 -> { oval.set(x, y - r, x + t, y + r); canvas.drawArc(oval, 0f, 180f, true, paint) }
                            1 -> { oval.set(x + r, y, x + t + r, y + t); canvas.drawArc(oval, 90f, 180f, true, paint) }
                            2 -> { oval.set(x, y + r, x + t, y + t + r); canvas.drawArc(oval, 180f, 180f, true, paint) }
                            else -> { oval.set(x - r, y, x + r, y + t); canvas.drawArc(oval, 270f, 180f, true, paint) }
                        }
                    }
                    roll < 0.78 -> canvas.drawCircle(x + t * 0.5f, y + t * 0.5f, t * 0.5f, paint)
                    else -> {
                        // Right triangle filling half the tile, anchored to a random corner.
                        val corner = rng.int(4)
                        val cx = if (corner == 1 || corner == 2) x + t else x
                        val cy = if (corner >= 2) y + t else y
                        path.reset()
                        path.moveTo(cx, cy)
                        path.lineTo(if (cx == x) x + t else x, cy)
                        path.lineTo(cx, if (cy == y) y + t else y)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
            }
        }
    }
}

/**
 * Neo-plastic composition: the canvas is partitioned by a handful of axis-aligned splits
 * at thirds-and-halves proportions, a few cells receive flat accent fills, and the whole
 * lattice — filled cells included — is ruled over with primary-colour lines.
 */
object MondrianPattern : Pattern {
    override val id = "mondrian"
    override val label = "Composition"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val lw = size * rng.range(0.010f, 0.016f)
        val minSide = max(lw * 6f, size * 0.10f)

        val rects = mutableListOf(RectF(0f, 0f, w, h))
        val targetSplits = rng.int(3..6)
        var done = 0
        while (done < targetSplits) {
            if (done >= 2 && rng.chance(0.15)) break   // stop early at random
            // Split the largest cell that still has room for two decent halves.
            var best = -1
            var bestArea = 0f
            for (k in rects.indices) {
                val rc = rects[k]
                val area = rc.width() * rc.height()
                if (max(rc.width(), rc.height()) >= minSide * 3f && area > bestArea) {
                    best = k
                    bestArea = area
                }
            }
            if (best < 0) break
            val rc = rects.removeAt(best)
            val vertical = when {
                rc.width() > rc.height() * 1.3f -> true
                rc.height() > rc.width() * 1.3f -> false
                else -> rng.chance(0.5)
            }
            val frac = rng.pick(listOf(0.33f, 0.5f, 0.66f)) + rng.range(-0.05f, 0.05f)
            if (vertical) {
                val cut = (rc.left + rc.width() * frac).coerceIn(rc.left + minSide, rc.right - minSide)
                rects.add(RectF(rc.left, rc.top, cut, rc.bottom))
                rects.add(RectF(cut, rc.top, rc.right, rc.bottom))
            } else {
                val cut = (rc.top + rc.height() * frac).coerceIn(rc.top + minSide, rc.bottom - minSide)
                rects.add(RectF(rc.left, rc.top, rc.right, cut))
                rects.add(RectF(rc.left, cut, rc.right, rc.bottom))
            }
            done++
        }

        // Flat accent fills in a minority of cells; the largest cell stays background.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        if (rects.size >= 2) {
            var largest = 0
            for (k in rects.indices) {
                if (rects[k].width() * rects[k].height() >
                    rects[largest].width() * rects[largest].height()
                ) largest = k
            }
            val candidates = rng.shuffled(rects.indices.filter { it != largest })
            val fills = min(rng.int(2..4), max(1, rects.size / 2)).coerceAtMost(candidates.size)
            val accents = rng.shuffled(colors.drop(2))
            for (k in 0 until fills) {
                paint.color = accents[k % accents.size]
                canvas.drawRect(rects[candidates[k]], paint)
            }
        }

        // Rule every cell border — filled cells included — plus a full-width outer frame.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = lw
        paint.color = colors[1]
        for (rc in rects) canvas.drawRect(rc, paint)
        canvas.drawRect(lw * 0.5f, lw * 0.5f, w - lw * 0.5f, h - lw * 0.5f, paint)
    }
}
