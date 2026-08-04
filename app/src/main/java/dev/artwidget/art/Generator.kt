package dev.artwidget.art

import java.util.concurrent.ThreadLocalRandom

/** Creates new specs and the two "switch up" operations (new pattern / new colours). */
object Generator {

    private fun entropy(): Rng = Rng(ThreadLocalRandom.current().nextLong())

    /** [dark] biases the palette background to the system theme; null = don't care. */
    fun newArt(enabled: List<Pattern>, dark: Boolean? = null): ArtSpec {
        val rng = entropy()
        val pattern = rng.pick(enabled.ifEmpty { Patterns.all })
        val count = rng.int(pattern.colorRange)
        return ArtSpec(pattern.id, rng.nextLong(), PaletteGen.generate(count, rng, dark))
    }

    /** New pattern and geometry, keeping the current colours (re-fitted to the new pattern). */
    fun switchPattern(spec: ArtSpec, enabled: List<Pattern>): ArtSpec {
        val rng = entropy()
        val pool = enabled.ifEmpty { Patterns.all }
        val others = pool.filter { it.id != spec.pattern }.ifEmpty { pool }
        val pattern = rng.pick(others)
        var colors = spec.colors
        if (colors.size < pattern.colorRange.first) colors = PaletteGen.extendTo(colors, pattern.colorRange.first, rng)
        if (colors.size > pattern.colorRange.last) colors = colors.take(pattern.colorRange.last)
        return ArtSpec(pattern.id, rng.nextLong(), colors)
    }

    /** Fresh colours for the same pattern and geometry. */
    fun switchColors(spec: ArtSpec, dark: Boolean? = null): ArtSpec =
        spec.copy(colors = PaletteGen.generate(spec.colors.size, entropy(), dark))

    /** The refresh step: randomises everything except the aspects the user locked. */
    fun nextArt(
        current: ArtSpec,
        enabled: List<Pattern>,
        dark: Boolean? = null,
        keepPattern: Boolean = false,
        keepColors: Boolean = false
    ): ArtSpec {
        val rng = entropy()
        return when {
            keepPattern && keepColors -> current.copy(seed = rng.nextLong())
            keepPattern -> {
                val pattern = Patterns.byId(current.pattern) ?: return newArt(enabled, dark)
                ArtSpec(pattern.id, rng.nextLong(), PaletteGen.generate(rng.int(pattern.colorRange), rng, dark))
            }
            keepColors -> switchPattern(current, enabled)
            else -> newArt(enabled, dark)
        }
    }
}
