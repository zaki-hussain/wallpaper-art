package dev.wallpaperart.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.wallpaperart.art.Noise
import dev.wallpaperart.art.Pattern
import dev.wallpaperart.art.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Tessellation patterns: Voronoi cells, jittered-grid triangles, and a hex mosaic.
 * All three lean on thin background-coloured grout so the shapes read as a calm,
 * hand-set mosaic rather than a busy fill.
 */

/** Primary colour dominates; later colours appear progressively less often. */
private fun fgWeights(count: Int): DoubleArray = when (count) {
    1 -> doubleArrayOf(1.0)
    2 -> doubleArrayOf(0.72, 0.28)
    3 -> doubleArrayOf(0.62, 0.26, 0.12)
    else -> doubleArrayOf(0.58, 0.24, 0.12, 0.06)
}

private fun pickIndex(rng: Rng, weights: DoubleArray): Int {
    var total = 0.0
    for (weight in weights) total += weight
    if (total <= 0.0) return 0
    val roll = rng.double() * total
    var acc = 0.0
    for (i in weights.indices) {
        acc += weights[i]
        if (roll < acc) return i
    }
    return weights.size - 1
}

private fun toPath(poly: List<FloatArray>, path: Path) {
    path.reset()
    path.moveTo(poly[0][0], poly[0][1])
    for (i in 1 until poly.size) path.lineTo(poly[i][0], poly[i][1])
    path.close()
}

/**
 * Sutherland-Hodgman clip of a convex polygon against the half-plane
 * { p : dot(p - m, n) <= 0 }, i.e. the side of the bisector nearer the cell's seed.
 */
private fun clipHalfPlane(poly: List<FloatArray>, mx: Float, my: Float, nx: Float, ny: Float): List<FloatArray> {
    val out = ArrayList<FloatArray>(poly.size + 2)
    val n = poly.size
    for (i in 0 until n) {
        val a = poly[i]
        val b = poly[(i + 1) % n]
        val da = (a[0] - mx) * nx + (a[1] - my) * ny
        val db = (b[0] - mx) * nx + (b[1] - my) * ny
        if (da <= 0f) out.add(a)
        if ((da <= 0f) != (db <= 0f)) {
            val denom = da - db
            if (abs(denom) > 1e-7f) {
                val t = da / denom
                out.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            }
        }
    }
    return out
}

/** Voronoi cells from jittered-grid seeds, separated by thin background grout. */
object VoronoiPattern : Pattern {
    override val id = "voronoi"
    override val label = "Voronoi"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)

        // 12..28 seeds on a jittered grid whose shape follows the aspect ratio.
        val target = rng.int(12..28)
        val cols = sqrt(target.toDouble() * w / h).roundToInt().coerceIn(1, target)
        val rows = ((target + cols - 1) / cols).coerceAtLeast(1)
        val cellW = w / cols
        val cellH = h / rows
        val jitter = rng.range(0.28f, 0.44f)

        val n = cols * rows
        val px = FloatArray(n)
        val py = FloatArray(n)
        var k = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                px[k] = (c + 0.5f) * cellW + rng.range(-jitter, jitter) * cellW
                py[k] = (r + 0.5f) * cellH + rng.range(-jitter, jitter) * cellH
                k++
            }
        }

        // Clip each cell from an oversized canvas rect by every perpendicular bisector.
        val margin = size * 0.05f
        val cells = ArrayList<List<FloatArray>?>(n)
        for (i in 0 until n) {
            var poly: List<FloatArray> = listOf(
                floatArrayOf(-margin, -margin),
                floatArrayOf(w + margin, -margin),
                floatArrayOf(w + margin, h + margin),
                floatArrayOf(-margin, h + margin)
            )
            for (j in 0 until n) {
                if (j == i) continue
                val nx = px[j] - px[i]
                val ny = py[j] - py[i]
                if (abs(nx) < 1e-4f && abs(ny) < 1e-4f) continue // coincident seeds
                poly = clipHalfPlane(poly, (px[i] + px[j]) * 0.5f, (py[i] + py[j]) * 0.5f, nx, ny)
                if (poly.size < 3) break
            }
            cells.add(if (poly.size >= 3) poly else null)
        }

        // Colour cells: primary-weighted, with occasional background cells for air.
        val fg = colors.drop(1)
        val weights = fgWeights(fg.size)
        val bgChance = rng.range(0.12, 0.26)
        val cellColor = IntArray(n) { if (rng.chance(bgChance)) -1 else pickIndex(rng, weights) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        paint.style = Paint.Style.FILL
        for (i in 0 until n) {
            val poly = cells[i] ?: continue
            val ci = cellColor[i]
            if (ci < 0) continue // background cell: the pre-filled canvas shows through
            toPath(poly, path)
            paint.color = fg[ci]
            canvas.drawPath(path, paint)
        }

        // Background-coloured grout over every edge, after all fills.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.008f
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = colors[0]
        for (i in 0 until n) {
            val poly = cells[i] ?: continue
            toPath(poly, path)
            canvas.drawPath(path, paint)
        }
    }
}

/** Jittered-grid triangulation with calm bg/primary fills and optional mortar lines. */
object TrianglesPattern : Pattern {
    override val id = "tris"
    override val label = "Triangles"
    override val colorRange = 3..5

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cols = rng.int(5..9)
        val rows = (cols * h / w).roundToInt().coerceIn(1, 64)
        val cellW = w / cols
        val cellH = h / rows
        val jitter = 0.25f

