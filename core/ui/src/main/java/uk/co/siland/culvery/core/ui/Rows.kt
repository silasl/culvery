package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val FROM = "From"
const val UNTIL = "Until"
const val EARLIER = "Earlier"
const val LATER = "Later"
const val TIME_STEP_MINUTES = 30L

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

fun clockText(time: LocalTime): String = time.format(CLOCK)

/** One switch for TalkBack and the finger, in [HhListRow]'s container; [HhSwitch] only draws. */
@Composable
fun HhSwitchRow(
    title: String,
    line: String?,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    minHeight: Dp = ListRowTokens.minHeight,
) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(ListRowTokens.radius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .border(CardTokens.edge, c.border, shape)
            .background(c.surf)
            .testTag(tag)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { if (enabled) onChange(it) })
            .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd, top = ListRowTokens.paddingV, bottom = ListRowTokens.paddingV),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ListRowTokens.titleStyle, color = c.ink)
            if (line != null) Text(line, style = HhType.secondary, color = c.mute)
        }
        HhSwitch(checked, onCheckedChange = null, tag = "${tag}_switch", enabled = enabled)
    }
}

/** A time with Earlier and Later buttons, half an hour a step; [subject] completes their spoken names. */
@Composable
fun HhTimeStepperRow(label: String, subject: String, time: LocalTime, tag: String, onTime: (LocalTime) -> Unit) {
    HhListRow(
        title = label,
        subtitle = clockText(time),
        modifier = Modifier.testTag(tag),
        trailing = {
            HhButton(EARLIER, { onTime(time.minusMinutes(TIME_STEP_MINUTES)) }, Modifier.testTag("${tag}_earlier"), on = ButtonSurface.Surf, spokenName = "$EARLIER, $subject")
            HhButton(LATER, { onTime(time.plusMinutes(TIME_STEP_MINUTES)) }, Modifier.testTag("${tag}_later"), on = ButtonSurface.Surf, spokenName = "$LATER, $subject")
        },
    )
}
