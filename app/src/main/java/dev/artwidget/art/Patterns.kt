package dev.artwidget.art

import dev.artwidget.art.patterns.FlowPattern
import dev.artwidget.art.patterns.GradientPattern
import dev.artwidget.art.patterns.SolidPattern

/** Registry of every available pattern. */
object Patterns {
    val all: List<Pattern> = listOf(
        SolidPattern,
        GradientPattern,
        FlowPattern,
    )

    fun byId(id: String): Pattern? = all.find { it.id == id }
}
