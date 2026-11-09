package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** How long a third-width tile shows its pressed colour after a tap. */
const val PRESS_FLASH_MS = 150L

/** Holiday and Party mode's tile and sheet layout values (11 §5.2, 13 §2.6); provisional until the end-of-v1 design walk-through. */
object HouseModeDimens {
    val radius = 17.dp
    val paddingV = 10.dp
    val paddingH = 12.dp
    val gap = 6.dp
    val icon = 24.dp
    val secondStyle = HhType.secondary.copy(fontSize = 12.sp)

    /** What a third of the tray shows in place of a label (18 walk-through): an icon, or a sound pad's number and waves. */
    val thirdIcon = 36.dp
    val thirdNumber = HhType.buttonLabel.copy(fontSize = 34.sp, lineHeight = 34.sp)
    val thirdWavesWidth = 18.dp
    val thirdWavesHeight = 30.dp
    val lineGap = 6.dp
    val pillHeight = 24.dp
    val pillPaddingH = 10.dp
    val pillStyle = HhType.labelSmall

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
    const val ON = "ON"
}

/** The "ON" pill a lit mode tile shows on its right; the text is tagged [tag] and not spoken, the tile says it. */
@Composable
fun HouseModeOnPill(tag: String) {
    val colors = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .shownNotSpoken()
            .height(HouseModeDimens.pillHeight)
            .background(colors.accentInk, RoundedCornerShape(percent = 50))
            .padding(horizontal = HouseModeDimens.pillPaddingH),
    ) {
        Text(HouseModeWords.ON, style = HouseModeDimens.pillStyle, color = colors.accent, maxLines = 1, modifier = Modifier.testTag(tag))
    }
}

/**
 * A mode's tray tile, narrow enough for half the tray: [icon] with [trailing] on its right, then [label] and [second]
 * under them, all spoken as [spoken]; what didn't happen is its tray cell's line, under the whole row. A tap does nothing
 * unless [enabled]. Its tags start with [tag]; [second]'s is [secondTag]. [clickLabel] is what TalkBack offers for a tap,
 * where "Turn on" and "Turn off" would be wrong.
 */
@Composable
fun HouseModeTile(
    tag: String,
    icon: String,
    label: String,
    on: Boolean,
    second: String?,
    secondTag: String,
    spoken: String,
    enabled: Boolean,
    onTap: () -> Unit,
    clickLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Culvery.colors
    val ink = if (on) colors.accentInk else colors.ink
    Column(
        verticalArrangement = Arrangement.spacedBy(HouseModeDimens.gap, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag)
            .clip(RoundedCornerShape(HouseModeDimens.radius))
            .background(if (on) colors.accent else colors.surf2)
            .hhClickable(onTap, label = clickLabel ?: if (on) HouseModeWords.CLICK_OFF else HouseModeWords.CLICK_ON, enabled = enabled, spoken = spoken)
            .padding(horizontal = HouseModeDimens.paddingH, vertical = HouseModeDimens.paddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            HhIcon(icon, size = HouseModeDimens.icon, filled = true, tint = if (on) ink else colors.accent)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Column(Modifier.shownNotSpoken()) {
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
    }
}

/**
 * A button in a third of the tray with no label (18 walk-through): [content] centred on [background], spoken as [spoken],
 * tagged [tag]. A tap does nothing unless [enabled], and the tile fades while it isn't, or while [faded] (a tap still
 * works). A tap flashes the tile's `accentSoft` for [PRESS_FLASH_MS].
 */
@Composable
fun HouseModeThirdTile(
    tag: String,
    spoken: String,
    clickLabel: String,
    enabled: Boolean,
    onTap: () -> Unit,
    background: Color = Culvery.colors.surf2,
    faded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(flashing) {
        if (flashing) {
            delay(PRESS_FLASH_MS)
            flashing = false
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag)
            .then(if (enabled && !faded) Modifier else Modifier.alpha(ControlTokens.DISABLED_ALPHA))
            .clip(RoundedCornerShape(HouseModeDimens.radius))
            .background(if (flashing) Culvery.colors.accentSoft else background)
            .hhClickable({
                flashing = true
                onTap()
            }, label = clickLabel, enabled = enabled, spoken = spoken),
    ) {
        Box(Modifier.shownNotSpoken()) { content() }
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

/** A mode page's turn button, [label] tagged `mode_turn`. It works once the mode's state is [read] and while no change is [running]. */
@Composable
fun HouseModeTurnButton(label: String, onClick: () -> Unit, read: Boolean, running: Boolean) {
    HhButton(label, onClick, Modifier.testTag("mode_turn"), tone = ButtonTone.Primary, enabled = !running && read, on = ButtonSurface.Bg)
}

/** What the turn button didn't do, under the page's head, tagged `mode_turn_line`. */
@Composable
fun HouseModeTurnLine(line: String?) {
    line?.let { HhStatusLine(it, "mode_turn_line") }
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
