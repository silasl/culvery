package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.HhTrayGrid
import uk.co.siland.culvery.core.ui.TRAY_ROWS
import uk.co.siland.culvery.core.ui.TrayCell

/** Home's One-tap tray: the capabilities' [buttons], in the order they were placed (11 §4.4), modes side by side (15a §2.1). */
@Composable
internal fun HomeTray(buttons: List<TrayButton>, modifier: Modifier = Modifier) {
    Box(modifier.testTag("home_tray")) {
        val rows = remember(buttons) { trayRows(buttons) }
        HhTrayGrid("One-tap", rows, Modifier.fillMaxSize())
    }
}

/**
 * The tray's [TRAY_ROWS] spots (15b Ruling 11): half-width buttons fill the first row and then the third, two to a row
 * in placement order; full-width buttons fill the middle row, then any row still empty. A row with nothing is empty.
 */
internal fun trayRows(buttons: List<TrayButton>): List<List<TrayCell>> {
    val rows = List(TRAY_ROWS) { mutableListOf<TrayCell>() }
    val halfRows = listOf(0, TRAY_ROWS - 1)
    buttons.filter { it.width == TrayWidth.HALF }.chunked(2).zip(halfRows).forEach { (pair, row) -> pair.mapTo(rows[row]) { it.id to it.content } }
    val fullRows = (listOf(TRAY_ROWS / 2) + rows.indices).distinct().filter { rows[it].isEmpty() }
    buttons.filter { it.width == TrayWidth.FULL }.zip(fullRows).forEach { (button, row) -> rows[row] += button.id to button.content }
    return rows
}
