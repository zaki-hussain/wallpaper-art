package dev.artwidget

import android.graphics.Bitmap
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import dev.artwidget.art.PaletteGen
import dev.artwidget.art.Patterns
import dev.artwidget.art.Rng
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders every pattern at several seeds and sizes into build/renders/ so the output can
 * be inspected by eye. Doubles as a smoke test that no pattern crashes at any aspect ratio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderGalleryTest {

    @Test
    fun renderGallery() {
        val outDir = File(System.getProperty("artwidget.renders") ?: "build/renders")
        outDir.mkdirs()
        val seeds = longArrayOf(11L, 22L, 33L)
        for (pattern in Patterns.all) {
            seeds.forEachIndexed { i, seed ->
                val rng = Rng(seed * 7919 + pattern.id.hashCode())
                val n = rng.int(pattern.colorRange)
                val spec = ArtSpec(pattern.id, rng.nextLong(), PaletteGen.generate(n, rng))
                save(ArtRenderer.render(spec, 800, 800), File(outDir, "${pattern.id}_$i.png"))
                if (i == 0) {
                    save(ArtRenderer.render(spec, 1200, 500), File(outDir, "${pattern.id}_wide.png"))
                    save(ArtRenderer.render(spec, 500, 1200), File(outDir, "${pattern.id}_tall.png"))
                }
            }
        }
    }

    @Test
    fun renderDivideGallery() {
        val outDir = File(System.getProperty("artwidget.renders") ?: "build/renders")
        outDir.mkdirs()
        val rng = Rng(5L)
        val spec = ArtSpec(
            dev.artwidget.art.patterns.BauhausPattern.id,
            rng.nextLong(),
            PaletteGen.generate(5, rng, dark = false)
        )
        for (style in Wallpaper.STYLES) {
            val art = ArtRenderer.render(spec, 540, 1200)
            save(Wallpaper.compose(art, spec.colors[0], 30, style, spec.seed), File(outDir, "divide_$style.png"))
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
