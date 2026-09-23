package uk.co.siland.househub.core.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.siland.househub.core.access.PinError
import uk.co.siland.househub.core.access.PinHasher
import uk.co.siland.househub.core.access.PinPromptController
import uk.co.siland.househub.core.ui.HhIcon
import uk.co.siland.househub.core.ui.HhType
import uk.co.siland.househub.core.ui.HouseHub

@Composable
fun PinPadHost(controller: PinPromptController) {
    val request by controller.request.collectAsState()
    request?.let { r ->
        // Keyed on the request so each retry starts with an empty field.
        key(r) {
            PinPadSheet(
                label = r.label,
                error = r.error,
                lockedUntilMillis = r.lockedUntilMillis,
                onSubmit = controller::submit,
                onCancel = controller::cancel,
            )
        }
    }
}

@Composable
fun PinPadSheet(
    label: String,
    error: PinError?,
    lockedUntilMillis: Long?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = HouseHub.colors
    var digits by remember { mutableStateOf("") }
    // Counts down from the initial value rather than re-reading the wall clock, so tests with a
    // virtual frame clock stay deterministic.
    val secondsLeft by produceState(secondsUntil(lockedUntilMillis), lockedUntilMillis) {
        while (value > 0) {
            delay(1_000)
            value -= 1
        }
    }
    val locked = secondsLeft > 0
    val canType = !locked && digits.length < PinHasher.PIN_LENGTH
    val message = when {
        locked -> "Too many tries — wait ${secondsLeft}s"
        error is PinError.WrongPin -> "Wrong PIN"
        error is PinError.NotAllowed -> "${error.name} can't do that"
        else -> ""
    }
    val type: (String) -> Unit = { d ->
        digits += d
        if (digits.length == PinHasher.PIN_LENGTH) onSubmit(digits)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pin_scrim")
            .background(Color(0x8C000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.bg)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp),
        ) {
            Text("Enter your PIN", style = HhType.screenTitle, color = c.ink)
            Text(label, style = HhType.secondary, color = c.mute)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(PinHasher.PIN_LENGTH) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < digits.length) c.ink else c.surf3),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message, style = HhType.label, color = c.ink, modifier = Modifier.height(20.dp))
            Spacer(Modifier.height(12.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { d -> DigitKey(d, enabled = canType) { type(d) } }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.size(80.dp))
                DigitKey("0", enabled = canType) { type("0") }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag("pin_backspace")
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(enabled = digits.isNotEmpty() && !locked) { digits = digits.dropLast(1) },
                ) {
                    HhIcon("backspace", size = 30.dp, tint = c.mute)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cancel",
                style = HhType.buttonLabel,
                color = c.mute,
                modifier = Modifier
                    .testTag("pin_cancel")
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 22.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun DigitKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    val c = HouseHub.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("pin_key_$digit")
            .size(80.dp)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(digit, style = HhType.pinDigit, color = c.ink)
    }
}

private fun secondsUntil(until: Long?): Int =
    until?.let { ((it - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0