        // Vertex lattice: interiors jittered, border coordinates pinned to the canvas edge.
        val vx = Array(rows + 1) { FloatArray(cols + 1) }
        val vy = Array(rows + 1) { FloatArray(cols + 1) }
        for (r in 0..rows) {
            for (c in 0..cols) {
                var x = c * cellW
                var y = r * cellH
                if (c in 1 until cols) x += rng.range(-jitter, jitter) * cellW
                if (r in 1 until rows) y += rng.range(-jitter, jitter) * cellH
                vx[r][c] = x
                vy[r][c] = y
            }
        }

        val fg = colors.drop(1)
        val weights = fgWeights(fg.size)
        val bgChance = rng.range(0.30, 0.50)
        val mortar = rng.chance(0.65)

        // Split each quad along a per-quad random diagonal; colour each triangle.
        val triPts = ArrayList<FloatArray>(rows * cols * 2)
        val triColor = ArrayList<Int>(rows * cols * 2) // -1 = background
        fun addTri(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
            triPts.add(floatArrayOf(x0, y0, x1, y1, x2, y2))
            triColor.add(if (rng.chance(bgChance)) -1 else pickIndex(rng, weights))
        }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x00 = vx[r][c]; val y00 = vy[r][c]
                val x10 = vx[r][c + 1]; val y10 = vy[r][c + 1]
                val x01 = vx[r + 1][c]; val y01 = vy[r + 1][c]
                val x11 = vx[r + 1][c + 1]; val y11 = vy[r + 1][c + 1]
                if (rng.chance(0.5)) {
                    addTri(x00, y00, x10, y10, x11, y11)
                    addTri(x00, y00, x11, y11, x01, y01)
                } else {
                    addTri(x00, y00, x10, y10, x01, y01)
                    addTri(x10, y10, x11, y11, x01, y01)
                }
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.strokeJoin = Paint.Join.ROUND
        // Without mortar, a hairline same-colour stroke closes anti-aliased seams.
        paint.style = if (mortar) Paint.Style.FILL else Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = size * 0.0015f
        val path = Path()
        for (i in triPts.indices) {
            val ci = triColor[i]
            if (ci < 0) continue
            val t = triPts[i]
            path.reset()
            path.moveTo(t[0], t[1]); path.lineTo(t[2], t[3]); path.lineTo(t[4], t[5]); path.close()
            paint.color = fg[ci]
            canvas.drawPath(path, paint)
        }

        if (mortar) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * rng.range(0.003f, 0.005f)
            paint.color = colors[0]
            for (t in triPts) {
                path.reset()
                path.moveTo(t[0], t[1]); path.lineTo(t[2], t[3]); path.lineTo(t[4], t[5]); path.close()
                canvas.drawPath(path, paint)
            }
        }
    }
}

/** Pointy-top hex mosaic coloured by a smooth fbm field, with bg grout between cells. */
object HexPattern : Pattern {
    override val id = "hex"
    override val label = "Hex"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val radius = size * rng.range(0.06f, 0.11f)
        val hexW = sqrt(3f) * radius
        val vStep = 1.5f * radius
        val drawR = radius * 0.90f // shrink about centre so grout lines appear

        val noise = Noise(rng.fork())
        val fScale = rng.range(0.8, 1.6)
        val ox = rng.range(0.0, 64.0)
        val oy = rng.range(0.0, 64.0)
        val flip = if (rng.chance(0.5)) -1.0 else 1.0

        // Background-heavy weighting keeps the field airy; cumulative thresholds
        // quantise the smooth field into coherent colour patches.
        val weights = when (colors.size) {
            2 -> doubleArrayOf(0.52, 0.48)
            3 -> doubleArrayOf(0.42, 0.36, 0.22)
            else -> doubleArrayOf(0.38, 0.32, 0.19, 0.11)
        }
        val thresholds = DoubleArray(weights.size)
        var acc = 0.0
        for (i in weights.indices) {
            acc += weights[i]
            thresholds[i] = acc
        }
        val total = acc

        // Pointy-top corner directions (unit circle, 60-degree steps offset by 30).
        val ux = FloatArray(6)
        val uy = FloatArray(6)
        for (i in 0 until 6) {
            val ang = Math.toRadians(60.0 * i + 30.0)
            ux[i] = cos(ang).toFloat()
            uy[i] = sin(ang).toFloat()
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val path = Path()

        var cy = -radius
        var oddRow = false
        while (cy < h + radius) {
            val rowOffset = if (oddRow) hexW * 0.5f else 0f
            var cx = rowOffset - hexW
            while (cx < w + hexW) {
                val v = flip * noise.fbm(cx / size * fScale + ox, cy / size * fScale + oy, 4)
                val t = ((v * 1.15 + 1.0) * 0.5).coerceIn(0.0, 1.0) * total
                var idx = weights.size - 1
                for (i in thresholds.indices) {
                    if (t < thresholds[i]) {
                        idx = i
                        break
                    }
                }
                if (idx > 0) { // index 0 is the pre-filled background: skip
                    path.reset()
                    path.moveTo(cx + ux[0] * drawR, cy + uy[0] * drawR)
                    for (i in 1 until 6) path.lineTo(cx + ux[i] * drawR, cy + uy[i] * drawR)
                    path.close()
                    paint.color = colors[idx]
                    canvas.drawPath(path, paint)
                }
                cx += hexW
            }
            cy += vStep
            oddRow = !oddRow
        }
    }
}
