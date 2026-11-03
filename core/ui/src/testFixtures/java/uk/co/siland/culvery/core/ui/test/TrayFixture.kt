package uk.co.siland.culvery.core.ui.test

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.HhTrayGrid
import uk.co.siland.culvery.core.ui.TrayCell

/** The One-tap tray's size on the home screen, in dp. */
const val TRAY_WIDTH_DP = 333
const val TRAY_HEIGHT_DP = 359

/**
 * One [cell] in the One-tap tray at its home-screen size, beside an empty neighbour cell or (with [neighbour] false) across
 * the full width, with its [line] under the row tagged [lineTag].
 */
@Composable
fun OneTapTray(id: String, neighbour: Boolean = true, line: String? = null, lineTag: String = "${id}_line", cell: @Composable () -> Unit) {
    val own = TrayCell(id, line, lineTag, cell)
    val row = if (neighbour) listOf(own, TrayCell("neighbour") { Spacer(Modifier.fillMaxSize()) }) else listOf(own)
    HhTrayGrid("One-tap", listOf(row), Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp))
}
