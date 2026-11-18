package uk.co.siland.culvery.capability.reminders

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd
import uk.co.siland.culvery.core.repeat.recurrenceRule

/** Weekly on [days] as the picker writes it, first on the earliest of them in the week of [from] (Monday 2 November 2026). */
internal fun weeklyOn(vararg days: DayOfWeek, from: LocalDate = LocalDate.of(2026, 11, 2)): Schedule.Repeating {
    val first = from.with(TemporalAdjusters.nextOrSame(days.min()))
    return Schedule.Repeating(first, checkNotNull(recurrenceRule(RepeatChoice.Weekly, RepeatEnd.Never, first, days = days.toSet())))
}
