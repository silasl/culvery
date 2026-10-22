package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.spokenWho
import uk.co.siland.culvery.core.ui.ControlType
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.Icons

internal const val LASTS_MORE_THAN_A_DAY = "Lasts more than a day"
internal const val TAP_FIRST_THEN_LAST = "Tap the first day, then the last"

private const val PICK_TODAY = "Today"
private const val PICK_TOMORROW = "Tomorrow"
private val FULL_WEEKDAY_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

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

/**
 * Handoff 6c and 6d: quick picks, then a month of days with up to three dots for the people already on each from [held],
 * and "Lasts more than a day", which turns taps into a first and a last day (9 §3.1). Any date can be picked. A day, or a
 * range's last day within the 60-day cap, calls [onPicked].
 */
@Composable
internal fun DayPane(form: EditorForm, held: Held?, household: EditorPeople, onPicked: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(form.day ?: form.today)) }
    val onDay = rememberGridDots(held, month, household, form.zone)
    val pick = { date: LocalDate ->
        form.tapDay(date)
        if (!form.awaitingLastDay && form.untilError == null) onPicked()
    }
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        QuickPicks(form) { date ->
            month = YearMonth.from(date)
            pick(date)
        }
        DateGrid(
            month = month,
            onMonth = { month = it },
            today = form.today,
            chosen = setOfNotNull(form.day, form.until),
            band = form.day?.let { first -> form.until?.let { last -> first..last } },
            allowed = { true },
            dots = onDay,
            spoken = { spokenWho(onDay[it].orEmpty()) },
            onPick = pick,
        )
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
