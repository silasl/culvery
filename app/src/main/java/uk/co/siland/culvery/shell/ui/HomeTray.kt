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
import uk.co.siland.culvery.core.ui.TrayCell

/** Home's One-tap tray: the capabilities' [buttons], in the order they were placed (11 §4.4), modes side by side (15a §2.1). */
@Composable
internal fun HomeTray(buttons: List<TrayButton>, modifier: Modifier = Modifier) {
    Box(modifier.testTag("home_tray")) {
        val rows = remember(buttons) { trayRows(buttons) }
        HhTrayGrid("One-tap", rows, Modifier.fillMaxSize())
    }
}

/** Two [TrayWidth.HALF] buttons in a row share a line; any other button, and a half with no half beside it, has its own. */
internal fun trayRows(buttons: List<TrayButton>): List<List<TrayCell>> {
    val rows = mutableListOf<List<TrayCell>>()
    var i = 0
    while (i < buttons.size) {
        val b = buttons[i]
        val next = buttons.getOrNull(i + 1)
        if (b.width == TrayWidth.HALF && next?.width == TrayWidth.HALF) {
            rows += listOf(b.id to b.content, next.id to next.content)
            i += 2
        } else {
            rows += listOf(b.id to b.content)
            i++
        }
    }
    return rows
}
