package uk.co.siland.househub.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.househub.core.plugin.HomeCardSize.REGULAR
import uk.co.siland.househub.core.plugin.HomeCardSize.TALL
import uk.co.siland.househub.core.plugin.HomeCardSize.WIDE

class HomeCardPlacerTest {
    private fun card(id: String, size: HomeCardSize, priority: Int) = HomeCard(id, size, priority) {}

    private fun List<HomePlacement>.layout() =
        associate { it.card.id to listOf(it.col, it.row, it.colSpan, it.rowSpan) }

    @Test
    fun noCardsGivesEmptyLayout() {
        assertThat(HomeCardPlacer.place(emptyList())).isEmpty()
    }

    @Test
    fun v1LayoutTodayTallComingUpAndForecastWide() {
        val result = HomeCardPlacer.place(
            listOf(card("forecast", WIDE, 10), card("today", TALL, 100), card("comingUp", WIDE, 50)),
        )
        assertThat(result.layout()).containsExactly(
            "today", listOf(0, 0, 1, 2),
            "comingUp", listOf(1, 0, 2, 1),
            "forecast", listOf(1, 1, 2, 1),
        )
    }

    @Test
    fun higherPriorityWideCardPushesOthersDownAndLowestIsDropped() {
        val result = HomeCardPlacer.place(
            listOf(
                card("today", TALL, 100),
                card("comingUp", WIDE, 50),
                card("forecast", WIDE, 10),
                card("scenes", WIDE, 200),
            ),
        )
        assertThat(result.layout()).containsExactly(
            "today", listOf(0, 0, 1, 2),
            "scenes", listOf(1, 0, 2, 1),
            "comingUp", listOf(1, 1, 2, 1),
        )
    }

    @Test
    fun secondTallCardIsDropped() {
        val result = HomeCardPlacer.place(listOf(card("a", TALL, 20), card("b", TALL, 10)))
        assertThat(result.map { it.card.id }).containsExactly("a")
    }

    @Test
    fun regularCardsFillRightColumnsBeforeLeft() {
        val result = HomeCardPlacer.place(listOf(card("x", REGULAR, 10)))
        assertThat(result.layout()["x"]).isEqualTo(listOf(1, 0, 1, 1))
    }

    @Test
    fun regularCardTakesLeftColumnWhenRightIsFull() {
        val result = HomeCardPlacer.place(listOf(card("a", WIDE, 30), card("b", WIDE, 20), card("c", REGULAR, 10)))
        assertThat(result.layout()["c"]).isEqualTo(listOf(0, 0, 1, 1))
    }

    @Test
    fun equalPriorityIsOrderedById() {
        val result = HomeCardPlacer.place(listOf(card("b", WIDE, 10), card("a", WIDE, 10)))
        assertThat(result.layout()["a"]).isEqualTo(listOf(1, 0, 2, 1))
        assertThat(result.layout()["b"]).isEqualTo(listOf(1, 1, 2, 1))
    }
}
