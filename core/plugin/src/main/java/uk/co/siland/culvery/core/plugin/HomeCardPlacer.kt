package uk.co.siland.culvery.core.plugin

data class HomePlacement(
    val card: HomeCard,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
)

object HomeCardPlacer {
    const val COLUMNS = 3
    const val ROWS = 2

    // Right-hand cells first so REGULAR cards sit beside a TALL card before taking its column.
    private val regularOrder = listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2, 0 to 0, 1 to 0)

    fun place(cards: List<HomeCard>): List<HomePlacement> {
        val used = Array(ROWS) { BooleanArray(COLUMNS) }
        val placed = mutableListOf<HomePlacement>()
        val ordered = cards.sortedWith(compareByDescending<HomeCard> { it.priority }.thenBy { it.id })
        for (card in ordered) {
            val placement = fit(card, used) ?: continue
            for (r in placement.row until placement.row + placement.rowSpan) {
                for (c in placement.col until placement.col + placement.colSpan) used[r][c] = true
            }
            placed += placement
        }
        return placed
    }

    private fun fit(card: HomeCard, used: Array<BooleanArray>): HomePlacement? =
        when (card.size) {
            HomeCardSize.TALL ->
                if (!used[0][0] && !used[1][0]) HomePlacement(card, 0, 0, 1, 2) else null
            HomeCardSize.WIDE ->
                (0 until ROWS).firstOrNull { r -> !used[r][1] && !used[r][2] }
                    ?.let { r -> HomePlacement(card, 1, r, 2, 1) }
            HomeCardSize.REGULAR ->
                regularOrder.firstOrNull { (r, c) -> !used[r][c] }
                    ?.let { (r, c) -> HomePlacement(card, c, r, 1, 1) }
        }
}
