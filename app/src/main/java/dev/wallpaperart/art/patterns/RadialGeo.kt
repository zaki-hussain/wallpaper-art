package dev.wallpaperart.art.patterns

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.wallpaperart.art.Pattern
import dev.wallpaperart.art.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private fun fgWeights(count: Int): DoubleArray = when (count) {
    1 -> doubleArrayOf(1.0)
    2 -> doubleArrayOf(0.72, 0.28)
    else -> doubleArrayOf(0.62, 0.26, 0.12)
}

private fun pickWeighted(rng: Rng, weights: DoubleArray): Int {
    val roll = rng.double()
    var acc = 0.0
    for (i in weights.indices) {
        acc += weights[i]
        if (roll < acc) return i
    }
    return weights.size - 1
}

private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/** Farthest distance from (ax, ay) to any corner of the w x h rect. Works for outside anchors. */
private fun farthestCornerDist(ax: Float, ay: Float, w: Float, h: Float): Float {
    val dx = max(abs(ax), abs(ax - w))
    val dy = max(abs(ay), abs(ay - h))
    return sqrt(dx * dx + dy * dy)
}

/** Concentric filled annuli around a centre, edge, or corner anchor. */
object RingsPattern : Pattern {
    override val id = "rings"
    override val label = "Rings"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val fg = colors.drop(1)

        // Anchor: mostly an edge point or corner (cropped rings read more elegant than
        // a dead-centre bullseye), occasionally centre-ish.
        val anchor = when {
            rng.chance(0.25) -> floatArrayOf(w * rng.range(0.4f, 0.6f), h * rng.range(0.4f, 0.6f))
            rng.chance(0.55) -> when (rng.int(4)) {
                0 -> floatArrayOf(w * rng.range(0.25f, 0.75f), 0f)
                1 -> floatArrayOf(w * rng.range(0.25f, 0.75f), h)
                2 -> floatArrayOf(0f, h * rng.range(0.25f, 0.75f))
                else -> floatArrayOf(w, h * rng.range(0.25f, 0.75f))
            }
            else -> floatArrayOf(if (rng.chance(0.5)) 0f else w, if (rng.chance(0.5)) 0f else h)
        }
        val maxR = farthestCornerDist(anchor[0], anchor[1], w, h)

        // Coloured rings stay slim; the background gaps between them run wider, so the
        // composition breathes instead of reading as a bullseye.
        val rhythmLen = rng.int(2..3)
        val colouredRhythm = FloatArray(rhythmLen) { size * rng.range(0.02f, 0.065f) }
        val gapRhythm = FloatArray(rhythmLen) { size * rng.range(0.045f, 0.14f) }

        // Cumulative annuli inside out — coloured ring, then background gap — until the
        // canvas is covered. Coloured rings never abut, keeping every edge clean.
        val ringBounds = ArrayList<FloatArray>()
        var r = if (rng.chance(0.5)) 0f else size * rng.range(0.03f, 0.1f)
        var k = 0
        while (r < maxR) {
            val outer = r + colouredRhythm[k % rhythmLen]
            ringBounds.add(floatArrayOf(r, outer))
            r = outer + gapRhythm[k % rhythmLen]
            k++
        }

        val cycle = when (fg.size) {
            1 -> listOf(fg[0])
            2 -> listOf(fg[0], fg[0], fg[1])
            else -> listOf(fg[0], fg[1], fg[0], fg[2])
        }
        val cycleStart = rng.int(cycle.size)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (j in ringBounds.indices) {
            val inner = ringBounds[j][0]
            val outer = ringBounds[j][1]
            paint.color = cycle[(j + cycleStart) % cycle.size]
            if (inner <= 0f) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(anchor[0], anchor[1], outer, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = outer - inner
                canvas.drawCircle(anchor[0], anchor[1], (inner + outer) * 0.5f, paint)
            }
        }
    }
}

/** Wedges radiating from a corner, edge point, or off-canvas anchor. */
object RaysPattern : Pattern {
    override val id = "rays"
    override val label = "Rays"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val fg = colors.drop(1)
        val weights = fgWeights(fg.size)

        // Anchor: a corner, a point on an edge, or pushed just outside the canvas.
        val edgePoint: FloatArray = when (rng.int(4)) {
            0 -> floatArrayOf(w * rng.range(0.15f, 0.85f), 0f)
            1 -> floatArrayOf(w * rng.range(0.15f, 0.85f), h)
            2 -> floatArrayOf(0f, h * rng.range(0.15f, 0.85f))
            else -> floatArrayOf(w, h * rng.range(0.15f, 0.85f))
        }
        val anchor = when (rng.int(3)) {
            0 -> floatArrayOf(if (rng.chance(0.5)) 0f else w, if (rng.chance(0.5)) 0f else h)
            1 -> edgePoint
            else -> {
                val push = size * rng.range(0.08f, 0.3f)
                floatArrayOf(
                    edgePoint[0] + (if (edgePoint[0] <= 0f) -push else if (edgePoint[0] >= w) push else 0f),
                    edgePoint[1] + (if (edgePoint[1] <= 0f) -push else if (edgePoint[1] >= h) push else 0f)
                )
            }
        }
        val reach = farthestCornerDist(anchor[0], anchor[1], w, h) * 1.6f

