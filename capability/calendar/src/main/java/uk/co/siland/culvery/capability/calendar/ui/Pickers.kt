package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.ShellTokens

/** Which picker is drawn over the add/edit sheet. */
enum class EditorPicker { None, Date, Time }

/** A date-picker page is 5 weeks (2b-2 design D10). */
internal const val PICKER_WEEKS = 5
private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_PAGE = PICKER_WEEKS * DAYS_PER_WEEK
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60

/** The time picker's minute step (hand-off §7). */
internal const val MINUTE_STEP = 15

// ENGLISH, as elsewhere in the calendar: the hand-off's "1 Oct".
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The first page starts on this week's Monday. */
internal fun firstPageStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** The page holding [date], counted from the first page; negative for earlier pages. */
internal fun pageOf(date: LocalDate, today: LocalDate): Int =
    Math.floorDiv(ChronoUnit.DAYS.between(firstPageStart(today), date), DAYS_PER_PAGE.toLong()).toInt()

internal fun pageStart(page: Int, today: LocalDate): LocalDate = firstPageStart(today).plusDays(DAYS_PER_PAGE.toLong() * page)

/** "Mon 21 Sep – Sun 25 Oct". */
internal fun pageRangeLabel(start: LocalDate): String =
    "${start.format(SHORT_DAY)} – ${start.plusDays(DAYS_PER_PAGE - 1L).format(SHORT_DAY)}"

/** A cell reads its day of the month; the 1st reads "1 Oct". */
internal fun dateCellLabel(date: LocalDate): String = if (date.dayOfMonth == 1) date.format(DAY_MONTH) else date.dayOfMonth.toString()

/** Hours step by 1 and wrap 23 ↔ 00 without changing the day. */
internal fun hourStep(hour: Int, up: Boolean): Int = Math.floorMod(hour + if (up) 1 else -1, HOURS_PER_DAY)

/** Minutes step through 00, 15, 30, 45 and wrap; an off-quarter minute moves to the next or previous quarter first. */
internal fun minuteStep(minute: Int, up: Boolean): Int = when {
    up -> (minute / MINUTE_STEP + 1) * MINUTE_STEP % MINUTES_PER_HOUR
    minute % MINUTE_STEP != 0 -> minute / MINUTE_STEP * MINUTE_STEP
    else -> Math.floorMod(minute - MINUTE_STEP, MINUTES_PER_HOUR)
}

/** Over the sheet only (2b-2 design D4): a scrim that cancels on tap, with [content] centred on it. */
@Composable
internal fun PickerLayer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("picker_scrim")
                .background(ShellTokens.pinScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Cancel",
                    onClick = onDismiss,
                ),
        )
        content()
    }
}

/**
 * Hand-off §7 date picker with 2b-2 design D10's changes: pages of 5 weeks, ‹ and › to move between them, past
 * days dimmed but selectable. It opens on the page holding [selected]; tapping a day picks it.
 */
@Composable
internal fun DatePickerCard(today: LocalDate, selected: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val c = Culvery.colors
    var page by remember { mutableIntStateOf(pageOf(selected, today)) }
    val start = pageStart(page, today)
    PickerCard(CalendarDimens.datePickerWidth, CalendarDimens.datePickerGap, "date_picker") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.pageButtonGap)) {
            Column(Modifier.weight(1f)) {
                Text("Pick a date", style = CalendarType.pickerTitle, color = c.ink)
                Text(
                    pageRangeLabel(start),
                    style = CalendarType.pickerRange,
                    color = c.mute,
                    modifier = Modifier.padding(top = CalendarDimens.pickerRangeTop).testTag("date_range"),
                )
            }
            RoundButton("chevron_left", "Earlier weeks", "date_prev") { page-- }
            RoundButton("chevron_right", "Later weeks", "date_next") { page++ }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.dateCellGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.dateCellGap)) {
                (0 until DAYS_PER_WEEK).forEach { d ->
                    Text(
                        start.plusDays(d.toLong()).format(WEEKDAY),
                        style = CalendarType.pickerWeekday,
                        color = c.mute,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = CalendarDimens.weekdayPaddingV),
                    )
                }
            }
            (0 until PICKER_WEEKS).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.dateCellGap)) {
                    (0 until DAYS_PER_WEEK).forEach { d ->
                        val date = start.plusDays((week * DAYS_PER_WEEK + d).toLong())
                        DateCell(date, today, selected, Modifier.weight(1f)) { onPick(date) }
                    }
                }
            }
        }
        PickerButton(
            "Cancel", primary = false, tag = "picker_cancel",
            height = CalendarDimens.pickerCancelHeight, radius = CalendarDimens.pickerCancelRadius,
            modifier = Modifier.fillMaxWidth(), onClick = onCancel,
        )
    }
}

