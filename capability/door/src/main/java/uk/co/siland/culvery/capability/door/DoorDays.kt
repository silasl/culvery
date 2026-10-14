package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DoorDays(val today: List<DoorEvent>, val yesterday: List<DoorEvent>)

/** [events] (newest first) on [today] and the day before, by the household's dates (5 §5.2); older ones, still kept, aren't shown. */
fun doorDays(events: List<DoorEvent>, today: LocalDate, zone: ZoneId): DoorDays {
    fun dayOf(event: DoorEvent) = Instant.ofEpochMilli(event.atMillis).atZone(zone).toLocalDate()
    return DoorDays(events.filter { dayOf(it) == today }, events.filter { dayOf(it) == today.minusDays(1) })
}
