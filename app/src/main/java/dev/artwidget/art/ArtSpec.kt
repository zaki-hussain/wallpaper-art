package dev.artwidget.art

import org.json.JSONArray
import org.json.JSONObject

/**
 * The complete recipe for one artwork: pattern id + geometry seed + explicit colours.
 * Rendering is deterministic, so the same spec reproduces the same art at any size.
 * The share code is just this JSON, small enough to paste anywhere.
 */
data class ArtSpec(val pattern: String, val seed: Long, val colors: List<Int>) {

    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("p", pattern)
        o.put("s", seed.toString())
        val arr = JSONArray()
        colors.forEach { arr.put(String.format("#%06X", it and 0xFFFFFF)) }
        o.put("c", arr)
        return o.toString()
    }

    companion object {
        fun fromJson(text: String): ArtSpec? {
            val t = text.trim()
            // Real codes are tiny; the cap also keeps hostile deeply nested JSON from
            // exhausting the parser's recursion depth.
            if (t.isEmpty() || t.length > 4096) return null
            return try {
                val o = JSONObject(t)
                val pattern = o.getString("p")
                val seed = o.getString("s").toLong()
                val arr = o.getJSONArray("c")
                if (arr.length() == 0 || arr.length() > 32) return null
                val colors = (0 until arr.length()).map { parseHex(arr.getString(it)) ?: return null }
                ArtSpec(pattern, seed, colors)
            } catch (e: Throwable) {
                // Throwable, not Exception: org.json recursion can throw
                // StackOverflowError on hostile input, and import must never crash.
                null
            }
        }

        private fun parseHex(s: String): Int? {
            val t = s.trim().removePrefix("#")
            if (t.length != 6 || t.any { Character.digit(it, 16) < 0 }) return null
            return (0xFF shl 24) or t.toInt(16)
        }
    }
}
