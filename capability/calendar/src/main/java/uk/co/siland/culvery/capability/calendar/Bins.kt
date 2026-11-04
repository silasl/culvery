package uk.co.siland.culvery.capability.calendar

enum class BinKind(val key: String, val label: String, val defaultInk: String) {
    FOOD("food", "Food", "green"),
    GARDEN("garden", "Garden", "brown"),
    RECYCLING("recycling", "Recycling", "blue"),
    WASTE("waste", "Waste", "black"),
}

fun binKindOf(key: String): BinKind? = BinKind.entries.firstOrNull { it.key == key }

/** A kind's label; the council's own name for a kind we do not know. */
fun binLabel(key: String): String = binKindOf(key)?.label ?: key

/** Known kinds in [BinKind] order, then unknown names alphabetically, each once. */
fun orderedBins(keys: Collection<String>): List<String> {
    val distinct = keys.distinct()
    val known = BinKind.entries.map { it.key }.filter { it in distinct }
    return known + distinct.filter { binKindOf(it) == null }.sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** What a bin day says in place of who it is for. */
const val BINS_WHO = "Bins"

internal fun EventUi.whoLine(): String = if (bins != null) BINS_WHO else whoLine(people)

internal fun EventUi.spokenWho(): String = if (bins != null) BINS_WHO else spokenWho(people)

fun binsTitle(keys: Collection<String>): String = "Bins: " + orderedBins(keys).joinToString(", ") { binLabel(it) }
