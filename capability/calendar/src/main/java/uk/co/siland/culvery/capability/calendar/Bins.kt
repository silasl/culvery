package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.ui.BinInk

enum class BinKind(val key: String, val label: String, val defaultInk: String) {
    FOOD("food", "Food", "green"),
    GARDEN("garden", "Garden", "brown"),
    RECYCLING("recycling", "Recycling", "blue"),
    WASTE("waste", "Waste", "black"),
}

fun binKindOf(key: String): BinKind? = BinKind.entries.firstOrNull { it.key == key }

/** One bin on a bin day: its [kind] and the ink it is drawn in, null for a kind with none. */
data class BinUi(val kind: String, val ink: BinInk?)

/** A kind's label; the council's own name for a kind we do not know. */
fun binLabel(key: String): String = binKindOf(key)?.label ?: key

/** Known kinds in [BinKind] order, then unknown names alphabetically, each once. */
fun orderedBins(keys: Collection<String>): List<String> {
    val given = keys.toSet()
    val known = BinKind.entries.map { it.key }.filter { it in given }
    return known + (given - known.toSet()).sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** What a bin day says in place of who it is for. */
const val BINS_WHO = "Bins"

internal fun EventUi.whoLine(): String = if (bins != null) BINS_WHO else whoLine(people)

internal fun EventUi.spokenWho(): String = if (bins != null) BINS_WHO else spokenWho(people)

/** [whoLineForms], longest first, with a bin day's one form. */
internal fun EventUi.whoLineForms(): List<String> = if (bins != null) listOf(BINS_WHO) else whoLineForms(people)

/** "Food, Waste": the bins' labels in [orderedBins] order. */
fun binNames(keys: Collection<String>): String = orderedBins(keys).joinToString(", ") { binLabel(it) }

fun binsTitle(keys: Collection<String>): String = "Bins: " + binNames(keys)
