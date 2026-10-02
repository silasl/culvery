package uk.co.siland.culvery.core.ui

import java.io.File
import java.nio.ByteBuffer

/**
 * The ligatures a TrueType/OpenType font makes: [has] says whether typing a name gives one glyph, and [hasFilledForm]
 * whether that glyph has its single-substitution alternate (Material Symbols' FILL ≥ 0.99 variant, reached through
 * `rclt`). Reads the `cmap` (format 12, else 4), every GSUB ligature lookup (type 4) and every single substitution
 * (type 1), each also inside a type 7 extension.
 */
internal class OpenTypeLigatures(file: File) {
    private val bytes: ByteBuffer = ByteBuffer.wrap(file.readBytes())
    private val tables: Map<String, Int> = tableOffsets()
    private val glyphOf: (Int) -> Int = characterMap()
    private val ligatures: Map<List<Int>, Int> = HashMap()
    private val singles: Map<Int, Int> = HashMap()

    init {
        readSubstitutions()
    }

    fun has(name: String): Boolean = ligatures.containsKey(name.map { glyphOf(it.code) })

    fun hasFilledForm(name: String): Boolean = ligatures[name.map { glyphOf(it.code) }]?.let(singles::containsKey) == true

    private fun u16(at: Int): Int = bytes.getShort(at).toInt() and 0xFFFF

    private fun u32(at: Int): Long = bytes.getInt(at).toLong() and 0xFFFFFFFFL

    private fun tableOffsets(): Map<String, Int> = (0 until u16(4)).associate { i ->
        val record = 12 + 16 * i
        String(ByteArray(4) { bytes.get(record + it) }, Charsets.US_ASCII) to u32(record + 8).toInt()
    }

    private fun characterMap(): (Int) -> Int {
        val cmap = tables.getValue("cmap")
        val subtables = (0 until u16(cmap + 2)).map { i -> cmap + u32(cmap + 4 + 8 * i + 4).toInt() }
        subtables.firstOrNull { u16(it) == 12 }?.let { t -> return { c -> format12(t, c) } }
        val format4 = subtables.first { u16(it) == 4 }
        return { c -> format4(format4, c) }
    }

    private fun format12(table: Int, c: Int): Int {
        for (g in 0 until u32(table + 12).toInt()) {
            val at = table + 16 + 12 * g
            val start = u32(at)
            if (c in start..u32(at + 4)) return (u32(at + 8) + (c - start)).toInt()
        }
        return 0
    }

    private fun format4(table: Int, c: Int): Int {
        val segments = u16(table + 6) / 2
        val ends = table + 14
        val starts = ends + 2 * segments + 2
        val deltas = starts + 2 * segments
        val ranges = deltas + 2 * segments
        for (s in 0 until segments) {
            if (c > u16(ends + 2 * s)) continue
            val start = u16(starts + 2 * s)
            if (c < start) return 0
            val delta = u16(deltas + 2 * s)
            val rangeAt = ranges + 2 * s
            val range = u16(rangeAt)
            if (range == 0) return (c + delta) and 0xFFFF
            val glyph = u16(rangeAt + range + 2 * (c - start))
            return if (glyph == 0) 0 else (glyph + delta) and 0xFFFF
        }
        return 0
    }

    private fun readSubstitutions() {
        val gsub = tables.getValue("GSUB")
        val lookupList = gsub + u16(gsub + 8)
        for (l in 0 until u16(lookupList)) {
            val lookup = lookupList + u16(lookupList + 2 + 2 * l)
            val type = u16(lookup)
            for (s in 0 until u16(lookup + 4)) {
                var subtable = lookup + u16(lookup + 6 + 2 * s)
                var subtableType = type
                if (type == EXTENSION) {
                    subtableType = u16(subtable + 2)
                    subtable += u32(subtable + 4).toInt()
                }
                when (subtableType) {
                    SINGLE -> addSingles(subtable)
                    LIGATURE -> addLigatures(subtable)
                }
            }
        }
    }

    private fun addSingles(subtable: Int) {
        val from = coverage(subtable + u16(subtable + 2))
        val found = singles as HashMap
        when (u16(subtable)) {
            1 -> from.forEach { g -> found[g] = (g + bytes.getShort(subtable + 4).toInt()) and 0xFFFF }
            else -> from.forEachIndexed { i, g -> found[g] = u16(subtable + 6 + 2 * i) }
        }
    }

    private fun addLigatures(subtable: Int) {
        val firsts = coverage(subtable + u16(subtable + 2))
        val found = ligatures as HashMap
        for (i in 0 until u16(subtable + 4)) {
            val set = subtable + u16(subtable + 6 + 2 * i)
            for (j in 0 until u16(set)) {
                val ligature = set + u16(set + 2 + 2 * j)
                val components = u16(ligature + 2)
                found[listOf(firsts[i]) + (0 until components - 1).map { u16(ligature + 4 + 2 * it) }] = u16(ligature)
            }
        }
    }

    private fun coverage(at: Int): List<Int> = when (u16(at)) {
        1 -> (0 until u16(at + 2)).map { u16(at + 4 + 2 * it) }
        else -> (0 until u16(at + 2)).flatMap { r -> (u16(at + 4 + 6 * r)..u16(at + 6 + 6 * r)).toList() }
    }

    private companion object {
        const val SINGLE = 1
        const val LIGATURE = 4
        const val EXTENSION = 7
    }
}
