package uk.co.siland.culvery.shell.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.TRAY_ROWS

/** The tray's spots (15b Ruling 11): halves fill the first row and then the third, the full-width buttons the middle one. */
class TrayRowsTest {
    private fun half(id: String, order: Int) = TrayButton(id, order, TrayWidth.HALF) {}
    private fun full(id: String, order: Int) = TrayButton(id, order) {}
    private fun ids(rows: List<List<Pair<String, *>>>) = rows.map { row -> row.map { it.first } }

    @Test fun threeRowsAlways() {
        assertThat(trayRows(emptyList())).hasSize(TRAY_ROWS)
        assertThat(trayRows(listOf(full("hoover.start", 20)))).hasSize(TRAY_ROWS)
        assertThat(trayRows(listOf(half("a", 10), half("b", 11), full("hoover.start", 20), half("c", 12), half("d", 13)))).hasSize(TRAY_ROWS)
    }

    /** Decision 3: fails if the third mode doesn't go to the row under the hoover's. */
    @Test fun halvesFillRowOneThenRowThree() = assertThat(ids(trayRows(listOf(half("h1", 10), half("h2", 11), half("h3", 12), full("hoover", 20)))))
        .containsExactly(listOf("h1", "h2"), listOf("hoover"), listOf("h3")).inOrder()

    @Test fun withNoHooverTheMiddleRowIsEmpty() = assertThat(ids(trayRows(listOf(half("h1", 10), half("h2", 11), half("h3", 12)))))
        .containsExactly(listOf("h1", "h2"), emptyList<String>(), listOf("h3")).inOrder()

    @Test fun aLoneHalfStaysInRowOne() = assertThat(ids(trayRows(listOf(half("h1", 10), full("hoover", 20)))))
        .containsExactly(listOf("h1"), listOf("hoover"), emptyList<String>()).inOrder()
}
