package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.DAYS_PER_WEEK
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.dotColours
import uk.co.siland.culvery.capability.calendar.peopleByDay
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable

/** What TalkBack hears for something that cannot be used now. */
internal const val NOT_AVAILABLE = "not available"
private const val EARLIER_MONTHS = "Earlier months"
private const val LATER_MONTHS = "Later months"
private const val IN_THE_RANGE = "in the range"
private const val FIRST_DAY = "first day"
private const val LAST_DAY = "last day"
private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** The alpha a day cell draws at, for tests: [HhAlpha.PAST_DAY], [HhAlpha.OUTSIDE_MONTH] or 1. */
internal val DayFade = SemanticsPropertyKey<Float>("DayFade")
private var SemanticsPropertyReceiver.dayFade by DayFade

/** The whole weeks, Monday first, that hold [month]: five or six of them. */
internal fun monthCells(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
}

/** The people from [held] on each day [month]'s grid shows, ready for [DateGrid]'s dots. */
@Composable
internal fun rememberGridDots(held: Held?, month: YearMonth, household: EditorPeople, zone: ZoneId): Map<LocalDate, List<Person>> =
    remember(held, month, household, zone) {
        val cells = monthCells(month)
        held?.peopleByDay(cells.first(), cells.last(), household.order, zone).orEmpty()
            .mapValues { (_, ids) -> ids.mapNotNull { household.byId[it] } }
    }

/**
 * A month header with ‹ ›, M T W T F S S, then the month's weeks. Days in [chosen] are filled, [band] runs behind its days,
 * [dots] gives the people on a day (up to three dots) and [spoken] what TalkBack adds after the date. A day [allowed] says false
 * for is dimmed, cannot be tapped and says "not available". Past days and days outside [month] fade but can be tapped.
 */
@Composable
internal fun DateGrid(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    today: LocalDate,
    chosen: Set<LocalDate>,
    band: ClosedRange<LocalDate>?,
    allowed: (LocalDate) -> Boolean,
    dots: Map<LocalDate, List<Person>>,
    spoken: (LocalDate) -> String,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(month) { monthCells(month).chunked(DAYS_PER_WEEK) }
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.monthGap), modifier = modifier) {
        MonthHeader(month, onEarlier = { onMonth(month.minusMonths(1)) }, onLater = { onMonth(month.plusMonths(1)) })
        WeekdayLetters()
        Column {
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEachIndexed { column, date ->
                        DayCell(
                            date, column, today, month, date in chosen, band, allowed(date), dots[date].orEmpty(), spoken(date),
                            Modifier.weight(1f),
                        ) { onPick(date) }
                    }
                }
            }
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
 * filled `accent` when [isChosen], ringed when today. A [band] runs behind the circles, each half rounded where the range ends or the line wraps.
 * Outside the month, past or not [allowed], a day fades, unless it is in the band or chosen.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    column: Int,
    today: LocalDate,
    month: YearMonth,
    isChosen: Boolean,
    band: ClosedRange<LocalDate>?,
    allowed: Boolean,
    on: List<Person>,
    spokenExtra: String,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val c = Culvery.colors
    val inBand = band != null && date in band
    val fade = when {
        !allowed -> HhAlpha.PAST_DAY
        isChosen || inBand -> 1f
        YearMonth.from(date) != month -> HhAlpha.OUTSIDE_MONTH
        date < today -> HhAlpha.PAST_DAY
        else -> 1f
    }
    val isToday = date == today
    val name = listOf(dateCellName(date, today), spokenExtra).filter { it.isNotEmpty() }.joinToString(", ")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("day_$date")
            .height(EditorDimens.dayCell)
            .alpha(fade)
            .hhClickable(onTap, enabled = allowed, spoken = name)
            .semantics {
                dayFade = fade
                selected = isChosen
                when {
                    !allowed -> stateDescription = NOT_AVAILABLE
                    inBand -> stateDescription = if (date == band.start) FIRST_DAY else if (date == band.endInclusive) LAST_DAY else IN_THE_RANGE
                }
            },
    ) {
        if (inBand) {
            Row(Modifier.fillMaxWidth().height(EditorDimens.dayCircle)) {
                BandHalf(show = date != band.start, rounded = column == 0, start = true, Modifier.weight(1f))
                BandHalf(show = date != band.endInclusive, rounded = column == DAYS_PER_WEEK - 1, start = false, Modifier.weight(1f))
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EditorDimens.dayDotsTop, Alignment.CenterVertically),
            modifier = Modifier
                .testTag("day_${date}_circle")
                .size(EditorDimens.dayCircle)
                .clip(CircleShape)
                .background(if (isChosen) c.accent else Color.Transparent)
                .then(if (isToday && !isChosen) Modifier.border(EditorDimens.todayRing, c.accent, CircleShape) else Modifier),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = CalendarType.dayDate.copy(fontWeight = if (isToday || isChosen) FontWeight.W700 else FontWeight.W500),
                color = if (isChosen) c.accentInk else c.ink,
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
