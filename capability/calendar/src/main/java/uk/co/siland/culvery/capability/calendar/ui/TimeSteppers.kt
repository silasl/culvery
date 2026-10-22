package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import java.time.LocalTime
import uk.co.siland.culvery.capability.calendar.ClockField
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill
import uk.co.siland.culvery.core.ui.readAsOne

private const val NOT_AVAILABLE = "not available"

/**
 * Hour and minute steppers around a colon, on a `surf` card. [onStep] says which field moved and which way; the caller does the
 * arithmetic. A button [enabled] says false for is dimmed, cannot be pressed and is spoken as "not available".
 */
@Composable
internal fun TimeSteppers(
    time: LocalTime,
    tagPrefix: String = "",
    onStep: (ClockField, Boolean) -> Unit,
    enabled: (ClockField, Boolean) -> Boolean = { _, _ -> true },
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeColumnGap, Alignment.CenterHorizontally),
        modifier = modifier,
    ) {
        Stepper(time.hour, "${tagPrefix}hour", "Hour", ClockField.Hour, onStep, enabled)
        Text(
            ":", style = CalendarType.timeColon, color = Culvery.colors.ink,
            modifier = Modifier.padding(bottom = CalendarDimens.colonBottom).semantics { hideFromAccessibility() },
        )
        Stepper(time.minute, "${tagPrefix}minute", "Minute", ClockField.Minute, onStep, enabled)
    }
}

@Composable
private fun Stepper(
    value: Int,
    tag: String,
    name: String,
    field: ClockField,
    onStep: (ClockField, Boolean) -> Unit,
    enabled: (ClockField, Boolean) -> Boolean,
) {
    val c = Culvery.colors
    var stepped by remember { mutableStateOf(false) }
    // Read up, value, down, then the next column.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.stepperGap),
        modifier = Modifier.readAsOne(),
    ) {
        StepButton(Icons.EXPAND_LESS, "${tag}_up", "$name up", enabled(field, true)) { stepped = true; onStep(field, true) }
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
        StepButton(Icons.EXPAND_MORE, "${tag}_down", "$name down", enabled(field, false)) { stepped = true; onStep(field, false) }
    }
}

/** On the picker card (`surf`), so the quiet fill on `surf`. */
@Composable
private fun StepButton(icon: String, tag: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.stepperWidth, CalendarDimens.stepperHeight)
            .alpha(if (enabled) 1f else HhAlpha.PAST_DAY)
            .clip(RoundedCornerShape(CalendarDimens.stepperRadius))
            .background(quietFill(ButtonSurface.Surf, c))
            .hhClickable(onClick, enabled = enabled)
            .semantics { if (!enabled) stateDescription = NOT_AVAILABLE },
    ) {
        HhIcon(icon, size = CalendarDimens.stepperIcon, tint = c.ink, contentDescription = label)
    }
}