/** Hand-off §7 time picker: hour and minute steppers around the value, Cancel and Set time. */
@Composable
internal fun TimePickerCard(initial: LocalTime, onSet: (LocalTime) -> Unit, onCancel: () -> Unit) {
    val c = Culvery.colors
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    PickerCard(CalendarDimens.timePickerWidth, CalendarDimens.timePickerGap, "time_picker") {
        Text("Pick a time", style = CalendarType.pickerTitle, color = c.ink)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeColumnGap, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Stepper(hour, "hour", onUp = { hour = hourStep(hour, up = true) }, onDown = { hour = hourStep(hour, up = false) })
            Text(":", style = CalendarType.timeColon, color = c.ink, modifier = Modifier.padding(bottom = CalendarDimens.colonBottom))
            Stepper(minute, "minute", onUp = { minute = minuteStep(minute, up = true) }, onDown = { minute = minuteStep(minute, up = false) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeButtonGap)) {
            PickerButton(
                "Cancel", primary = false, tag = "picker_cancel",
                height = CalendarDimens.timeButtonHeight, radius = CalendarDimens.timeButtonRadius,
                modifier = Modifier.weight(1f), onClick = onCancel,
            )
            PickerButton(
                "Set time", primary = true, tag = "picker_set",
                height = CalendarDimens.timeButtonHeight, radius = CalendarDimens.timeButtonRadius,
                modifier = Modifier.weight(1f), onClick = { onSet(LocalTime.of(hour, minute)) },
            )
        }
    }
}

@Composable
internal fun PickerCard(width: Dp, gap: Dp, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .testTag(tag)
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
private fun DateCell(date: LocalDate, today: LocalDate, selected: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val c = Culvery.colors
    val isSelected = date == selected
    val shape = RoundedCornerShape(CalendarDimens.dateCellRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("date_cell_$date")
            .alpha(if (date.isBefore(today) && !isSelected) CalendarDimens.PAST_DAY_ALPHA else 1f)
            .height(CalendarDimens.dateCellHeight)
            .clip(shape)
            .background(if (isSelected) c.accent else c.surf2)
            .then(if (date == today) Modifier.border(CalendarDimens.dateRing, c.accent, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Text(dateCellLabel(date), style = CalendarType.pickerCell, color = if (isSelected) c.accentInk else c.ink, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: String, label: String, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.pageButton)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.pageButtonIcon, tint = c.ink, contentDescription = label)
    }
}

@Composable
private fun Stepper(value: Int, name: String, onUp: () -> Unit, onDown: () -> Unit) {
    val c = Culvery.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(CalendarDimens.stepperGap)) {
        StepButton("expand_less", "${name}_up", "More", onUp)
        Text(value.toString().padStart(2, '0'), style = CalendarType.timeValue, color = c.ink, modifier = Modifier.testTag("${name}_value"))
        StepButton("expand_more", "${name}_down", "Less", onDown)
    }
}

@Composable
private fun StepButton(icon: String, tag: String, label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(CalendarDimens.stepperWidth, CalendarDimens.stepperHeight)
            .clip(RoundedCornerShape(CalendarDimens.stepperRadius))
            .background(c.surf2)
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.stepperIcon, tint = c.ink, contentDescription = label)
    }
}

@Composable
internal fun PickerButton(
    text: String,
    primary: Boolean,
    tag: String,
    height: Dp,
    radius: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag(tag)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (primary) c.accent else c.surf2)
            .clickable(onClick = onClick),
    ) {
        Text(text, style = CalendarType.pickerButton, color = if (primary) c.accentInk else c.ink, maxLines = 1)
    }
}
