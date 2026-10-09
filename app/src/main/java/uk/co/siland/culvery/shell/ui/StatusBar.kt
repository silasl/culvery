package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.ShellType
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.shell.SessionUi

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val STATUS_BAR_MIN_HEIGHT = 30.dp

// What the theme toggle's tap does, for TalkBack.
private const val PREVIEW_NIGHT = "preview the night theme"
private const val PREVIEW_DAY = "preview the day theme"
private const val BACK_TO_AUTOMATIC = "go back to the automatic theme"

/**
 * Hand-off status bar, plus §7's signed-in indicator: `account_circle` · "{name} · {role}" · "Sign out". Its controls
 * take a touch up to 48 dp around them (Compose's minimum touch target), so the bar stays 30 dp unless its text grows.
 */
@Composable
fun StatusBar(
    now: () -> LocalDateTime,
    dark: Boolean,
    previewing: Boolean,
    session: SessionUi?,
    onSignOut: () -> Unit,
    onToggleThemePreview: () -> Unit,
) {
    val c = Culvery.colors
    val label = when {
        !previewing -> "Auto"
        dark -> "Night"
        else -> "Day"
    }
    val action = when {
        previewing -> BACK_TO_AUTOMATIC
        dark -> PREVIEW_DAY
        else -> PREVIEW_NIGHT
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("status_bar").fillMaxWidth().heightIn(min = STATUS_BAR_MIN_HEIGHT).padding(horizontal = 20.dp),
    ) {
        Text(now().format(TIME), style = HhType.status, color = c.mute)
        Spacer(Modifier.weight(1f))
        if (session != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShellTokens.statusGap),
                modifier = Modifier.testTag("status_session"),
            ) {
                HhIcon(Icons.ACCOUNT_CIRCLE, size = ShellTokens.statusIcon, tint = c.mute)
                Text("${session.name} · ${session.role}", style = HhType.status, color = c.mute, maxLines = 1)
                Text(
                    "Sign out",
                    style = ShellType.signOut,
                    color = c.accent,
                    modifier = Modifier
                        .testTag("status_sign_out")
                        .hhClickable(onSignOut)
                        .padding(horizontal = ShellTokens.statusSignOutPaddingH, vertical = ShellTokens.statusSignOutPaddingV),
                )
            }
            Spacer(Modifier.width(ShellTokens.statusGroupGap))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .testTag("status_theme")
                .hhClickable(onToggleThemePreview, label = action)
                .semantics { contentDescription = "Theme: $label" }
                .padding(4.dp),
        ) {
            HhIcon(if (dark) Icons.DARK_MODE else Icons.LIGHT_MODE, size = 16.dp, tint = c.mute)
            Text(label, style = HhType.status.copy(fontSize = 12.sp), color = c.mute)
        }
    }
}
