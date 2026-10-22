package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.ALL_DAY_LABEL
import uk.co.siland.culvery.capability.calendar.CLASHES_ALLOWED
import uk.co.siland.culvery.capability.calendar.CLASHES_ALLOWED_TAIL
import uk.co.siland.culvery.capability.calendar.EDITOR_LENGTHS
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.busyHoursAndCheck
import uk.co.siland.culvery.capability.calendar.clashLine
import uk.co.siland.culvery.capability.calendar.freeAtLine
import uk.co.siland.culvery.capability.calendar.freeLine
import uk.co.siland.culvery.capability.calendar.lengthChip
import uk.co.siland.culvery.capability.calendar.lengthLabel
import uk.co.siland.culvery.capability.calendar.spansDays
import uk.co.siland.culvery.capability.calendar.timeValue
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ControlType
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSegmented
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.clockText

internal const val AT_A_TIME = "At a time"
internal const val OTHER_TIME = "Other time"
internal const val ALL_DAY_ONLY = "An event over several days is all day."

private const val MINUTES = "Minutes"
private const val HOW_LONG = "How long"
private const val BUSY = "busy"
private const val HOURS_A_ROW = 6
private val HOUR_GROUPS = listOf("Morning" to 7..11, "Afternoon" to 12..17, "Evening" to 18..22)
private val CHIP_HOURS = HOUR_GROUPS.first().second.first..HOUR_GROUPS.last().second.last
private val MINUTE_CHIPS = listOf(0, 15, 30, 45)
private val HOUR_MINUTE_TAG: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm", Locale.ENGLISH)

/** What the tablet can say about the chosen time (9 §3.1, §3.4): the day's busy hours and whether everyone chosen is free. */
internal data class TimeCheck(val busyHours: Set<Int>, val result: FreeCheck)

private val NO_CHECK = TimeCheck(emptySet(), FreeCheck.None)

/**
 * Reads [held] once for each change of who, day, start or length; nothing without a day. [order] is the household's, so
 * the first busy person is named; the event being changed never clashes with itself.
 */
@Composable
internal fun rememberTimeCheck(form: EditorForm, held: Held?, order: List<PersonId>): TimeCheck {
    val who = form.who
    val day = form.day
    val start = form.startTime
    val length = form.length
    return remember(held, who, day, start, length, order) {
        if (held == null || day == null) return@remember NO_CHECK
        val except = (form.mode as? EditorForm.Mode.Edit)?.original?.ref
        val from = start?.let(day::atTime)
        val (busyHours, result) = held.busyHoursAndCheck(who, day, from, (from ?: day.atStartOfDay()) + length, form.zone, except, order)
        TimeCheck(busyHours, result)
    }
}

private fun overDays(form: EditorForm): Boolean {
    val day = form.day ?: return false
    val from = day.atTime(form.startTime ?: return false)
    return spansDays(from, from + form.length)
}

/** The result line's icon: `check_circle` when free, `warning` on a clash, none when the tablet can't say. */
internal fun checkIcon(check: FreeCheck): String? = when (check) {
    is FreeCheck.Free -> Icons.CHECK_CIRCLE
    is FreeCheck.Busy -> Icons.WARNING
    FreeCheck.None -> null
}

/**
 * Handoff 6e and 6i: All day | At a time; then the hours in three groups, with the hours the chosen people are busy edged
 * and dotted in `weather`; once there is an hour, its minutes, how long, and the result with its free or clash line and
 * the nearest free starts. All day, a length and an Until pick call [onPicked]; an hour, a minute or a free start don't,
 * so the rest can still be set. "Other time" and "Until…" ask [onPicker] for the start or end picker. While the event
 * lasts more than a day, At a time is locked and a tap on it says why (9 §3.1).
 */
