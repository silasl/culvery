package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Holiday and Party mode's tile and sheet layout values (11 §5.2, 13 §2.6); provisional until the end-of-v1 design walk-through. */
object HouseModeDimens {
    val radius = 17.dp
    val paddingV = 10.dp
    val paddingH = 12.dp
    val gap = 12.dp
    val icon = 24.dp
    val secondStyle = HhType.secondary.copy(fontSize = 12.sp)
    val lineGap = 6.dp

    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
    val stepGap = 8.dp
    val buttonGap = 12.dp
}

/** The words Holiday and Party mode's tiles and sheets share. */
object HouseModeWords {
    const val TURNING_ON = "Turning on…"
    const val TURNING_OFF = "Turning off…"
    const val CLICK_ON = "Turn on"
    const val CLICK_OFF = "Turn off"
    const val CANCEL = "Cancel"
}

/**
 * A mode's tray tile: [icon], [label] and [second] shown and spoken as [spoken], then [trailing], then what didn't
 * happen as [line] under it. A tap does nothing unless [enabled]. Its tags start with [tag]; [second]'s is [secondTag].
 */
@Composable
fun HouseModeTile(
    tag: String,
    icon: String,
    label: String,
    on: Boolean,
    second: String?,
    secondTag: String,
    line: String?,
    spoken: String,
    enabled: Boolean,
    onTap: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Culvery.colors
    val ink = if (on) colors.accentInk else colors.ink
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HouseModeDimens.gap),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .clip(RoundedCornerShape(HouseModeDimens.radius))
                .background(if (on) colors.accent else colors.surf2)
                .hhClickable(onTap, label = if (on) HouseModeWords.CLICK_OFF else HouseModeWords.CLICK_ON, enabled = enabled, spoken = spoken)
                .padding(horizontal = HouseModeDimens.paddingH, vertical = HouseModeDimens.paddingV),
        ) {
            HhIcon(icon, size = HouseModeDimens.icon, filled = true, tint = if (on) ink else colors.accent)
            Column(Modifier.weight(1f).shownNotSpoken()) {
                Text(label, style = HhType.buttonLabel, color = ink, maxLines = 1)
                if (second != null) {
                    Text(
                        second,
                        style = HouseModeDimens.secondStyle,
                        color = if (on) ink else colors.mute,
                        maxLines = 1,
                        modifier = Modifier.testTag(secondTag),
                    )
                }
            }
            trailing()
        }
        if (line != null) {
            Text(
                line,
                style = HouseModeDimens.secondStyle,
                color = colors.danger,
                modifier = Modifier
                    .padding(start = HouseModeDimens.paddingH, end = HouseModeDimens.paddingH, top = HouseModeDimens.lineGap)
                    .testTag("${tag}_line"),
            )
        }
    }
}

/**
 * A mode's sheet: [header], then [body] scrolling, then [confirm] and Cancel. Neither button works unless [enabled].
 * Its tags start with [tag]; the confirm's is [confirmTag], Cancel's [cancelTag].
 */
@Composable
fun HouseModeSheet(
    tag: String,
    title: String,
    confirm: String,
    confirmTag: String,
    cancelTag: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
    header: @Composable ColumnScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    HhSheet(
        padding = PaddingValues(horizontal = HouseModeDimens.sheetPaddingH, vertical = HouseModeDimens.sheetPaddingV),
        modifier = Modifier.testTag(tag).semantics { paneTitle = title },
    ) {
        header()
        Column(
            verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            content = body,
        )
        Column(verticalArrangement = Arrangement.spacedBy(HouseModeDimens.buttonGap)) {
            HhFooterButton(confirm, onConfirm, confirmTag, enabled = enabled)
            HhButton(
                HouseModeWords.CANCEL, onCancel, Modifier.fillMaxWidth().testTag(cancelTag),
                tone = ButtonTone.Quiet, size = ButtonSize.Large, enabled = enabled, on = ButtonSurface.Bg,
            )
        }
    }
}

/** The sheet's list: [title], then each of [lines], tagged [tag]. */
@Composable
fun HouseModeLines(title: String, lines: List<String>, tag: String) {
    val c = Culvery.colors
    Text(title, style = HhType.label, color = c.mute)
    Column(verticalArrangement = Arrangement.spacedBy(HouseModeDimens.stepGap), modifier = Modifier.testTag(tag)) {
        lines.forEach { Text(it, style = HhType.body, color = c.ink) }
    }
}
