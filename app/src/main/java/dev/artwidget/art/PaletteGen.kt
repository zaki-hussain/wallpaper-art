package dev.artwidget.art

/**
 * Generates palettes in OKLCH using colour-harmony strategies, so the user never has to
 * think about colour theory. Convention: index 0 is the background, indices 1..n-1 are
 * foreground colours ordered by prominence (the last one is the accent, used sparingly).
 * Every foreground is pushed to a minimum lightness contrast against the background.
 */
object PaletteGen {

    private const val MIN_CONTRAST = 0.16 // minimum |ΔL| in OKLab between bg and any fg

    fun generate(n: Int, rng: Rng): List<Int> {
        require(n >= 1)
        val strategy = rng.int(8)
        val palette = when (strategy) {
            0 -> tonal(n, rng)
            1 -> analogous(n, rng)
            2 -> complement(n, rng)
            3 -> pastel(n, rng)
            4 -> earthy(n, rng)
            5 -> neutralPop(n, rng)
            6 -> darkLuminous(n, rng)
            else -> duotone(n, rng)
        }
        return enforceContrast(palette)
    }

    /** One hue, varied lightness. Works at any colour count. */
    private fun tonal(n: Int, rng: Rng): List<Lch> {
        val h = rng.range(0.0, 360.0)
        val dark = rng.chance(0.4)
        val bg = if (dark) Lch(rng.range(0.18, 0.24), rng.range(0.01, 0.04), h)
        else Lch(rng.range(0.93, 0.97), rng.range(0.008, 0.03), h)
        val fgs = (1 until n).map { i ->
            val t = if (n <= 2) 0.5 else (i - 1) / (n - 2.0).coerceAtLeast(1.0)
            val l = if (dark) 0.5 + 0.38 * t else 0.72 - 0.4 * t
            Lch(l + rng.range(-0.03, 0.03), rng.range(0.06, 0.14), h + rng.range(-8.0, 8.0))
        }
        return listOf(bg) + fgs
    }

    /** Neighbouring hues, soft and cohesive. */
    private fun analogous(n: Int, rng: Rng): List<Lch> {
        val base = rng.range(0.0, 360.0)
        val spread = rng.range(28.0, 55.0)
        val dark = rng.chance(0.35)
        val bg = if (dark) Lch(rng.range(0.17, 0.23), rng.range(0.015, 0.045), base)
        else Lch(rng.range(0.93, 0.97), rng.range(0.01, 0.035), base)
        val fgs = (1 until n).map { i ->
            val off = spread * ((i - 1) - (n - 2) / 2.0) / ((n - 1).coerceAtLeast(1) / 2.0).coerceAtLeast(1.0)
            val l = if (dark) rng.range(0.55, 0.82) else rng.range(0.4, 0.68)
            Lch(l, rng.range(0.08, 0.15), base + off + rng.range(-6.0, 6.0))
        }
        return listOf(bg) + fgs
    }

    /** Mostly one hue family with an opposite-hue accent in the last slot. */
    private fun complement(n: Int, rng: Rng): List<Lch> {
        val base = rng.range(0.0, 360.0)
        val dark = rng.chance(0.3)
        val bg = if (dark) Lch(rng.range(0.17, 0.23), rng.range(0.01, 0.04), base)
        else Lch(rng.range(0.93, 0.97), rng.range(0.008, 0.03), base)
        val fgs = (1 until n).map { i ->
            val isAccent = i == n - 1 && n >= 2
            val h = if (isAccent) base + 180.0 + rng.range(-15.0, 15.0) else base + rng.range(-18.0, 18.0)
            val l = if (dark) rng.range(0.55, 0.8) else rng.range(0.38, 0.65)
            val c = if (isAccent) rng.range(0.12, 0.19) else rng.range(0.06, 0.12)
            Lch(l, c, h)
        }
        return listOf(bg) + fgs
    }

    /** Light, airy, multi-hue softness. */
    private fun pastel(n: Int, rng: Rng): List<Lch> {
        val base = rng.range(0.0, 360.0)
        val step = rng.range(45.0, 110.0)
        val bg = Lch(rng.range(0.95, 0.975), rng.range(0.005, 0.02), base)
        val fgs = (1 until n).map { i ->
            Lch(rng.range(0.78, 0.87), rng.range(0.05, 0.1), base + step * i + rng.range(-12.0, 12.0))
        }
        return listOf(bg) + fgs
    }

