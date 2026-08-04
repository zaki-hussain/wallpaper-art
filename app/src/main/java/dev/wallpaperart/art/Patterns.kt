package dev.wallpaperart.art

import dev.wallpaperart.art.patterns.ArchesPattern
import dev.wallpaperart.art.patterns.AuroraPattern
import dev.wallpaperart.art.patterns.BauhausPattern
import dev.wallpaperart.art.patterns.BlobsPattern
import dev.wallpaperart.art.patterns.BokehPattern
import dev.wallpaperart.art.patterns.ChevronPattern
import dev.wallpaperart.art.patterns.ConfettiPattern
import dev.wallpaperart.art.patterns.ContoursPattern
import dev.wallpaperart.art.patterns.CrossesPattern
import dev.wallpaperart.art.patterns.CubesPattern
import dev.wallpaperart.art.patterns.DiamondsPattern
import dev.wallpaperart.art.patterns.DotsPattern
import dev.wallpaperart.art.patterns.FlowPattern
import dev.wallpaperart.art.patterns.GradientPattern
import dev.wallpaperart.art.patterns.HalftonePattern
import dev.wallpaperart.art.patterns.HexPattern
import dev.wallpaperart.art.patterns.HillsPattern
import dev.wallpaperart.art.patterns.MeshPattern
import dev.wallpaperart.art.patterns.MondrianPattern
import dev.wallpaperart.art.patterns.NestedPattern
import dev.wallpaperart.art.patterns.OrbitsPattern
import dev.wallpaperart.art.patterns.PetalsPattern
import dev.wallpaperart.art.patterns.PillsPattern
import dev.wallpaperart.art.patterns.RadialPattern
import dev.wallpaperart.art.patterns.RaysPattern
import dev.wallpaperart.art.patterns.RidgesPattern
import dev.wallpaperart.art.patterns.RingsPattern
import dev.wallpaperart.art.patterns.ScallopsPattern
import dev.wallpaperart.art.patterns.SolidPattern
import dev.wallpaperart.art.patterns.SpiralPattern
import dev.wallpaperart.art.patterns.StepsPattern
import dev.wallpaperart.art.patterns.StripesPattern
import dev.wallpaperart.art.patterns.TerrazzoPattern
import dev.wallpaperart.art.patterns.TrianglesPattern
import dev.wallpaperart.art.patterns.TruchetPattern
import dev.wallpaperart.art.patterns.VoronoiPattern
import dev.wallpaperart.art.patterns.WavesPattern
import dev.wallpaperart.art.patterns.WeavePattern

/** Registry of every available pattern. */
object Patterns {
    val all: List<Pattern> = listOf(
        SolidPattern,
        GradientPattern,
        RadialPattern,
        MeshPattern,
        FlowPattern,
        ContoursPattern,
        WavesPattern,
        RidgesPattern,
        BlobsPattern,
        DotsPattern,
        HalftonePattern,
        TerrazzoPattern,
        ScallopsPattern,
        StripesPattern,
        RingsPattern,
        RaysPattern,
        OrbitsPattern,
        VoronoiPattern,
        TrianglesPattern,
        HexPattern,
        TruchetPattern,
        BauhausPattern,
        MondrianPattern,
        ArchesPattern,
        SpiralPattern,
        BokehPattern,
        HillsPattern,
        AuroraPattern,
        ChevronPattern,
        DiamondsPattern,
        WeavePattern,
        CrossesPattern,
        StepsPattern,
        PillsPattern,
        CubesPattern,
        ConfettiPattern,
        NestedPattern,
        PetalsPattern,
    )

    fun byId(id: String): Pattern? = all.find { it.id == id }
}