        val sparse = rng.chance(0.35)
        // Dense mode alternates colour/bg, so keep the count even; both stay in 16..40.
        val n = if (sparse) rng.int(16..28) else 2 * rng.int(8..20)

        // Angular slots with gently varied widths (about +-30%), normalised to the full circle.
        val slots = DoubleArray(n) { rng.range(0.7, 1.3) }
        var total = 0.0
        for (s in slots) total += s
        val scale = 2.0 * Math.PI / total
        for (i in slots.indices) slots[i] *= scale

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        val path = Path()

        var a = rng.range(0.0, 2.0 * Math.PI)
        for (i in 0 until n) {
            val slot = slots[i]
            var a0 = a
            var a1 = a + slot
            a = a1
            val coloured: Boolean
            if (sparse) {
                // Thin rays centred in their slot, some slots left empty.
                coloured = rng.chance(0.6)
                val ray = slot * rng.range(0.18, 0.4)
                val mid = (a0 + a1) * 0.5
                a0 = mid - ray * 0.5
                a1 = mid + ray * 0.5
            } else {
                coloured = i % 2 == 0
            }
            if (!coloured) continue
            paint.color = fg[pickWeighted(rng, weights)]
            path.reset()
            path.moveTo(anchor[0], anchor[1])
            path.lineTo(anchor[0] + (cos(a0) * reach).toFloat(), anchor[1] + (sin(a0) * reach).toFloat())
            path.lineTo(anchor[0] + (cos(a1) * reach).toFloat(), anchor[1] + (sin(a1) * reach).toFloat())
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}

/** A quiet solar-system diagram: thin concentric orbits with a few dots. */
object OrbitsPattern : Pattern {
    override val id = "orbits"
    override val label = "Orbits"
    override val colorRange = 2..4

    override fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng) {
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val bg = colors[0]
        val fg = colors.drop(1)
        val primary = fg[0]

        // Anchor: centre-ish or at a golden-ratio offset.
        val gx = if (rng.chance(0.5)) 0.382f else 0.618f
        val gy = if (rng.chance(0.5)) 0.382f else 0.618f
        val ax: Float
        val ay: Float
        if (rng.chance(0.5)) {
            ax = w * rng.range(0.46f, 0.54f)
            ay = h * rng.range(0.46f, 0.54f)
        } else {
            ax = w * (gx + rng.range(-0.02f, 0.02f))
            ay = h * (gy + rng.range(-0.02f, 0.02f))
        }

        val n = rng.int(4..9)
        val rMin = size * rng.range(0.08f, 0.16f)
        val rMax = size * rng.range(0.45f, 0.8f)
        val radii = FloatArray(n) { i ->
            rMin * (rMax / rMin).pow(i.toFloat() / (n - 1).toFloat())
        }

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = size * rng.range(0.0022f, 0.004f)
        for (r in radii) {
            val alpha = if (rng.chance(0.55)) 255 else rng.int(90..175)
            stroke.color = withAlpha(primary, alpha)
            canvas.drawCircle(ax, ay, r, stroke)
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.style = Paint.Style.FILL

        // A few dots, each on its own orbit; accents preferred when available.
        val dotCount = rng.int(2..min(5, n))
        val orbitIdx = rng.shuffled(radii.indices.toList()).take(dotCount)
        val planetSlot = if (rng.chance(0.6)) rng.int(dotCount) else -1
        for ((slot, oi) in orbitIdx.withIndex()) {
            val r = radii[oi]
            var angle = rng.range(0.0, 2.0 * Math.PI)
            var px = ax + (cos(angle) * r).toFloat()
            var py = ay + (sin(angle) * r).toFloat()
            // Nudge the dot onto the visible canvas when possible (bounded retries).
            var tries = 0
            val margin = size * 0.05f
            while (tries < 8 && (px < margin || px > w - margin || py < margin || py > h - margin)) {
                angle = rng.range(0.0, 2.0 * Math.PI)
                px = ax + (cos(angle) * r).toFloat()
                py = ay + (sin(angle) * r).toFloat()
                tries++
            }
            val dotR = if (slot == planetSlot) {
                size * rng.range(0.035f, 0.05f)
            } else {
                size * rng.range(0.008f, 0.03f)
            }
            val colour = if (fg.size >= 2 && rng.chance(0.75)) {
                fg[1 + pickWeighted(rng, if (fg.size == 2) doubleArrayOf(1.0) else doubleArrayOf(0.68, 0.32))]
            } else {
                primary
            }
            if (dotR > size * 0.02f) {
                // A background halo lets the larger dots float on their orbit line.
                fill.color = bg
                canvas.drawCircle(px, py, dotR * 1.55f, fill)
            }
            fill.color = colour
            canvas.drawCircle(px, py, dotR, fill)
        }

        // Optional small filled circle at the anchor.
        if (rng.chance(0.55)) {
            fill.color = primary
            canvas.drawCircle(ax, ay, size * rng.range(0.006f, 0.014f), fill)
        }
    }
}
