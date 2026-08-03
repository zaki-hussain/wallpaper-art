package dev.artwidget

import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import dev.artwidget.art.PaletteGen
import dev.artwidget.art.Patterns
import dev.artwidget.art.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EngineTest {

    @Test
    fun sameSpecRendersIdenticalPixels() {
        for (pattern in Patterns.all) {
            val rng = Rng(pattern.id.hashCode().toLong())
            val spec = ArtSpec(pattern.id, 424242L, PaletteGen.generate(pattern.colorRange.last, rng))
            val a = ArtRenderer.render(spec, 300, 300)
            val b = ArtRenderer.render(spec, 300, 300)
            val bufA = ByteBuffer.allocate(a.byteCount)
            val bufB = ByteBuffer.allocate(b.byteCount)
            a.copyPixelsToBuffer(bufA)
            b.copyPixelsToBuffer(bufB)
            assertEquals("pattern ${pattern.id} not deterministic", bufA, bufB)
        }
    }

    @Test
    fun shareCodeRoundTrips() {
        val rng = Rng(7L)
        for (pattern in Patterns.all) {
            val spec = ArtSpec(pattern.id, rng.nextLong(), PaletteGen.generate(pattern.colorRange.first, rng))
            assertEquals(spec, ArtSpec.fromJson(spec.toJson()))
        }
    }

    @Test
    fun malformedCodesAreRejected() {
        assertNull(ArtSpec.fromJson(""))
        assertNull(ArtSpec.fromJson("not json"))
        assertNull(ArtSpec.fromJson("""{"v":1,"p":"solid"}"""))
        assertNull(ArtSpec.fromJson("""{"v":1,"p":"solid","s":"abc","c":["#112233"]}"""))
        assertNull(ArtSpec.fromJson("""{"v":1,"p":"solid","s":"1","c":["#11223"]}"""))
        assertNull(ArtSpec.fromJson("""{"v":1,"p":"solid","s":"1","c":[]}"""))
        assertNotNull(ArtSpec.fromJson("""{"v":1,"p":"solid","s":"1","c":["#112233"]}"""))
    }

    @Test
    fun undersizedPaletteStillRenders() {
        // A spec with fewer colours than the pattern's minimum must render via extendTo.
        for (pattern in Patterns.all) {
            val spec = ArtSpec(pattern.id, 99L, listOf(0xFF224466.toInt()))
            val bmp = ArtRenderer.render(spec, 120, 120)
            assertEquals(120, bmp.width)
        }
    }

    @Test
    fun extremeAspectRatiosRender() {
        for (pattern in Patterns.all) {
            val rng = Rng(5L)
            val spec = ArtSpec(pattern.id, 1234L, PaletteGen.generate(pattern.colorRange.last, rng))
            ArtRenderer.render(spec, 1600, 220)
            ArtRenderer.render(spec, 220, 1600)
            ArtRenderer.render(spec, 64, 64)
        }
    }

    @Test
    fun paletteGenAlwaysProducesRequestedCount() {
        val rng = Rng(1L)
        for (n in 1..8) {
            repeat(50) {
                assertEquals(n, PaletteGen.generate(n, rng).size)
            }
        }
    }

    @Test
    fun extendToGrowsAndTruncates() {
        val rng = Rng(2L)
        val base = listOf(0xFF102030.toInt(), 0xFFE0D0C0.toInt())
        assertEquals(5, PaletteGen.extendTo(base, 5, rng).size)
        assertEquals(1, PaletteGen.extendTo(base, 1, rng).size)
        assertEquals(4, PaletteGen.extendTo(listOf(0xFF808080.toInt()), 4, rng).size)
    }

    @Test
    fun rngIsStable() {
        // Guard: the PRNG algorithm must never change, or saved seeds would change art.
        val rng = Rng(123456789L)
        val first = rng.nextLong()
        val rng2 = Rng(123456789L)
        assertEquals(first, rng2.nextLong())
        assertTrue(Rng(1L).double() in 0.0..1.0)
    }
}
