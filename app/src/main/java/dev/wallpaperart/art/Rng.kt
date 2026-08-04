package dev.wallpaperart.art

/**
 * Deterministic PRNG (xoshiro256** seeded via splitmix64). The algorithm is fixed here
 * so a saved seed reproduces identical art forever, independent of platform or runtime.
 */
class Rng(seed: Long) {
    private var s0: Long
    private var s1: Long
    private var s2: Long
    private var s3: Long
    private var spareGaussian: Double? = null

    init {
        var x = seed
        fun splitmix(): Long {
            x += -0x61c8864680b583ebL
            var z = x
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
        s0 = splitmix(); s1 = splitmix(); s2 = splitmix(); s3 = splitmix()
    }

    fun nextLong(): Long {
        val result = java.lang.Long.rotateLeft(s1 * 5, 7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = java.lang.Long.rotateLeft(s3, 45)
        return result
    }

    /** Uniform in [0, 1). */
    fun double(): Double = (nextLong() ushr 11) * 1.1102230246251565E-16

    fun float(): Float = double().toFloat()

    /** Uniform in [a, b). */
    fun range(a: Float, b: Float): Float = a + (b - a) * float()

    fun range(a: Double, b: Double): Double = a + (b - a) * double()

    /** Uniform int in [0, bound); 0 when bound <= 0. */
    fun int(bound: Int): Int = if (bound <= 0) 0 else (double() * bound).toInt().coerceAtMost(bound - 1)

    fun int(range: IntRange): Int = range.first + int(range.last - range.first + 1)

    fun chance(p: Double): Boolean = double() < p

    fun sign(): Int = if (chance(0.5)) 1 else -1

    fun gaussian(): Double {
        spareGaussian?.let { spareGaussian = null; return it }
        var u: Double
        var v: Double
        var s: Double
        do {
            u = 2.0 * double() - 1.0
            v = 2.0 * double() - 1.0
            s = u * u + v * v
        } while (s >= 1.0 || s == 0.0)
        val m = kotlin.math.sqrt(-2.0 * kotlin.math.ln(s) / s)
        spareGaussian = v * m
        return u * m
    }

    fun <T> pick(list: List<T>): T = list[int(list.size)]

    fun <T> shuffled(list: List<T>): List<T> {
        val out = list.toMutableList()
        for (i in out.indices.reversed()) {
            val j = int(i + 1)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    /** Derives an independent stream; useful to keep sub-features decoupled. */
    fun fork(): Rng = Rng(nextLong())
}
