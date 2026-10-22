package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventWhen
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.MAX_SPAN_DAYS
import uk.co.siland.culvery.capability.calendar.spokenWho
import uk.co.siland.culvery.core.household.PersonId

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
 * The month of days for the start (9c §3.1), with a dot for each person already busy that day. A pick keeps the length, or
 * with [timesTouched] false starts over from that day's default times at [now]; it calls [onPicked].
 */
@Composable
internal fun StartDatePane(
    eventWhen: EventWhen,
    onChange: (EventWhen) -> Unit,
    context: WhenContext,
    now: LocalTime,
    timesTouched: Boolean,
    onPicked: () -> Unit,
) {
    WhenDateGrid(
        eventWhen, context, "starts_date_pane",
        allowed = { true },
        onPick = { date ->
            onChange(eventWhen.pickStartDate(date, defaults = !timesTouched, context.today, now))
            onPicked()
        },
    )
}

/** Hour and minute steppers for the start; the end follows. Under them, whether the people are free, and the nearest free starts. */
@Composable
internal fun StartTimePane(eventWhen: EventWhen, onChange: (EventWhen) -> Unit, context: WhenContext) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap), modifier = Modifier.testTag("starts_time_pane")) {
        TimeSteppers(
            eventWhen.startClock, "start_",
            onStep = { field, up -> onChange(eventWhen.stepStart(field, up)) },
            modifier = Modifier.fillMaxWidth(),
        )
        WhenClash(eventWhen, onChange, context)
    }
}

/** The month of days for the end: only the start's day and the 59 after it can be picked, and a pick keeps the end time. */
@Composable
internal fun EndDatePane(eventWhen: EventWhen, onChange: (EventWhen) -> Unit, context: WhenContext, onPicked: () -> Unit) {
    val first = eventWhen.startDate
    WhenDateGrid(
        eventWhen, context, "ends_date_pane",
        allowed = { date -> first != null && date >= first && date < first.plusDays(MAX_SPAN_DAYS) },
        onPick = { date ->
            eventWhen.pickEndDate(date)?.let {
                onChange(it)
                onPicked()
            }
        },
    )
}

/** Steppers for the end, with any step that would reach the start or leave the end's day dimmed. */
@Composable
internal fun EndTimePane(eventWhen: EventWhen, onChange: (EventWhen) -> Unit, context: WhenContext) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap), modifier = Modifier.testTag("ends_time_pane")) {
        TimeSteppers(
            eventWhen.endClock, "end_",
            onStep = { field, up -> eventWhen.stepEnd(field, up)?.let(onChange) },
            enabled = eventWhen::canStepEnd,
            modifier = Modifier.fillMaxWidth(),
        )
        WhenClash(eventWhen, onChange, context)
    }
}

@Composable
private fun WhenDateGrid(
    eventWhen: EventWhen,
    context: WhenContext,
    tag: String,
    allowed: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(eventWhen.endDate ?: eventWhen.startDate ?: context.today)) }
    val dots = rememberGridDots(context.held, month, context.household, context.zone)
    val first = eventWhen.startDate
    val last = eventWhen.endDate
    DateGrid(
        month = month,
        onMonth = { month = it },
        today = context.today,
        chosen = setOfNotNull(first, last),
        band = if (first != null && last != null && last > first) first..last else null,
        allowed = allowed,
        dots = dots,
        spoken = { spokenWho(dots[it].orEmpty()) },
        onPick = onPick,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun WhenClash(eventWhen: EventWhen, onChange: (EventWhen) -> Unit, context: WhenContext) {
    val order = context.household.order
    val check = remember(context.held, eventWhen, context.who, context.except, context.zone, order) {
        context.held?.checkWhen(eventWhen, context.who, context.zone, context.except, order) ?: FreeCheck.None
    }
    ClashResult(
        summary = null,
        check = check,
        who = context.who,
        overDays = eventWhen.overDays(context.zone),
        zone = context.zone,
        nameOf = { id -> context.household.byId[id]?.name.orEmpty() },
        onFreeStart = { onChange(eventWhen.withStart(it)) },
    )
}
