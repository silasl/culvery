package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.ui.HhTrayColumn

/** Home's One-tap tray: the capabilities' [buttons], in the order they were placed (11 §4.4). */
@Composable
internal fun HomeTray(buttons: List<TrayButton>, modifier: Modifier = Modifier) {
    Box(modifier.testTag("home_tray")) {
        val rows = remember(buttons) { buttons.map { it.id to it.content } }
        HhTrayColumn("One-tap", rows, Modifier.fillMaxSize())
    }
}
