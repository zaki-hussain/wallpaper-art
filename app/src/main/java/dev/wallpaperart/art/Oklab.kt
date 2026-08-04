package dev.wallpaperart.art

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

/**
 * OKLCH <-> sRGB conversion (Björn Ottosson's OKLab). Palettes are designed in OKLCH
 * because equal steps there look perceptually even, which keeps generated colours tasteful.
 */
object Oklab {

    /** L in [0,1], C >= 0 (~0..0.3 usable), H in degrees. Out-of-gamut colours get chroma reduced. */
    fun oklchToArgb(l: Double, c: Double, hDeg: Double): Int {
        var rgb = oklabToRgbAt(l, c, hDeg)
        if (!inGamut(rgb)) {
            var lo = 0.0
            var hi = c
            repeat(24) {
                val mid = (lo + hi) / 2
                if (inGamut(oklabToRgbAt(l, mid, hDeg))) lo = mid else hi = mid
            }
            rgb = oklabToRgbAt(l, lo, hDeg)
        }
        val r = (clamp01(rgb[0]) * 255.0 + 0.5).toInt()
        val g = (clamp01(rgb[1]) * 255.0 + 0.5).toInt()
        val b = (clamp01(rgb[2]) * 255.0 + 0.5).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun oklabToRgbAt(l: Double, c: Double, hDeg: Double): DoubleArray {
        val hRad = Math.toRadians(hDeg)
        val a = c * cos(hRad)
        val b = c * sin(hRad)
        val l1 = l + 0.3963377774 * a + 0.2158037573 * b
        val m1 = l - 0.1055613458 * a - 0.0638541728 * b
        val s1 = l - 0.0894841775 * a - 1.2914855480 * b
        val l3 = l1 * l1 * l1
        val m3 = m1 * m1 * m1
        val s3 = s1 * s1 * s1
        val rLin = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
        val gLin = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
        val bLin = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
        return doubleArrayOf(linearToSrgb(rLin), linearToSrgb(gLin), linearToSrgb(bLin))
    }

    /** Returns [l, c, hDeg] for an ARGB colour. */
    fun argbToOklch(argb: Int): DoubleArray {
        val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = srgbToLinear((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val lab0 = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val lab1 = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val lab2 = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        val c = sqrt(lab1 * lab1 + lab2 * lab2)
        var h = Math.toDegrees(atan2(lab2, lab1))
        if (h < 0) h += 360.0
        return doubleArrayOf(lab0, c, h)
    }

    /** Perceptual blend of two ARGB colours through OKLab (alpha lerps linearly). */
    fun lerp(c1: Int, c2: Int, t: Double): Int {
        val lch1 = argbToOklch(c1)
        val lch2 = argbToOklch(c2)
        val h1 = Math.toRadians(lch1[2])
        val h2 = Math.toRadians(lch2[2])
        val a = lch1[1] * cos(h1) + t * (lch2[1] * cos(h2) - lch1[1] * cos(h1))
        val b = lch1[1] * sin(h1) + t * (lch2[1] * sin(h2) - lch1[1] * sin(h1))
        val l = lch1[0] + t * (lch2[0] - lch1[0])
        val c = sqrt(a * a + b * b)
        var h = Math.toDegrees(atan2(b, a))
        if (h < 0) h += 360.0
        val a1 = (c1 ushr 24) and 0xFF
        val a2 = (c2 ushr 24) and 0xFF
        val alpha = (a1 + t * (a2 - a1)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (oklchToArgb(l, c, h) and 0xFFFFFF)
    }

    /**
     * Expands gradient stops into many OKLab-interpolated sub-stops, so shader gradients
     * blend perceptually instead of through muddy raw RGB.
     */
    fun smoothStops(colors: List<Int>, positions: FloatArray, perSegment: Int = 8): Pair<IntArray, FloatArray> {
        val outC = ArrayList<Int>()
        val outP = ArrayList<Float>()
        for (seg in 0 until colors.size - 1) {
            for (k in 0 until perSegment) {
                val t = k / perSegment.toDouble()
                outC.add(lerp(colors[seg], colors[seg + 1], t))
                outP.add(positions[seg] + (positions[seg + 1] - positions[seg]) * t.toFloat())
            }
        }
        outC.add(colors.last())
        outP.add(positions.last())
        return outC.toIntArray() to outP.toFloatArray()
    }

    private fun inGamut(rgb: DoubleArray): Boolean {
        val eps = 1e-4
        return rgb.all { it > -eps && it < 1 + eps }
    }

    private fun linearToSrgb(x: Double): Double {
        val v = x.coerceIn(-1.0, 2.0)
        return if (abs(v) <= 0.0031308) 12.92 * v
        else (if (v < 0) -1 else 1) * (1.055 * abs(v).pow(1.0 / 2.4) - 0.055)
    }

    private fun srgbToLinear(x: Double): Double =
        if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)

    private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
}
