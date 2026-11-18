package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import uk.co.siland.culvery.core.repeat.EDITOR_REPEATS
import uk.co.siland.culvery.core.repeat.MAX_TIMES
import uk.co.siland.culvery.core.repeat.MIN_TIMES
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd
import uk.co.siland.culvery.core.repeat.nthWeekday
import uk.co.siland.culvery.core.repeat.repeatWords
import uk.co.siland.culvery.core.repeat.wording

const val KEEPS_GOING = "Keeps going"
const val STOPS_AFTER = "Stops after"
const val FEWER_TIMES = "Fewer times"
const val MORE_TIMES = "More times"

/** The repeat picker (handoff 6f): the stepper's 48 buttons 8 either side of its count, a 48 pill 19 in at its sides. */
object RepeatPickerTokens {
    val stepperGap = 8.dp
    val stepperPaddingH = 19.dp
}

object RepeatPickerType {
    /** 17 sp / 600: the stepper's count in tabular figures. */
    val count = HhType.rowTitle.copy(fontFeatureSettings = "tnum")
}

/**
 * The repeats worded from [day] as radio rows, a [RepeatPick.kept] rule first as its own; for weekly and every 2 weeks a
 * chip for each day, [day]'s own ticked and fixed; while it repeats, Keeps going | Stops after, and for Stops after one
 * stepper with the count and the last date. A row [lockedLine] gives a reason for is locked and shows it; [daysLocked]
 * chips can't be ticked, and [daysLockedLine] says why under them. A tap while [busy] changes nothing. [onPicked] follows
 * each row chosen.
 */
@Composable
fun HhRepeatPicker(
    pick: RepeatPick,
    day: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    nth: Int = nthWeekday(day),
    lockedLine: (RepeatChoice) -> String? = { null },
    daysLocked: Boolean = false,
    daysLockedLine: String? = null,
    busy: Boolean = false,
    onPicked: (RepeatChoice) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(ControlTokens.formGap), modifier = modifier) {
        HhRadioGroup {
            pick.kept?.let { kept ->
                val words = remember(kept, day, zone, today) { "As the event: ${repeatWords(kept, day, zone, today)}" }
                HhRadioRow(words, selected = pick.keeping, tag = "repeat_kept") { if (!busy) pick.keep() }
            }
            EDITOR_REPEATS.forEach { choice ->
                val line = lockedLine(choice)
                HhRadioRow(
                    choice.wording(day, nth, pick.extraDays),
                    line = line,
                    selected = !pick.keeping && pick.choice == choice,
                    tag = "repeat_${choice.name}",
                    locked = line != null,
                ) {
                    if (!busy) {
                        pick.choose(choice)
                        onPicked(choice)
                    }
                }
            }
        }
        if (pick.keeping || pick.choice == RepeatChoice.Never) return@Column
        if (pick.daysOn(day).isNotEmpty()) DayChips(pick, day.dayOfWeek, daysLocked, daysLockedLine, busy)
        val after = pick.end as? RepeatEnd.After
        HhSegmented(
            options = listOf(KEEPS_GOING, STOPS_AFTER),
            selected = if (after != null) 1 else 0,
            onSelect = {
                if (!busy) {
                    if (it == 0) pick.keepsGoing() else pick.stopsAfter()
                }
            },
            tag = "ends_mode",
        )
        if (after != null) Stepper(pick, after.times, day, nth, busy)
    }
}

@Composable
private fun DayChips(pick: RepeatPick, own: DayOfWeek, locked: Boolean, lockedLine: String?, busy: Boolean) {
    val chosen = pick.extraDays + own
    Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap), modifier = Modifier.testTag("repeat_days")) {
        DayOfWeek.entries.forEach { d ->
            HhChoiceChip(
                label = d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                selected = d in chosen,
                tag = "repeat_day_${d.name}",
                onClick = { if (!locked && !busy) pick.toggleDay(d, own) },
                enabled = !locked || d == own,
                unselectedFill = Culvery.colors.surf2,
                role = Role.Checkbox,
                locked = d == own,
            )
        }
    }
    if (locked && lockedLine != null) {
        Text(lockedLine, style = RadioRowTokens.lineStyle, color = Culvery.colors.mute, modifier = Modifier.testTag("repeat_days_note"))
    }
}

/** − "12 times · last on Sat 12 Dec" +: held, a button keeps stepping; each end disables its button. */
@Composable
private fun Stepper(pick: RepeatPick, times: Int, day: LocalDate, nth: Int, busy: Boolean) {
    val c = Culvery.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepeatPickerTokens.stepperGap)) {
        StepButton(Icons.REMOVE, FEWER_TIMES, "ends_fewer", enabled = times > MIN_TIMES) { if (!busy) pick.fewerTimes() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .testTag("ends_count")
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .heightIn(min = ControlTokens.chipHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(c.surf2)
                .padding(horizontal = RepeatPickerTokens.stepperPaddingH),
        ) {
            Text(pick.stepperLabel(day, nth).orEmpty(), style = RepeatPickerType.count, color = c.ink, maxLines = 1)
        }
        StepButton(Icons.ADD, MORE_TIMES, "ends_more", enabled = times < MAX_TIMES) { if (!busy) pick.moreTimes() }
    }
}

@Composable
private fun StepButton(icon: String, spoken: String, tag: String, enabled: Boolean, onStep: () -> Unit) {
    HhRoundButton(
        icon = icon,
        spoken = spoken,
        onClick = onStep,
        modifier = Modifier.testTag(tag),
        enabled = enabled,
        on = ButtonSurface.Surf,
        size = ControlTokens.chipHeight,
        iconSize = ButtonTokens.icon,
        repeatWhileHeld = true,
    )
}