    /** Muted, warm, natural tones. */
    private fun earthy(n: Int, rng: Rng): List<Lch> {
        val hues = listOf(30.0, 55.0, 80.0, 110.0, 150.0, 250.0)
        val darkBg = rng.chance(0.3)
        val bg = if (darkBg) Lch(rng.range(0.22, 0.28), rng.range(0.015, 0.04), rng.range(40.0, 90.0))
        else Lch(rng.range(0.92, 0.96), rng.range(0.01, 0.03), rng.range(60.0, 100.0))
        val picked = rng.shuffled(hues)
        val fgs = (1 until n).map { i ->
            val l = if (darkBg) rng.range(0.55, 0.78) else rng.range(0.42, 0.68)
            Lch(l, rng.range(0.04, 0.09), picked[(i - 1) % picked.size] + rng.range(-10.0, 10.0))
        }
        return listOf(bg) + fgs
    }

    /** Neutral greys plus one vivid accent. */
    private fun neutralPop(n: Int, rng: Rng): List<Lch> {
        val accentH = rng.range(0.0, 360.0)
        val dark = rng.chance(0.45)
        val bg = if (dark) Lch(rng.range(0.16, 0.22), rng.range(0.0, 0.012), accentH)
        else Lch(rng.range(0.94, 0.97), rng.range(0.0, 0.01), accentH)
        val fgs = (1 until n).map { i ->
            if (i == n - 1) Lch(rng.range(0.55, 0.7), rng.range(0.16, 0.23), accentH)
            else {
                val l = if (dark) rng.range(0.45, 0.75) else rng.range(0.35, 0.7)
                Lch(l, rng.range(0.0, 0.02), accentH)
            }
        }
        return listOf(bg) + fgs
    }

    /** Near-black background with glowing colours. */
    private fun darkLuminous(n: Int, rng: Rng): List<Lch> {
        val base = rng.range(0.0, 360.0)
        val bg = Lch(rng.range(0.13, 0.19), rng.range(0.01, 0.04), base)
        val spread = rng.range(30.0, 90.0)
        val fgs = (1 until n).map { i ->
            Lch(rng.range(0.62, 0.85), rng.range(0.09, 0.18), base + spread * (i - 1) / (n - 1).coerceAtLeast(1) + rng.range(-10.0, 10.0))
        }
        return listOf(bg) + fgs
    }

    /** Two hue families, tints alternating between them. */
    private fun duotone(n: Int, rng: Rng): List<Lch> {
        val h1 = rng.range(0.0, 360.0)
        val h2 = h1 + rng.range(70.0, 180.0) * rng.sign()
        val dark = rng.chance(0.35)
        val bg = if (dark) Lch(rng.range(0.17, 0.23), rng.range(0.02, 0.05), h1)
        else Lch(rng.range(0.93, 0.97), rng.range(0.01, 0.035), h1)
        val fgs = (1 until n).map { i ->
            val h = if (i % 2 == 1) h1 else h2
            val l = if (dark) rng.range(0.55, 0.82) else rng.range(0.42, 0.7)
            Lch(l, rng.range(0.08, 0.15), h + rng.range(-8.0, 8.0))
        }
        return listOf(bg) + fgs
    }

    /**
     * Deterministically derives extra colours from an existing palette so a spec whose
     * pattern needs more slots than it has colours still renders (e.g. after a pattern
     * switch that keeps the colours, or a hand-edited import).
     */
    fun extendTo(colors: List<Int>, n: Int, rng: Rng): List<Int> {
        if (colors.size >= n) return colors.take(n)
        val out = colors.toMutableList()
        while (out.size < n) {
            val src = out[if (out.size == 1) 0 else 1 + (out.size - 1) % (out.size - 1)]
            val (l, c, h) = Oklab.argbToOklch(src).let { Triple(it[0], it[1], it[2]) }
            val newL = when {
                out.size == 1 && l > 0.5 -> l - rng.range(0.35, 0.5)   // derive fg from light bg
                out.size == 1 -> l + rng.range(0.35, 0.5)              // derive fg from dark bg
                else -> (l + rng.range(-0.14, 0.14)).coerceIn(0.25, 0.88)
            }
            out.add(Oklab.oklchToArgb(newL, (c + rng.range(-0.02, 0.03)).coerceIn(0.0, 0.25), h + rng.range(-18.0, 18.0)))
        }
        return out
    }

    private fun enforceContrast(palette: List<Lch>): List<Int> {
        val bg = palette[0]
        val fixed = palette.mapIndexed { i, col ->
            if (i == 0) col
            else {
                val dl = col.l - bg.l
                if (kotlin.math.abs(dl) < MIN_CONTRAST) {
                    val dir = if (bg.l > 0.5) -1.0 else 1.0
                    col.copy(l = (bg.l + dir * MIN_CONTRAST).coerceIn(0.08, 0.95))
                } else col
            }
        }
        return fixed.map { Oklab.oklchToArgb(it.l, it.c, ((it.h % 360.0) + 360.0) % 360.0) }
    }

    private data class Lch(val l: Double, val c: Double, val h: Double)
}
