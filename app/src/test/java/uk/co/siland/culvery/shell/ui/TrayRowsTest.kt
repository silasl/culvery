package uk.co.siland.culvery.shell.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.TRAY_ROWS
import uk.co.siland.culvery.core.ui.TrayCell

/** The tray's spots (redesign note 13): two halves in the first row, the full-width buttons the middle one, three thirds the last. */
class TrayRowsTest {
    private fun half(id: String, order: Int) = TrayButton(id, order, TrayWidth.HALF) {}
    private fun third(id: String, order: Int) = TrayButton(id, order, TrayWidth.THIRD) {}
    private fun full(id: String, order: Int) = TrayButton(id, order) {}
    private fun ids(rows: List<List<TrayCell>>) = rows.map { row -> row.map { it.id } }

    @Test fun threeRowsAlways() {
        assertThat(trayRows(emptyList())).hasSize(TRAY_ROWS)
        assertThat(trayRows(listOf(full("hoover.start", 20)))).hasSize(TRAY_ROWS)
        assertThat(trayRows(listOf(half("a", 10), half("b", 11), full("hoover.start", 20), third("p1", 81), third("p2", 82)))).hasSize(TRAY_ROWS)
    }

    /** Fails if a third mode goes to the last row, which belongs to the sounds. */
    @Test fun halvesFillOnlyTheFirstRow() = assertThat(ids(trayRows(listOf(half("h1", 10), half("h2", 11), half("h3", 12), full("hoover", 20)))))
        .containsExactly(listOf("h1", "h2"), listOf("hoover"), emptyList<String>()).inOrder()

    /** Fails if the thirds don't share the last row in placement order. */
    @Test fun thirdsFillTheLastRow() =
        assertThat(ids(trayRows(listOf(half("h1", 10), half("h2", 11), full("hoover", 20), third("p1", 81), third("p2", 82), third("tkd", 90)))))
            .containsExactly(listOf("h1", "h2"), listOf("hoover"), listOf("p1", "p2", "tkd")).inOrder()

    @Test fun aFourthThirdIsLeftOff() =
        assertThat(ids(trayRows(listOf(third("p1", 81), third("p2", 82), third("p3", 83), third("tkd", 90)))))
            .containsExactly(emptyList<String>(), emptyList<String>(), listOf("p1", "p2", "p3")).inOrder()

    /** Fails if a full-width button doesn't take the middle row, or a second one doesn't take the first empty row. */
    @Test fun aFullButtonTakesTheMiddleRow() {
        assertThat(ids(trayRows(listOf(full("hoover", 20), third("p1", 81)))))
            .containsExactly(emptyList<String>(), listOf("hoover"), listOf("p1")).inOrder()
        assertThat(ids(trayRows(listOf(full("hoover", 20), full("other", 30), third("p1", 81)))))
            .containsExactly(listOf("other"), listOf("hoover"), listOf("p1")).inOrder()
    }

    @Test fun withNoHooverTheMiddleRowIsEmpty() = assertThat(ids(trayRows(listOf(half("h1", 10), half("h2", 11), third("p1", 81)))))
        .containsExactly(listOf("h1", "h2"), emptyList<String>(), listOf("p1")).inOrder()

    @Test fun aLoneHalfStaysInRowOne() = assertThat(ids(trayRows(listOf(half("h1", 10), full("hoover", 20)))))
        .containsExactly(listOf("h1"), listOf("hoover"), emptyList<String>()).inOrder()

    /** Fails if a cell doesn't carry its button's line and line tag. */
    @Test fun aCellCarriesItsButtonsLine() {
        val holiday = TrayButton("holiday.mode", 10, TrayWidth.HALF, lineTag = "holiday_tile_line") {}
        val cell = trayRows(listOf(holiday), mapOf("holiday.mode" to "Turn Party mode off first.")).first().single()
        assertThat(cell.line).isEqualTo("Turn Party mode off first.")
        assertThat(cell.lineTag).isEqualTo("holiday_tile_line")
    }
}
