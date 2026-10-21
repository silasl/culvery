package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import java.time.LocalTime
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.readAsOne

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60

/** The time picker's minute step (hand-off §7). */
internal const val MINUTE_STEP = 15

/** Hours step by 1 and wrap 23 ↔ 00 without changing the day. */
internal fun hourStep(hour: Int, up: Boolean): Int = Math.floorMod(hour + if (up) 1 else -1, HOURS_PER_DAY)

/** Minutes step through 00, 15, 30, 45 and wrap; an off-quarter minute moves to the next or previous quarter first. */
internal fun minuteStep(minute: Int, up: Boolean): Int = when {
    up -> (minute / MINUTE_STEP + 1) * MINUTE_STEP % MINUTES_PER_HOUR
    minute % MINUTE_STEP != 0 -> minute / MINUTE_STEP * MINUTE_STEP
    else -> Math.floorMod(minute - MINUTE_STEP, MINUTES_PER_HOUR)
}

/** Over the editor only: a scrim that cancels on a tap, with [content] centred on it. */
@Composable
internal fun PickerLayer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("picker_scrim")
                .background(ShellTokens.cardScrim)
                // A gesture, not a clickable: TalkBack never lands on the scrim; the card has Cancel.
                .pointerInput(Unit) { detectTapGestures { dismiss() } },
        )
        content()
    }
}

/** Hand-off §7 time picker: hour and minute steppers around the value, Cancel and Set time. */
@Composable
internal fun TimePickerCard(initial: LocalTime, onSet: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val c = Culvery.colors
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    PickerCard(CalendarDimens.timePickerWidth, CalendarDimens.timePickerGap, "time_picker", paneTitle = "Pick a time") {
        Text("Pick a time", style = CalendarType.pickerTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeColumnGap, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Stepper(hour, "hour", "Hour", onUp = { hour = hourStep(hour, up = true) }, onDown = { hour = hourStep(hour, up = false) })
            Text(
                ":", style = CalendarType.timeColon, color = c.ink,
                modifier = Modifier.padding(bottom = CalendarDimens.colonBottom).semantics { hideFromAccessibility() },
            )
            Stepper(minute, "minute", "Minute", onUp = { minute = minuteStep(minute, up = true) }, onDown = { minute = minuteStep(minute, up = false) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeButtonGap)) {
            HhButton("Cancel", onCancel, Modifier.weight(1f).testTag("picker_cancel"), size = ButtonSize.Large, on = ButtonSurface.Surf)
            HhButton(
                "Set time", { onSet(LocalTime.of(hour, minute)) }, Modifier.weight(1f).testTag("picker_set"),
                tone = ButtonTone.Primary, size = ButtonSize.Large, on = ButtonSurface.Surf,
            )
        }
    }
}

@Composable
internal fun PickerCard(width: Dp, gap: Dp, tag: String, paneTitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.paneTitle = paneTitle }
            .width(width)
            .clip(RoundedCornerShape(CalendarDimens.pickerRadius))
            .background(Culvery.colors.surf)
            // Taps on the card's own space mustn't reach the scrim underneath, which cancels.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(CalendarDimens.pickerPadding),
        content = content,
    )
}

@Composable
private fun Stepper(value: Int, tag: String, name: String, onUp: () -> Unit, onDown: () -> Unit) {
    val c = Culvery.colors
    var stepped by remember { mutableStateOf(false) }
    // Read up, value, down, then the next column.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.stepperGap),
        modifier = Modifier.readAsOne(),
    ) {
        StepButton(Icons.EXPAND_LESS, "${tag}_up", "$name up") { stepped = true; onUp() }
        Text(
            value.toString().padStart(2, '0'),
            style = CalendarType.timeValue,
            color = c.ink,
            // "Hour 7", not "zero seven"; spoken as it changes, not as the picker opens.
            modifier = Modifier.testTag("${tag}_value").semantics {
                contentDescription = "$name $value"
                if (stepped) liveRegion = LiveRegionMode.Polite
            },
        )
        StepButton(Icons.EXPAND_MORE, "${tag}_down", "$name down") { stepped = true; onDown() }
    }
}

/** On the picker card (`surf`), so the quiet fill on `surf`. */
@Composable
private fun StepButton(icon: String, tag: String, label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.stepperWidth, CalendarDimens.stepperHeight)
            .clip(RoundedCornerShape(CalendarDimens.stepperRadius))
            .background(quietFill(ButtonSurface.Surf, c))
            .hhClickable(onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.stepperIcon, tint = c.ink, contentDescription = label)
    }
}
