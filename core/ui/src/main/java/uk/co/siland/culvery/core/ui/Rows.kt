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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** One switch for TalkBack and the finger, in [HhListRow]'s container; [HhSwitch] only draws. [spokenName] replaces what TalkBack reads for the row. */
@Composable
fun HhSwitchRow(
    title: String,
    line: String?,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    minHeight: Dp = ListRowTokens.minHeight,
    spokenName: String? = null,
) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .listRowCard(c, minHeight)
            .testTag(tag)
            .hhToggleable(checked, enabled, onValueChange = onChange)
            .semantics { spokenName?.let { contentDescription = it } }
            .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd, top = ListRowTokens.paddingV, bottom = ListRowTokens.paddingV),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ListRowTokens.titleStyle, color = c.ink)
            if (line != null) Text(line, style = HhType.secondary, color = c.mute)
        }
        HhSwitch(checked, tag = "${tag}_switch", enabled = enabled)
    }
}

/** How long a Settings slider keeps the value let go while its save is on the way; a refused save then shows the stored value (9 §4.12). */
const val SETTINGS_HOLD_MS = 2_000L

/** A slider row's columns: the title takes one share, the track 1.5, and the value keeps room for "100 %". No vertical padding: the 48 dp track centres in the 69 dp row. */
object SliderRowTokens {
    const val TITLE_WEIGHT = 1f
    const val TRACK_WEIGHT = 1.5f
    val valueMinWidth = 56.dp
}

/**
 * One slider for Settings in [HhListRow]'s container: [title] left, [HhPercentSlider] (tag [tag]) in the middle, the value
 * ([valueLabel], `{tag}_value`) right. The row is `{tag}_row`. Saved on let go through [onLetGo]; a value that doesn't
 * come back as [percent] within [SETTINGS_HOLD_MS] is shown as [percent] again. Spoken "[spokenName], [spokenState]".
 */
@Composable
fun HhSliderRow(
    title: String,
    percent: Int,
    tag: String,
    onLetGo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..100,
    steps: Int = 0,
    enabled: Boolean = true,
    spokenName: String = title,
    spokenState: (Int) -> String = { "$it percent" },
    valueLabel: (Int) -> String = { "$it %" },
) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap),
        modifier = modifier
            .fillMaxWidth()
            .listRowCard(c)
            .testTag("${tag}_row")
            .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd),
    ) {
        Text(title, style = ListRowTokens.titleStyle, color = c.ink, modifier = Modifier.weight(SliderRowTokens.TITLE_WEIGHT))
        HhPercentSlider(
            title, percent, tag, Modifier.weight(SliderRowTokens.TRACK_WEIGHT),
            range = range, steps = steps, spokenName = spokenName, spokenState = spokenState, enabled = enabled, holdCeilingMs = SETTINGS_HOLD_MS,
            label = { shown ->
                Text(
                    valueLabel(shown.coerceIn(range)), style = HhType.body, color = c.mute, textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = SliderRowTokens.valueMinWidth).testTag("${tag}_value"),
                )
            },
            onLetGo = onLetGo,
        )
    }
}

/** A radio row's size and look (9 §4.11, handoff 5b): at least 59 dp, radius 16, a 20 dp radio with a 6 dp dot 13 from the words. */
object RadioRowTokens {
    val minHeight = 59.dp
    val radius = 16.dp
    val shape = RoundedCornerShape(radius)
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
    val shape = RadioRowTokens.shape
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