@Composable
internal fun TimePane(form: EditorForm, check: TimeCheck, household: EditorPeople, onPicker: (EditorPicker) -> Unit, onPicked: () -> Unit) {
    // At a time with no hour yet is the pane's own state: the row still reads All day.
    var atATime by remember { mutableStateOf(form.startTime != null) }
    var explainLock by remember { mutableStateOf(false) }
    val timed = !form.multiDay && (atATime || form.startTime != null)
    val nameOf = { id: PersonId -> household.byId[id]?.name.orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        HhSegmented(
            options = listOf(ALL_DAY_LABEL, AT_A_TIME),
            selected = if (timed) 1 else 0,
            onSelect = { i ->
                if (i == 0) {
                    atATime = false
                    form.chooseAllDay()
                    onPicked()
                } else {
                    atATime = true
                }
            },
            tag = "time_mode",
            locked = if (form.multiDay) setOf(1) else emptySet(),
            onLocked = { explainLock = true },
        )
        if (form.multiDay && explainLock) LockedNote(ALL_DAY_ONLY, "time_all_day_only", live = true)
        if (timed) {
            Hours(form, check.busyHours, onOther = { onPicker(EditorPicker.StartTime) })
            if (form.startTime != null) {
                MinutesAndLength(form, onUntil = { onPicker(EditorPicker.EndTime) }, onLength = onPicked)
                form.endError?.let { HhStatusLine(it, tag = "time_end_error", icon = Icons.ERROR, style = ControlType.fieldError) }
                Result(form, check.result, nameOf)
            }
        }
    }
}

/** Morning, Afternoon and Evening, six a row, Other time last; an hour outside them shows as Other time chosen. */
@Composable
private fun Hours(form: EditorForm, busy: Set<Int>, onOther: () -> Unit) {
    val hour = form.startTime?.hour
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        HOUR_GROUPS.forEachIndexed { i, (name, hours) ->
            val last = i == HOUR_GROUPS.lastIndex
            Group(name, "hour_group_$i") {
                // As tall as the tallest chip, so a two-line Other time keeps the row even.
                Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.hourGap), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    hours.forEach { h ->
                        TimeChip(
                            label = clockText(LocalTime.of(h, 0)),
                            selected = hour == h,
                            tag = "hour_%02d".format(h),
                            busy = h in busy,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            fill = true,
                        ) { form.chooseHour(h) }
                    }
                    if (last) {
                        TimeChip(
                            label = OTHER_TIME,
                            selected = hour != null && hour !in CHIP_HOURS,
                            tag = "hour_other",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            fill = true,
                            onClick = onOther,
                        )
                    }
                    repeat(HOURS_A_ROW - hours.count() - if (last) 1 else 0) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** :00 to :45, then 30 min to 2 hours and Until…, which reads "Until 18:30" for any other length. */
@Composable
private fun MinutesAndLength(form: EditorForm, onUntil: () -> Unit, onLength: () -> Unit) {
    val minute = form.startTime?.minute
    val custom = form.length !in EDITOR_LENGTHS
    Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.timeColumnsGap)) {
        Group(MINUTES, "minute_group") {
            Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.timeChipGap)) {
                MINUTE_CHIPS.forEach { m ->
                    TimeChip(":%02d".format(m), minute == m, "minute_%02d".format(m)) { form.chooseMinute(m) }
                }
            }
        }
        Group(HOW_LONG, "length_group") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(EditorDimens.timeChipGap), verticalArrangement = Arrangement.spacedBy(EditorDimens.timeChipGap)) {
                EDITOR_LENGTHS.forEach { length ->
                    TimeChip(lengthChip(length), form.length == length, "length_${length.toMinutes()}", spoken = lengthLabel(length)) {
                        form.chooseLength(length)
                        onLength()
                    }
                }
                TimeChip(form.untilTimeLabel, custom, "length_until", onClick = onUntil)
            }
        }
    }
}

/** [name] as a heading over [content]. */
@Composable
private fun Group(name: String, tag: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.timeLabelGap)) {
        CapsHeading(name, Modifier.testTag(tag))
        content()
    }
}

