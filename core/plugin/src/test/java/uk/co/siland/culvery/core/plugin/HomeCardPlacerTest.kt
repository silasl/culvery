package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.HomeCardSize.REGULAR
import uk.co.siland.culvery.core.plugin.HomeCardSize.TALL
import uk.co.siland.culvery.core.plugin.HomeCardSize.WIDE

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

    @Test
    fun mixedSizesFillTallThenWideThenRegularBesideIt() {
        val result = HomeCardPlacer.place(
            listOf(card("today", TALL, 100), card("comingUp", WIDE, 50), card("r1", REGULAR, 20), card("r2", REGULAR, 10)),
        )
        assertThat(result.layout()).containsExactly(
            "today", listOf(0, 0, 1, 2),
            "comingUp", listOf(1, 0, 2, 1),
            "r1", listOf(1, 1, 1, 1),
            "r2", listOf(2, 1, 1, 1),
        )
    }

    @Test
    fun lowPriorityTallIsDroppedWhenRegularCardsTookTheLeftColumn() {
        val result = HomeCardPlacer.place(
            listOf(
                card("a", REGULAR, 90), card("b", REGULAR, 80), card("c", REGULAR, 70),
                card("d", REGULAR, 60), card("e", REGULAR, 50), card("t", TALL, 40), card("f", REGULAR, 30),
            ),
        )
        assertThat(result.map { it.card.id }).doesNotContain("t")
        assertThat(result.layout()["e"]).isEqualTo(listOf(0, 0, 1, 1))
        assertThat(result.layout()["f"]).isEqualTo(listOf(0, 1, 1, 1))
    }

    @Test
    fun fullGridDropsEverythingElse() {
        val six = listOf("a", "b", "c", "d", "e", "f").mapIndexed { i, id -> card(id, REGULAR, 60 - i * 10) }
        val result = HomeCardPlacer.place(six + card("late", REGULAR, 5) + card("tall", TALL, 1) + card("wide", WIDE, 2))
        assertThat(result.map { it.card.id }).containsExactly("a", "b", "c", "d", "e", "f")
        assertThat(result.layout()).containsExactly(
            "a", listOf(1, 0, 1, 1),
            "b", listOf(2, 0, 1, 1),
            "c", listOf(1, 1, 1, 1),
            "d", listOf(2, 1, 1, 1),
            "e", listOf(0, 0, 1, 1),
            "f", listOf(0, 1, 1, 1),
        )
    }

    @Test
    fun withOnlyTheConnectCardTheForecastTakesTheFirstCellBesideIt() {
        val result = HomeCardPlacer.place(listOf(card("calendar.connect", TALL, 100), card("weather.forecast", REGULAR, 40)))
        assertThat(result.layout()["weather.forecast"]).isEqualTo(listOf(1, 0, 1, 1))
    }
}
