package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.shownNotSpoken
import uk.co.siland.culvery.display.ui.THE_CAMERA_IS_ON
import uk.co.siland.culvery.shell.SessionUi

internal const val WATCHING = "Watching"

// What the theme control's tap does, for TalkBack.
private const val PREVIEW_NIGHT = "preview the night theme"
private const val PREVIEW_DAY = "preview the day theme"
private const val BACK_TO_AUTOMATIC = "go back to the automatic theme"

/** What the theme control reads: the theme on screen, and whether the clock or a tap chose it (9 §4.1). */
internal fun themeLabel(dark: Boolean, previewing: Boolean): String =
    "${if (dark) "Night" else "Day"} · ${if (previewing) "Preview" else "Auto"}"

/** Where the Watching pill's icon is, or would be, in the root's coordinates; null until the top bar has been laid out. */
internal class CameraMarkSpot {
    var centre: Offset? by mutableStateOf(null)
}

/**
 * The top bar, right-aligned: the Watching pill while the camera runs, who is signed in with Sign out, then the theme
 * control (9 §4.1, §6.7). The pill's slot is laid out even when it is hidden, so nothing else moves when it comes and goes.
 * Each control takes the bar's height as its tap area.
 */
@Composable
internal fun StatusBar(
    dark: Boolean,
    previewing: Boolean,
    session: SessionUi?,
    onSignOut: () -> Unit,
    onToggleThemePreview: () -> Unit,
    watching: Boolean = false,
    pillSpot: CameraMarkSpot? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.topBarGap, Alignment.End),
        modifier = Modifier.testTag("status_bar").fillMaxWidth().heightIn(min = ShellDimens.topBarHeight),
    ) {
        WatchingPill(watching, pillSpot)
        if (session != null) {
            SignedIn(session, onSignOut)
            Box(Modifier.size(ShellDimens.dividerWidth, ShellDimens.divider).background(Culvery.colors.surf3))
        }
        ThemeControl(dark, previewing, onToggleThemePreview)
    }
}

@Composable
private fun WatchingPill(shown: Boolean, spot: CameraMarkSpot?) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.pillGap),
        modifier = Modifier
            .then(
                if (shown) {
                    Modifier.testTag("status_watching").semantics(mergeDescendants = true) { contentDescription = THE_CAMERA_IS_ON }
                } else {
                    Modifier.alpha(0f).clearAndSetSemantics {}
                },
            )
            .heightIn(min = ShellDimens.pillHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(c.accentSoft)
            .padding(horizontal = ShellDimens.pillPaddingH),
    ) {
        Box(Modifier.size(ShellDimens.pillDot).clip(CircleShape).background(c.accent))
        HhIcon(
            Icons.PHOTO_CAMERA,
            size = ShellDimens.pillIcon,
            filled = true,
            tint = c.accent,
            modifier = Modifier.onGloballyPositioned { spot?.centre = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) },
        )
        Text(
            WATCHING,
            style = HhType.label.copy(fontSize = ShellDimens.pillText, fontWeight = FontWeight.W700),
            color = c.accent,
            maxLines = 1,
            modifier = Modifier.shownNotSpoken(),
        )
    }
}

@Composable
private fun SignedIn(session: SessionUi, onSignOut: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.signedInGap),
        modifier = Modifier.testTag("status_session"),
    ) {
        HhIcon(Icons.ACCOUNT_CIRCLE, size = ShellDimens.signedInIcon, tint = c.mute)
        Text("${session.name} · ${session.role}", style = HhType.secondary, color = c.ink, maxLines = 1)
        // A 29 dp pill drawn inside the bar's full height, which is all tap.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.testTag("status_sign_out").hhClickable(onSignOut).heightIn(min = ShellDimens.topBarHeight),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .testTag("status_sign_out_pill")
                    .heightIn(min = ShellDimens.signOutHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(quietFill(ButtonSurface.Bg, c))
                    .padding(horizontal = ShellDimens.signOutPaddingH),
            ) {
                Text("Sign out", style = HhType.label.copy(fontSize = ShellDimens.signOutText, fontWeight = FontWeight.W700), color = c.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ThemeControl(dark: Boolean, previewing: Boolean, onToggle: () -> Unit) {
    val c = Culvery.colors
    val label = themeLabel(dark, previewing)
    val action = when {
        previewing -> BACK_TO_AUTOMATIC
        dark -> PREVIEW_DAY
        else -> PREVIEW_NIGHT
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.themeGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .testTag("status_theme")
            .hhClickable(onToggle, label = action)
            .semantics { contentDescription = "Theme: $label" }
            .heightIn(min = ShellDimens.topBarHeight)
            .padding(start = ShellDimens.themeTapStart),
    ) {
        HhIcon(if (dark) Icons.DARK_MODE else Icons.LIGHT_MODE, size = ShellDimens.themeIcon, tint = c.mute)
        Text(label, style = HhType.secondary, color = c.mute, maxLines = 1, modifier = Modifier.shownNotSpoken())
    }
}
