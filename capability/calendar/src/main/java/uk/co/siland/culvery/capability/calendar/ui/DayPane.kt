package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.dotColours
import uk.co.siland.culvery.capability.calendar.peopleOn
import uk.co.siland.culvery.capability.calendar.spokenWho
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ControlType
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

internal const val LASTS_MORE_THAN_A_DAY = "Lasts more than a day"
internal const val TAP_FIRST_THEN_LAST = "Tap the first day, then the last"

private const val PICK_TODAY = "Today"
private const val PICK_TOMORROW = "Tomorrow"
private const val EARLIER_MONTHS = "Earlier months"
private const val LATER_MONTHS = "Later months"
private const val IN_THE_RANGE = "in the range"
private const val FIRST_DAY = "first day"
private const val LAST_DAY = "last day"
private const val DAYS_A_WEEK = 7
private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val FULL_WEEKDAY_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** The alpha a day cell draws at, for tests: [HhAlpha.PAST_DAY], [HhAlpha.OUTSIDE_MONTH] or 1. */
internal val DayFade = SemanticsPropertyKey<Float>("DayFade")
private var SemanticsPropertyReceiver.dayFade by DayFade

/**
 * Handoff 6c's quick picks: Today, Tomorrow, the first Saturday or Sunday after tomorrow by its date ("Sat 26 Sep"), and
 * the same weekday next week ("Wednesday next week").
 */
internal fun quickPicks(today: LocalDate): List<Pair<String, LocalDate>> {
    val weekend = generateSequence(today.plusDays(2)) { it.plusDays(1) }.first { it.dayOfWeek in WEEKEND }
    val nextWeek = today.plusWeeks(1)
    return listOf(
        PICK_TODAY to today,
        PICK_TOMORROW to today.plusDays(1),
        weekend.format(SHORT_DAY) to weekend,
        "${nextWeek.format(FULL_WEEKDAY_NAME)} next week" to nextWeek,
    )
}

/** The whole weeks, Monday first, that hold [month]: five or six of them. */
internal fun monthCells(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
}

/**
 * Handoff 6c and 6d: quick picks, then a month of days with up to three dots for the people already on each from [held],
 * and "Lasts more than a day", which turns taps into a first and a last day (9 §3.1). Any date can be picked. A day, or a
 * range's last day within the 60-day cap, calls [onPicked].
 */
@Composable
internal fun DayPane(form: EditorForm, held: Held?, people: List<Person>, onPicked: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(form.day ?: form.today)) }
    val byId = remember(people) { (people + Person.Family).associateBy { it.id } }
    val order = remember(people) { people.map { it.id } }
    val pick = { date: LocalDate ->
        form.tapDay(date)
        if (!form.awaitingLastDay && form.untilError == null) onPicked()
    }
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        QuickPicks(form) { date ->
            month = YearMonth.from(date)
            pick(date)
        }
        Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.monthGap)) {
            MonthHeader(month, onEarlier = { month = month.minusMonths(1) }, onLater = { month = month.plusMonths(1) })
            WeekdayLetters()
            Column {
                monthCells(month).chunked(DAYS_A_WEEK).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEachIndexed { column, date ->
                            val on = held?.peopleOn(date, order, form.zone).orEmpty().mapNotNull { byId[it] }
                            DayCell(date, column, form, month, on, Modifier.weight(1f)) { pick(date) }
                        }
                    }
                }
            }
        }
        form.untilError?.let { HhStatusLine(it, tag = "day_error", icon = Icons.ERROR, style = ControlType.fieldError) }
        HhSwitchRow(LASTS_MORE_THAN_A_DAY, line = TAP_FIRST_THEN_LAST.takeIf { form.multiDay }, checked = form.multiDay, tag = "day_multi") {
            form.setMultiDay(it)
        }
    }
}

/** In a range a quick pick is a first tap; it reads as chosen while it is the one day. */
@Composable
private fun QuickPicks(form: EditorForm, onPick: (LocalDate) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EditorDimens.quickGap), verticalArrangement = Arrangement.spacedBy(EditorDimens.quickGap)) {
        quickPicks(form.today).forEachIndexed { i, (label, date) ->
            HhChoiceChip(
                label = label,
                selected = form.day == date && form.until == null,
                tag = "day_quick_$i",
                onClick = { onPick(date) },
                unselectedFill = Culvery.colors.surf2,
            )
        }
    }
}

