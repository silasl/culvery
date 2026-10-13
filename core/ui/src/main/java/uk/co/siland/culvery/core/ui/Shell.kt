package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shell component values from the hand-off (§6 Holiday sheet, §7 Calendar sheets and toast). */
object ShellTokens {
    /** rgba(0,0,0,.55) behind sheets. The same in both themes, so not an HhColors token. */
    val sheetScrim = Color(0x8C000000)

    /** rgba(0,0,0,.5) behind the PIN pad. The same in both themes. */
    val pinScrim = Color(0x80000000)

    // Right-side sheet: 600 dp, full height, bg, 1 dp left border `line`, 16 dp between blocks, 48 dp close button.
    val sheetWidth = 600.dp
    val sheetBorder = 1.dp
    val sheetGap = 16.dp
    val closeButton = 48.dp
    val closeIcon = 26.dp

    // Toast: bottom-centre pill 28 dp above the bottom, padding 14×22, radius 26, `info` icon; its time is [toastMillis].
    val toastBottom = 28.dp
    val toastPaddingV = 14.dp
    val toastPaddingH = 22.dp
    val toastRadius = 26.dp
    val toastIcon = 22.dp
    val toastIconGap = 10.dp
    val toastMaxWidth = 720.dp

    /** Home's cards (hand-off: Today's `border-radius: 26px`): the calendar's and the weather's. */
    val homeCardRadius = 26.dp

    // Status-bar sign-in: account_circle 16 dp, 6 dp between items, 16 dp before the theme indicator.
    val statusIcon = 16.dp
    val statusGap = 6.dp
    val statusGroupGap = 16.dp
    val statusSignOutPaddingH = 10.dp
    val statusSignOutPaddingV = 6.dp
}

/** Shell text styles, derived from HhType. */
object ShellType {
    /** 16 sp / 600: toast text. */
    val toast = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 12 sp / 700: the status bar's "Sign out". */
    val signOut = HhType.labelSmall
}

/** How long a toast shows (4d-2 §6.5): 4 s, 6 s for an error or refusal, longer for long text, at most 8 s. */
fun toastMillis(message: String, urgent: Boolean): Long {
    val base = if (urgent) 6_000L else 4_000L
    // 50 ms a character is a slow reader's pace; 40 characters fit in the base time.
    return minOf(base + 50L * (message.length - 40).coerceAtLeast(0), 8_000L)
}

/**
 * The hand-off's right-side sheet: 600 dp, full height, `bg`, 1 dp `line` left border, 16 dp between blocks.
 * Taps on empty sheet space are swallowed so they don't reach the scrim underneath.
 */
@Composable
fun HhSheet(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    keyboard: WindowInsets = WindowInsets.ime,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
        modifier = modifier
            .fillMaxHeight()
            .width(ShellTokens.sheetWidth)
            .background(c.bg)
            .drawBehind {
                val x = ShellTokens.sheetBorder.toPx() / 2
                drawLine(c.line, Offset(x, 0f), Offset(x, size.height), ShellTokens.sheetBorder.toPx())
            }
            .pointerInput(Unit) { detectTapGestures { } }
            // The background runs behind the keyboard; the content stays above it.
            .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
            .padding(padding),
        content = content,
    )
}

/** 48 dp round quiet button with a 26 dp `close` icon, named by its icon. Sheets are `bg`, so its fill is the quiet fill on `bg`. */
@Composable
fun HhCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("sheet_close")
            .size(ShellTokens.closeButton)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Bg, c))
            .hhClickable(onClick),
    ) {
        HhIcon(Icons.CLOSE, size = ShellTokens.closeIcon, tint = c.ink, contentDescription = "Close")
    }
}

/** The toast pill: `ink` background, `bg` text, 16 sp / 600, padding 14×22, radius 26. One spoken item, polite unless [urgent]. */
@Composable
fun HhToast(message: String, icon: String, modifier: Modifier = Modifier, urgent: Boolean = false) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShellTokens.toastIconGap),
        modifier = modifier
            .testTag("toast")
            .semantics(mergeDescendants = true) { liveRegion = if (urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite }
            .widthIn(max = ShellTokens.toastMaxWidth)
            .clip(RoundedCornerShape(ShellTokens.toastRadius))
            .background(c.ink)
            .padding(horizontal = ShellTokens.toastPaddingH, vertical = ShellTokens.toastPaddingV),
    ) {
        HhIcon(icon, size = ShellTokens.toastIcon, tint = c.bg)
        Text(message, style = ShellType.toast, color = c.bg)
    }
}
