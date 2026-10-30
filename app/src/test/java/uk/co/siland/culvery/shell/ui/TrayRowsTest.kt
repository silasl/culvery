package uk.co.siland.culvery.shell.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth

/** How the tray's buttons fall into rows (15a §2.1). */
class TrayRowsTest {
    private fun half(id: String, order: Int) = TrayButton(id, order, TrayWidth.HALF) {}
    private fun full(id: String, order: Int) = TrayButton(id, order) {}
    private fun ids(rows: List<List<Pair<String, *>>>) = rows.map { row -> row.map { it.first } }

    /** Fails if two half-width buttons next to each other don't share a row. */
    @Test fun twoHalvesShareARow() = assertThat(ids(trayRows(listOf(half("holiday.mode", 10), half("party.mode", 15), full("hoover.start", 20)))))
        .containsExactly(listOf("holiday.mode", "party.mode"), listOf("hoover.start")).inOrder()

    /** Fails if a half with no half after it is paired with the full-width button instead of having its own row. */
    @Test fun aLoneHalfTakesItsOwnRow() = assertThat(ids(trayRows(listOf(half("party.mode", 15), full("hoover.start", 20)))))
        .containsExactly(listOf("party.mode"), listOf("hoover.start")).inOrder()

    /** Fails if halves either side of a full button are paired across it. */
    @Test fun aFullBetweenHalvesBreaksThePair() = assertThat(ids(trayRows(listOf(half("a", 10), full("b", 20), half("c", 30)))))
        .containsExactly(listOf("a"), listOf("b"), listOf("c")).inOrder()
}
