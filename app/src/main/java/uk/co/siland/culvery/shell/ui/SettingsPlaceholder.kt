package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Culvery

/** Replaced by real Settings screens in Plan 4. [sections] are the capabilities' blocks (3a: the calendar's connections). */
@Composable
fun SettingsPlaceholder(onExitKiosk: () -> Unit, onClose: () -> Unit, sections: @Composable () -> Unit = {}) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings")
            .background(c.bg)
            // Swallow taps on empty space so they don't reach the rail underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(48.dp),
    ) {
        Text("Settings", style = HhType.screenTitle, color = c.ink)
        Text("Household, people and connections arrive in a later update.", style = HhType.body, color = c.mute)
        sections()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhPillButton("Exit kiosk", onExitKiosk)
            HhPillButton("Close", onClose, primary = true)
        }
    }
}
