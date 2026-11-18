package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.core.repeat.repeatWords

/** The detail sheet's Repeats row for an event starting at [start]; see [repeatWords]. */
fun repeatsLabel(rule: String?, start: EventTime, zone: ZoneId, today: LocalDate): String =
    repeatWords(rule, start.instantIn(zone).atZone(zone).toLocalDate(), zone, today)