/** "September 2026", then ‹ › in one `surf2` pill; any month can be shown, past ones included. */
@Composable
private fun MonthHeader(month: YearMonth, onEarlier: () -> Unit, onLater: () -> Unit) {
    val c = Culvery.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            month.format(MONTH_YEAR),
            style = CalendarType.dayMonth,
            color = c.ink,
            modifier = Modifier.weight(1f).testTag("month_title").semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Row(Modifier.clip(CircleShape).background(c.surf2)) {
            HhRoundButton(Icons.CHEVRON_LEFT, EARLIER_MONTHS, onEarlier, Modifier.testTag("month_prev"), on = ButtonSurface.Surf)
            HhRoundButton(Icons.CHEVRON_RIGHT, LATER_MONTHS, onLater, Modifier.testTag("month_next"), on = ButtonSurface.Surf)
        }
    }
}

/** M T W T F S S; each cell already says its weekday. */
@Composable
private fun WeekdayLetters() {
    Row(Modifier.fillMaxWidth().testTag("month_letters")) {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                style = CalendarType.dayLetter,
                color = Culvery.colors.mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { hideFromAccessibility() },
            )
        }
    }
}

/**
 * One day of [month]'s grid: a circle with the date and up to three dots for the people [on] it, whom TalkBack names too;
 * filled `accent` when chosen or a range's end, ringed when today. A range's band runs behind the circles, each half rounded where the range ends or the line wraps.
 * Outside the month or past, a day fades, unless it is in the range.
 */
@Composable
private fun DayCell(date: LocalDate, column: Int, form: EditorForm, month: YearMonth, on: List<Person>, modifier: Modifier, onTap: () -> Unit) {
    val c = Culvery.colors
    val first = form.day
    val last = form.until?.takeIf { form.multiDay }
    val chosen = date == first || date == last
    val banded = first != null && last != null && date in first..last
    val fade = when {
        chosen || banded -> 1f
        YearMonth.from(date) != month -> HhAlpha.OUTSIDE_MONTH
        date < form.today -> HhAlpha.PAST_DAY
        else -> 1f
    }
    val isToday = date == form.today
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("day_$date")
            .height(EditorDimens.dayCell)
            .alpha(fade)
            .hhClickable(onTap, spoken = listOf(dateCellName(date, form.today), spokenWho(on)).filter { it.isNotEmpty() }.joinToString(", "))
            .semantics {
                dayFade = fade
                selected = chosen
                if (banded) stateDescription = if (date == first) FIRST_DAY else if (date == last) LAST_DAY else IN_THE_RANGE
            },
    ) {
        if (banded) {
            Row(Modifier.fillMaxWidth().height(EditorDimens.dayCircle)) {
                BandHalf(show = date != first, rounded = column == 0, start = true, Modifier.weight(1f))
                BandHalf(show = date != last, rounded = column == DAYS_A_WEEK - 1, start = false, Modifier.weight(1f))
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EditorDimens.dayDotsTop, Alignment.CenterVertically),
            modifier = Modifier
                .testTag("day_${date}_circle")
                .size(EditorDimens.dayCircle)
                .clip(CircleShape)
                .background(if (chosen) c.accent else Color.Transparent)
                .then(if (isToday && !chosen) Modifier.border(EditorDimens.todayRing, c.accent, CircleShape) else Modifier),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = CalendarType.dayDate.copy(fontWeight = if (isToday || chosen) FontWeight.W700 else FontWeight.W500),
                color = if (chosen) c.accentInk else c.ink,
                maxLines = 1,
                modifier = Modifier.testTag("day_${date}_date").semantics { hideFromAccessibility() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.dayDotGap), modifier = Modifier.height(EditorDimens.dayDot)) {
                dotColours(on).forEach { colour ->
                    Spacer(Modifier.testTag("day_${date}_dot").size(EditorDimens.dayDot).clip(CircleShape).background(Color(colour)))
                }
            }
        }
    }
}

/** Half a cell's band: flat where the range carries on, a rounded cap at a line's first or last column. */
@Composable
private fun BandHalf(show: Boolean, rounded: Boolean, start: Boolean, modifier: Modifier) {
    if (!show) {
        Spacer(modifier)
        return
    }
    val r = EditorDimens.bandRadius
    val shape = when {
        !rounded -> RectangleShape
        start -> RoundedCornerShape(topStart = r, bottomStart = r)
        else -> RoundedCornerShape(topEnd = r, bottomEnd = r)
    }
    Box(modifier.height(EditorDimens.dayCircle).clip(shape).background(Culvery.colors.accentSoft))
}
