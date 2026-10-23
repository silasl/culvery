package uk.co.siland.culvery.core.plugin

import java.text.Normalizer
import java.util.Locale

private val WHITESPACE = Regex("\\s+")
private val MARKS = Regex("\\p{Mn}+")

/** What two texts must share to be one: trimmed, single-spaced, lower case, accents dropped. */
fun textKey(text: String): String =
    Normalizer.normalize(text.trim().replace(WHITESPACE, " "), Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)
