package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.HhTrayGrid
import uk.co.siland.culvery.core.ui.TRAY_ROWS
import uk.co.siland.culvery.core.ui.TrayCell

/** Home's One-tap tray: the capabilities' [buttons], in the order they were placed (11 §4.4), modes and sounds side by side (18 §4.1). */
@Composable
internal fun HomeTray(buttons: List<TrayButton>, modifier: Modifier = Modifier) {
    val lines = buttons.associate { button -> button.id to key(button.id) { button.line.collectAsStateWithLifecycle(null).value } }
    Box(modifier.testTag("home_tray")) {
        HhTrayGrid("One-tap", trayRows(buttons, lines), Modifier.fillMaxSize())
    }
}

/**
 * The tray's [TRAY_ROWS] spots (redesign note 13): half-width buttons fill the first row, two at most, and third-width
 * buttons the last, three at most, in placement order; full-width buttons fill the middle row, then any row still empty.
 * Anything past those is left off, and a row with nothing is empty. Each cell carries its button's line from [lines], by id.
 */
internal fun trayRows(buttons: List<TrayButton>, lines: Map<String, String?> = emptyMap()): List<List<TrayCell>> {
    val rows = List(TRAY_ROWS) { mutableListOf<TrayCell>() }
    fun TrayButton.cell() = TrayCell(id, lines[id], lineTag, content)
    fun widths(width: TrayWidth) = buttons.filter { it.width == width }
    widths(TrayWidth.HALF).take(2).mapTo(rows.first()) { it.cell() }
    widths(TrayWidth.THIRD).take(3).mapTo(rows.last()) { it.cell() }
    val fullRows = (listOf(TRAY_ROWS / 2) + rows.indices).distinct().filter { rows[it].isEmpty() }
    widths(TrayWidth.FULL).zip(fullRows).forEach { (button, row) -> rows[row] += button.cell() }
    return rows
}
