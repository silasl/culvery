package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** A radio row's size and look (9 §4.11, handoff 5b): at least 59 dp, radius 16, a 20 dp radio with a 6 dp dot 13 from the words. */
object RadioRowTokens {
    val minHeight = 59.dp
    val radius = 16.dp
    val paddingH = 17.dp
    val paddingV = 9.dp
    val radio = 20.dp
    val radioDot = 6.dp
    val radioGap = 13.dp
    val ring = 2.dp

    /** The gap between rows in a group. */
    val gap = 8.dp
    val titleStyle = HhType.rowTitle.copy(fontWeight = FontWeight.W700)
    val lineStyle = HhType.secondary.copy(fontSize = 13.sp)
}

/**
 * One choice among a few, full width: a radio then [title] and, under it, [line]. `surf2`; chosen, `accentSoft` with a 2 dp
 * `accent` ring inside. A [locked] choice is muted and not tappable. [line] is `{tag}_note`. Put the rows in a `selectableGroup`.
 */
@Composable
fun HhRadioRow(title: String, line: String? = null, selected: Boolean, tag: String, locked: Boolean = false, onClick: () -> Unit) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(RadioRowTokens.radius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RadioRowTokens.radioGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected }
            .hhClickable(onClick, role = Role.RadioButton, enabled = !locked)
            .fillMaxWidth()
            .heightIn(min = RadioRowTokens.minHeight)
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surf2)
            .then(if (selected) Modifier.border(RadioRowTokens.ring, c.accent, shape) else Modifier)
            .padding(horizontal = RadioRowTokens.paddingH, vertical = RadioRowTokens.paddingV),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(RadioRowTokens.radio).border(RadioRowTokens.ring, if (selected && !locked) c.accent else c.mute, CircleShape),
        ) {
            if (selected) Box(Modifier.size(RadioRowTokens.radioDot).clip(CircleShape).background(c.accent))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RadioRowTokens.titleStyle, color = if (locked) c.mute else c.ink)
            if (line != null) Text(line, style = RadioRowTokens.lineStyle, color = c.mute, modifier = Modifier.testTag("${tag}_note"))
        }
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
