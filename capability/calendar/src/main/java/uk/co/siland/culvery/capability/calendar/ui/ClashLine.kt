package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.CLASHES_ALLOWED
import uk.co.siland.culvery.capability.calendar.CLASHES_ALLOWED_TAIL
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventSpan
import uk.co.siland.culvery.capability.calendar.EventWhen
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.check
import uk.co.siland.culvery.capability.calendar.clashLine
import uk.co.siland.culvery.capability.calendar.freeAtLine
import uk.co.siland.culvery.capability.calendar.freeLine
import uk.co.siland.culvery.capability.calendar.spansDays
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.clockText

private val HOUR_MINUTE_TAG: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm", Locale.ENGLISH)

/** The result line's icon: `check_circle` when free, `warning` on a clash, none when the tablet can't say. */
internal fun checkIcon(check: FreeCheck): String? = when (check) {
    is FreeCheck.Free -> Icons.CHECK_CIRCLE
    is FreeCheck.Busy -> Icons.WARNING
    FreeCheck.None -> null
}

/** Whether [who] are free over a timed [eventWhen] (9c §3.5); [FreeCheck.None] for All day or no start date. */
internal fun Held.checkWhen(eventWhen: EventWhen, who: List<PersonId>, zone: ZoneId, except: EventRef?, order: List<PersonId>): FreeCheck {
    val (from, to) = (eventWhen.span(zone) as? EventSpan.Timed)?.localRange() ?: return FreeCheck.None
    return check(who, from, to, zone, except, order)
}

/** Whether a timed [eventWhen] reaches past its first day, so a clash names its date. */
internal fun EventWhen.overDays(zone: ZoneId): Boolean {
    val (from, to) = (span(zone) as? EventSpan.Timed)?.localRange() ?: return false
    return spansDays(from, to)
}

/**
 * Under a rule: what the tablet knows of the time, said as it changes: "Mia is free then" in `accent`, or the clash in
 * `weather`, then the nearest free starts for the same length; nothing past what it holds (9 §3.1). With nothing to say,
 * nothing is drawn.
 */
@Composable
internal fun ClashResult(
    check: FreeCheck,
    who: List<PersonId>,
    overDays: Boolean,
    zone: ZoneId,
    nameOf: (PersonId) -> String,
    onFreeStart: (LocalTime) -> Unit,
) {
    val line = when (check) {
        is FreeCheck.Free -> freeLine(check, nameOf)
        is FreeCheck.Busy -> clashLine(check, single = who.size == 1, overDays = overDays, nameOf, zone)
        FreeCheck.None -> null
    }
    if (line == null) return
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.resultTop)) {
        Box(Modifier.fillMaxWidth().height(EditorDimens.resultRule).background(c.surf3))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorDimens.resultGap),
            modifier = Modifier.testTag("time_result").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            val icon = checkIcon(check)
            if (line != null && icon != null) {
                val tint = if (check is FreeCheck.Busy) c.weather else c.accent
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorDimens.checkIconGap)) {
                    HhIcon(icon, size = EditorDimens.checkIcon, filled = true, tint = tint, modifier = Modifier.testTag("time_result_icon"))
                    Text(line, style = CalendarType.timeCheck, color = tint)
                }
            }
        }
        if (check is FreeCheck.Busy) FreeStarts(who, check.freeStarts, nameOf, onFreeStart)
    }
}

/** "Mia is free at" 14:45 16:15 "or keep it; clashes are allowed.", or with no free start, "Clashes are allowed." */
@Composable
private fun FreeStarts(who: List<PersonId>, starts: List<LocalTime>, nameOf: (PersonId) -> String, onPick: (LocalTime) -> Unit) {
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
        Text(freeAtLine(who.map(nameOf)), style = CalendarType.timeFreeAt, color = mute, modifier = Modifier.testTag("time_free_at"))
        starts.forEach { start ->
            HhChoiceChip(
                label = clockText(start),
                selected = false,
                tag = "time_free_${start.format(HOUR_MINUTE_TAG)}",
                onClick = { onPick(start) },
                unselectedFill = Culvery.colors.surf2,
                role = Role.Button,
                labelStyle = CalendarType.timeChip,
            )
        }
        Text(CLASHES_ALLOWED_TAIL, style = CalendarType.timeFreeAt, color = mute, modifier = Modifier.testTag("time_free_tail"))
    }
}
