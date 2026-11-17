package uk.co.siland.culvery.core.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

enum class ClockField { Hour, Minute }

/** The time picker's sizes: 88 × 52 buttons radius 18 with a 32 icon, 8 above and below a 72 sp value, 14 either side of the colon. */
object TimeStepperTokens {
    val columnGap = 14.dp
    val width = 88.dp
    val height = 52.dp
    val radius = 18.dp
    val icon = 32.dp
    val gap = 8.dp
    val colonBottom = 10.dp

    /** 72 sp / 600, tabular, −2 tracking, line height 1: the hour and minute. */
    val value = HhType.headerValue.copy(fontSize = 72.sp, letterSpacing = (-2).sp, lineHeight = 72.sp)

    /** 64 sp / 600: the ":" between them. */
    val colon = HhType.headerValue.copy(fontSize = 64.sp)
}

/**
 * Hour and minute steppers around a colon, on a `surf` card. [onStep] says which field moved and which way; the caller does the
 * arithmetic. A button [enabled] says false for is dimmed, cannot be pressed and is spoken as "not available".
 */
@Composable
fun HhTimeSteppers(
    time: LocalTime,
    tagPrefix: String = "",
    onStep: (ClockField, Boolean) -> Unit,
    enabled: (ClockField, Boolean) -> Boolean = { _, _ -> true },
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TimeStepperTokens.columnGap, Alignment.CenterHorizontally),
        modifier = modifier,
    ) {
        Stepper(time.hour, "${tagPrefix}hour", "Hour", ClockField.Hour, onStep, enabled)
        Text(
            ":", style = TimeStepperTokens.colon, color = Culvery.colors.ink,
            modifier = Modifier.padding(bottom = TimeStepperTokens.colonBottom).semantics { hideFromAccessibility() },
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
        verticalArrangement = Arrangement.spacedBy(TimeStepperTokens.gap),
        modifier = Modifier.readAsOne(),
    ) {
        StepButton(Icons.EXPAND_LESS, "${tag}_up", "$name up", enabled(field, true)) { stepped = true; onStep(field, true) }
        Text(
            value.toString().padStart(2, '0'),
            style = TimeStepperTokens.value,
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
            .size(TimeStepperTokens.width, TimeStepperTokens.height)
            .alpha(if (enabled) 1f else HhAlpha.PAST_DAY)
            .clip(RoundedCornerShape(TimeStepperTokens.radius))
            .background(quietFill(ButtonSurface.Surf, c))
            .hhClickable(onClick, enabled = enabled)
            .semantics { if (!enabled) stateDescription = NOT_AVAILABLE },
    ) {
        HhIcon(icon, size = TimeStepperTokens.icon, tint = c.ink, contentDescription = label)
    }
}
