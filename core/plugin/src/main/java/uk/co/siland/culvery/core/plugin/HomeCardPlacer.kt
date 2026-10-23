package uk.co.siland.culvery.core.plugin

/**
 * Home's two columns: [left] is the TALL card, [right] the cards stacked top to bottom (9 §4.2, §6.1). With a [utility]
 * card, it fills the top of the right column and [right] shares one row beneath it (10 §4.9).
 */
data class HomeLayout(val left: HomeCard?, val right: List<HomeCard>, val utility: HomeCard? = null) {
    companion object {
        val Empty = HomeLayout(null, emptyList())
    }
}

object HomeCardPlacer {
    const val RIGHT_CARDS = 2

    fun place(cards: List<HomeCard>): HomeLayout {
        val ordered = cards.sortedWith(compareByDescending<HomeCard> { it.priority }.thenBy { it.id })
        val regular = ordered.filter { it.size == HomeCardSize.REGULAR }.take(RIGHT_CARDS)
        val utility = ordered.firstOrNull { it.size == HomeCardSize.UTILITY }
        return HomeLayout(
            left = ordered.firstOrNull { it.size == HomeCardSize.TALL },
            right = when {
                utility != null -> regular
                else -> regular.ifEmpty { listOfNotNull(ordered.firstOrNull { it.size == HomeCardSize.WIDE }) }
            },
            utility = utility,
        )
    }
}
