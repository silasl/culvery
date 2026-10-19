package uk.co.siland.culvery.core.plugin

/** Home's two columns: [left] is the TALL card, [right] the cards stacked top to bottom (9 §4.2, §6.1). */
data class HomeLayout(val left: HomeCard?, val right: List<HomeCard>) {
    companion object {
        val Empty = HomeLayout(null, emptyList())
    }
}

object HomeCardPlacer {
    const val RIGHT_CARDS = 2

    fun place(cards: List<HomeCard>): HomeLayout {
        val ordered = cards.sortedWith(compareByDescending<HomeCard> { it.priority }.thenBy { it.id })
        val regular = ordered.filter { it.size == HomeCardSize.REGULAR }.take(RIGHT_CARDS)
        return HomeLayout(
            left = ordered.firstOrNull { it.size == HomeCardSize.TALL },
            right = regular.ifEmpty { listOfNotNull(ordered.firstOrNull { it.size == HomeCardSize.WIDE }) },
        )
    }
}
