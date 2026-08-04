package dev.wallpaperart.art

import android.graphics.Canvas

/**
 * A procedural pattern. Implementations must:
 *  - draw only with the supplied colours (alpha, blends, or small lightness
 *    adjustments derived from them are fine);
 *  - treat colors[0] as the background (the canvas is pre-filled with it) and later
 *    indices as progressively more sparing accents;
 *  - derive every dimension from w/h (no absolute pixel sizes) so art scales cleanly;
 *  - use only the supplied rng so a seed reproduces the same art;
 *  - cover the full canvas at any aspect ratio.
 */
interface Pattern {
    val id: String
    val label: String

    /** How many colour slots the pattern can use, including the background at index 0. */
    val colorRange: IntRange

    fun draw(canvas: Canvas, w: Float, h: Float, colors: List<Int>, rng: Rng)
}
