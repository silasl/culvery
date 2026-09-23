package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Culvery

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun StatusBar(now: LocalDateTime, dark: Boolean, previewing: Boolean, onToggleThemePreview: () -> Unit) {
    val c = Culvery.colors
    val label = when {
        !previewing -> "Auto"
        dark -> "Night"
        else -> "Day"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 20.dp),
    ) {
        Text(now.format(TIME), style = HhType.status, color = c.mute)
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("status_theme").clickable(onClick = onToggleThemePreview).padding(4.dp),
        ) {
            HhIcon(if (dark) "dark_mode" else "light_mode", size = 16.dp, tint = c.mute)
            Text(label, style = HhType.status.copy(fontSize = 12.sp), color = c.mute)
        }
    }
}
