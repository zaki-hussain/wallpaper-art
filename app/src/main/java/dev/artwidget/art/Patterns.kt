package dev.artwidget.art

import dev.artwidget.art.patterns.BauhausPattern
import dev.artwidget.art.patterns.BlobsPattern
import dev.artwidget.art.patterns.ContoursPattern
import dev.artwidget.art.patterns.DotsPattern
import dev.artwidget.art.patterns.FlowPattern
import dev.artwidget.art.patterns.GradientPattern
import dev.artwidget.art.patterns.HalftonePattern
import dev.artwidget.art.patterns.HexPattern
import dev.artwidget.art.patterns.MeshPattern
import dev.artwidget.art.patterns.MondrianPattern
import dev.artwidget.art.patterns.OrbitsPattern
import dev.artwidget.art.patterns.RadialPattern
import dev.artwidget.art.patterns.RaysPattern
import dev.artwidget.art.patterns.RidgesPattern
import dev.artwidget.art.patterns.RingsPattern
import dev.artwidget.art.patterns.ScallopsPattern
import dev.artwidget.art.patterns.SolidPattern
import dev.artwidget.art.patterns.StripesPattern
import dev.artwidget.art.patterns.TerrazzoPattern
import dev.artwidget.art.patterns.TrianglesPattern
import dev.artwidget.art.patterns.TruchetPattern
import dev.artwidget.art.patterns.VoronoiPattern
import dev.artwidget.art.patterns.WavesPattern

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
    )

    fun byId(id: String): Pattern? = all.find { it.id == id }
}
