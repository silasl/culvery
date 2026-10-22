package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.ClockField
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventWhen
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.MAX_SPAN_DAYS
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.spokenWho
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons

internal const val STARTS_DATE_QUESTION = "Which day does it start?"
internal const val STARTS_TIME_QUESTION = "What time does it start?"
internal const val ENDS_DATE_QUESTION = "Which day does it end?"
internal const val ENDS_TIME_QUESTION = "What time does it end?"

/** What the Starts and Ends panes read besides the When: who is going, the held events for the dots and the clash line, and the clock. */
internal class WhenContext(
    val held: Held?,
    val household: EditorPeople,
    val who: List<PersonId>,
    val except: EventRef?,
    val zone: ZoneId,
    val today: LocalDate,
)

/**
 * The month of days for the start (9c §3.1), opening on the start's month, with a dot for each person already busy that
 * day. After "Use this" the [banner] says what was copied and the [quickPicks] offer the next dates that fit its repeat.
 */
@Composable
internal fun StartDatePane(eventWhen: EventWhen, context: WhenContext, banner: String? = null, quickPicks: List<LocalDate> = emptyList(), onPick: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        if (banner != null) CopiedBanner(banner)
        if (quickPicks.isNotEmpty()) QuickPicks(quickPicks, onPick)
        WhenDateGrid(eventWhen, context, "starts_date_pane", opensOn = eventWhen.startDate, allowed = { true }, onPick = onPick)
    }
}

@Composable
private fun CopiedBanner(text: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.bannerGap),
        modifier = Modifier
            .testTag("copied_banner")
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth()
            .clip(RoundedCornerShape(EditorDimens.bannerRadius))
            .background(c.accentSoft)
            .padding(EditorDimens.bannerPadding),
    ) {
        HhIcon(Icons.CONTENT_COPY, size = EditorDimens.bannerIcon, tint = c.accent)
        Text(text, style = CalendarType.suggestionLine, color = c.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickPicks(days: List<LocalDate>, onPick: (LocalDate) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.quickGap), modifier = Modifier.testTag("quick_picks")) {
        days.forEachIndexed { i, day ->
            HhButton(day.format(SHORT_DAY), { onPick(day) }, Modifier.testTag("quick_pick_$i"), on = ButtonSurface.Surf)
        }
    }
}

/** Hour and minute steppers for the start; the end follows. Under them, whether the people are free, and the nearest free starts. */
@Composable
internal fun StartTimePane(eventWhen: EventWhen, context: WhenContext, onStep: (ClockField, Boolean) -> Unit, onFreeStart: (LocalTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap), modifier = Modifier.testTag("starts_time_pane")) {
        TimeSteppers(eventWhen.startClock, "start_", onStep = onStep, modifier = Modifier.fillMaxWidth())
        WhenClash(eventWhen, context, onFreeStart)
    }
}

/**
 * The month of days for the end, opening on the end's month: only the start's day and the 59 after it can be picked,
 * and a pick keeps the end time.
 */
@Composable
internal fun EndDatePane(eventWhen: EventWhen, context: WhenContext, onPick: (LocalDate) -> Unit) {
    val first = eventWhen.startDate
    WhenDateGrid(
        eventWhen, context, "ends_date_pane",
        opensOn = eventWhen.endDate,
        allowed = { date -> first != null && date >= first && date < first.plusDays(MAX_SPAN_DAYS) },
        onPick = onPick,
    )
}

/** Steppers for the end, with any step that would reach the start or leave the end's day dimmed. */
@Composable
internal fun EndTimePane(eventWhen: EventWhen, context: WhenContext, onStep: (ClockField, Boolean) -> Unit, onFreeStart: (LocalTime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap), modifier = Modifier.testTag("ends_time_pane")) {
        TimeSteppers(eventWhen.endClock, "end_", onStep = onStep, enabled = eventWhen::canStepEnd, modifier = Modifier.fillMaxWidth())
        WhenClash(eventWhen, context, onFreeStart)
    }
}

@Composable
private fun WhenDateGrid(
    eventWhen: EventWhen,
    context: WhenContext,
    tag: String,
    opensOn: LocalDate?,
    allowed: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(opensOn ?: context.today)) }
    val dots = rememberGridDots(context.held, month, context.household, context.zone)
    val first = eventWhen.startDate
    val last = eventWhen.endDate
    val chosen = remember(first, last) { setOfNotNull(first, last) }
    val band = remember(first, last) { if (first != null && last != null && last > first) first..last else null }
    DateGrid(
        month = month,
        onMonth = { month = it },
        today = context.today,
        chosen = chosen,
        band = band,
        allowed = allowed,
        dots = dots,
        spoken = { spokenWho(dots[it].orEmpty()) },
        onPick = onPick,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun WhenClash(eventWhen: EventWhen, context: WhenContext, onFreeStart: (LocalTime) -> Unit) {
    val order = context.household.order
    val check = remember(context.held, eventWhen, context.who, context.except, context.zone, order) {
        context.held?.checkWhen(eventWhen, context.who, context.zone, context.except, order) ?: FreeCheck.None
    }
    ClashResult(
        check = check,
        who = context.who,
        overDays = eventWhen.overDays(context.zone),
        zone = context.zone,
        nameOf = { id -> context.household.byId[id]?.name.orEmpty() },
        onFreeStart = onFreeStart,
    )
}
