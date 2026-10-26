package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.HomeCardSize.REGULAR
import uk.co.siland.culvery.core.plugin.HomeCardSize.TALL
import uk.co.siland.culvery.core.plugin.HomeCardSize.UTILITY
import uk.co.siland.culvery.core.plugin.HomeCardSize.WIDE

class HomeCardPlacerTest {
    private fun card(id: String, size: HomeCardSize, priority: Int) = HomeCard(id, size, priority) {}

    private fun HomeLayout.ids() = listOfNotNull(left?.id) to right.map { it.id }

    @Test
    fun emptyIsEmpty() {
        assertThat(HomeCardPlacer.place(emptyList())).isEqualTo(HomeLayout.Empty)
    }

    @Test
    fun theHighestTallCardTakesTheLeft() {
        val layout = HomeCardPlacer.place(listOf(card("b", TALL, 10), card("a", TALL, 20)))
        assertThat(layout.left?.id).isEqualTo("a")
        assertThat(layout.right).isEmpty()
    }

    @Test
    fun lightsSitAboveMusic() {
        val layout = HomeCardPlacer.place(listOf(card("music", REGULAR, 30), card("lights", REGULAR, 35)))
        assertThat(layout.right.map { it.id }).containsExactly("lights", "music").inOrder()
    }

    @Test
    fun atMostTwoOnTheRight() {
        val layout = HomeCardPlacer.place(
            listOf(card("c", REGULAR, 10), card("b", REGULAR, 30), card("a", REGULAR, 30)),
        )
        assertThat(layout.right.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(HomeCardPlacer.RIGHT_CARDS).isEqualTo(2)
    }

    @Test
    fun comingUpOnlyWithoutLightsOrMusic() {
        val withRegular = HomeCardPlacer.place(listOf(card("t", TALL, 100), card("w", WIDE, 50), card("r", REGULAR, 30)))
        assertThat(withRegular.ids()).isEqualTo(listOf("t") to listOf("r"))
        val without = HomeCardPlacer.place(listOf(card("t", TALL, 100), card("w", WIDE, 50)))
        assertThat(without.ids()).isEqualTo(listOf("t") to listOf("w"))
    }

    @Test
    fun aSecondWideCardIsDropped() {
        val layout = HomeCardPlacer.place(listOf(card("b", WIDE, 10), card("a", WIDE, 10), card("c", WIDE, 5)))
        assertThat(layout.right.map { it.id }).containsExactly("a")
    }

    @Test
    fun shoppingTakesTheTopAndLightsAndMusicShareARow() {
        val layout = HomeCardPlacer.place(
            listOf(
                card("today", TALL, 100), card("comingUp", WIDE, 50), card("shopping", UTILITY, 40),
                card("lights", REGULAR, 35), card("music", REGULAR, 30),
            ),
        )
        assertThat(layout.left?.id).isEqualTo("today")
        assertThat(layout.utility?.id).isEqualTo("shopping")
        assertThat(layout.right.map { it.id }).containsExactly("lights", "music").inOrder()
    }

    @Test
    fun comingUpNeverShowsWithShopping() {
        val layout = HomeCardPlacer.place(
            listOf(card("today", TALL, 100), card("comingUp", WIDE, 50), card("shopping", UTILITY, 40)),
        )
        assertThat(layout.utility?.id).isEqualTo("shopping")
        assertThat(layout.right).isEmpty()
    }

    @Test
    fun aSecondUtilityCardIsDropped() {
        val layout = HomeCardPlacer.place(listOf(card("b", UTILITY, 10), card("a", UTILITY, 40)))
        assertThat(layout.utility?.id).isEqualTo("a")
        assertThat(layout.right).isEmpty()
    }

    private fun button(id: String, order: Int) = TrayButton(id, order) {}

    @Test
    fun aTrayKeepsTheUtilityRow() {
        val layout = HomeCardPlacer.place(
            listOf(
                card("today", TALL, 100), card("comingUp", WIDE, 50), card("lights", REGULAR, 35), card("music", REGULAR, 30),
            ),
            listOf(button("hoover", 20)),
        )
        assertThat(layout.tray.map { it.id }).containsExactly("hoover")
        assertThat(layout.utility).isNull()
        assertThat(layout.right.map { it.id }).containsExactly("lights", "music").inOrder()
    }

    @Test
    fun trayButtonsAreInOrder() {
        val layout = HomeCardPlacer.place(
            emptyList(),
            listOf(button("hoover", 20), button("holiday", 10), button("b", 30), button("a", 30)),
        )
        assertThat(layout.tray.map { it.id }).containsExactly("holiday", "hoover", "a", "b").inOrder()
    }

    /** Pin: with no tray buttons the layout is what it was before trays. */
    @Test
    fun noTrayIsAsBefore() {
        val cards = listOf(card("today", TALL, 100), card("shopping", UTILITY, 40), card("lights", REGULAR, 35))
        assertThat(HomeCardPlacer.place(cards)).isEqualTo(HomeCardPlacer.place(cards, emptyList()))
        assertThat(HomeCardPlacer.place(cards).tray).isEmpty()
    }
}
