package uk.co.siland.culvery.capability.reminders

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private fun DayOfWeek.short() = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/** `Once`, `Every Friday`, `Every day`, `Mon to Fri` for three or more days in a row within Monday to Sunday, else `Mon, Wed, Fri`. */
internal fun repeatWords(schedule: Schedule): String = when (schedule) {
    is Schedule.Once -> "Once"
    is Schedule.Weekly -> {
        val days = schedule.days.sorted()
        val inARow = days.last().value - days.first().value + 1 == days.size
        when {
            days.size == 7 -> "Every day"
            days.size == 1 -> "Every ${days.single().getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            days.size >= 3 && inARow -> "${days.first().short()} to ${days.last().short()}"
            else -> days.joinToString { it.short() }
        }
    }
}