/** A `surf2` chip in tabular figures, filling [modifier]'s size when [fill]; [busy], a 1 dp `weather` edge (unless chosen) and a `weather` dot in its top corner. */
@Composable
private fun TimeChip(
    label: String,
    selected: Boolean,
    tag: String,
    busy: Boolean = false,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    role: Role = Role.RadioButton,
    spoken: String? = null,
    onClick: () -> Unit,
) {
    val c = Culvery.colors
    Box(modifier) {
        HhChoiceChip(
            label = label,
            selected = selected,
            tag = tag,
            onClick = onClick,
            unselectedFill = c.surf2,
            role = role,
            spokenName = if (busy) "${spoken ?: label}, $BUSY" else spoken,
            labelStyle = CalendarType.timeChip,
            modifier = (if (fill) Modifier.fillMaxSize() else Modifier).then(
                if (busy && !selected) Modifier.border(EditorDimens.busyEdge, c.weather, RoundedCornerShape(percent = 50)) else Modifier,
            ),
        )
        // In the corner rather than after the words, so a large font keeps the time on one line.
        if (busy) {
            Spacer(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = EditorDimens.busyDotInset, end = EditorDimens.busyDotInset)
                    .testTag("${tag}_dot")
                    .size(EditorDimens.busyDot)
                    .clip(CircleShape)
                    .background(c.weather),
            )
        }
    }
}

/**
 * Under a rule: the range, and what the tablet knows of it, said as it changes: "Mia is free then" in `accent`, or the
 * clash in `weather`, then the nearest free starts for the same length; nothing past what it holds (9 §3.1).
 */
@Composable
private fun Result(form: EditorForm, check: FreeCheck, nameOf: (PersonId) -> String) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.resultTop)) {
        Box(Modifier.fillMaxWidth().height(EditorDimens.resultRule).background(c.surf3))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorDimens.resultGap),
            modifier = Modifier.testTag("time_result").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(timeValue(form), style = CalendarType.timeResult, color = c.ink, maxLines = 1)
            val line = when (check) {
                is FreeCheck.Free -> freeLine(check, nameOf)
                is FreeCheck.Busy -> clashLine(check, single = form.who.size == 1, overDays = overDays(form), nameOf, form.zone)
                FreeCheck.None -> null
            }
            val icon = checkIcon(check)
            if (line != null && icon != null) {
                val tint = if (check is FreeCheck.Busy) c.weather else c.accent
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorDimens.checkIconGap)) {
                    HhIcon(icon, size = EditorDimens.checkIcon, filled = true, tint = tint, modifier = Modifier.testTag("time_result_icon"))
                    Text(line, style = CalendarType.timeCheck, color = tint)
                }
            }
        }
        if (check is FreeCheck.Busy) FreeStarts(form, check.freeStarts, nameOf)
    }
}

/** "Mia is free at" 14:45 16:15 "or keep it; clashes are allowed.", or with no free start, "Clashes are allowed." */
@Composable
private fun FreeStarts(form: EditorForm, starts: List<LocalTime>, nameOf: (PersonId) -> String) {
    val mute = Culvery.colors.mute
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.freeGap),
        verticalArrangement = Arrangement.spacedBy(EditorDimens.freeGap),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("time_free"),
    ) {
        if (starts.isEmpty()) {
            Text(CLASHES_ALLOWED, style = CalendarType.timeFreeAt, color = mute, modifier = Modifier.testTag("time_free_tail"))
            return@FlowRow
        }
        Text(freeAtLine(form.who.map(nameOf)), style = CalendarType.timeFreeAt, color = mute, modifier = Modifier.testTag("time_free_at"))
        starts.forEach { start ->
            TimeChip(clockText(start), selected = false, tag = "time_free_${start.format(HOUR_MINUTE_TAG)}", role = Role.Button) {
                form.chooseTime(start)
            }
        }
        Text(CLASHES_ALLOWED_TAIL, style = CalendarType.timeFreeAt, color = mute, modifier = Modifier.testTag("time_free_tail"))
    }
}
