package dev.wallpaperart.art

import kotlin.math.floor

/** Seeded 2D Perlin gradient noise with fBm, deterministic per Rng seed. */
class Noise(rng: Rng) {
    private val perm = IntArray(512)

    init {
        val p = rng.shuffled((0..255).toList())
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)

    private fun grad(hash: Int, x: Double, y: Double): Double {
        return when (hash and 7) {
            0 -> x + y
            1 -> x - y
            2 -> -x + y
            3 -> -x - y
            4 -> x
            5 -> -x
            6 -> y
            else -> -y
        }
    }

    /** Perlin noise, roughly in [-1, 1]. */
    fun noise(x: Double, y: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val u = fade(xf)
        val v = fade(yf)
        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]
        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)
        return lerp(x1, x2, v)
    }

    /** Fractal Brownian motion, roughly in [-1, 1]. */
    fun fbm(x: Double, y: Double, octaves: Int = 4, lacunarity: Double = 2.0, gain: Double = 0.5): Double {
        var sum = 0.0
        var amp = 1.0
        var norm = 0.0
        var fx = x
        var fy = y
        repeat(octaves) {
            sum += amp * noise(fx, fy)
            norm += amp
            amp *= gain
            fx *= lacunarity
            fy *= lacunarity
        }
        return sum / norm
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)
}
