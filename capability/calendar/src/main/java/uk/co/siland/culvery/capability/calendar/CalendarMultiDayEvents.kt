package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.house.MultiDayEvent
import uk.co.siland.culvery.core.house.MultiDayEvents
import uk.co.siland.culvery.core.household.HouseholdZone

/** The all-day events of two days or more on the calendars the wall shows, for Holiday mode's back date (11 §5.2). */
class CalendarMultiDayEvents @Inject constructor(
    private val store: CalendarStore,
    private val zone: HouseholdZone,
) : MultiDayEvents {
    override suspend fun around(today: LocalDate): List<MultiDayEvent> {
        val z = zone.current()
        val tomorrow = today.plusDays(1)
        val from = millis(today, z) - ZONE_MARGIN_MS
        val to = millis(tomorrow.plusDays(1), z) + ZONE_MARGIN_MS
        return store.eventsBetween(from, to).first().mapNotNull { event ->
            val start = event.start as? EventTime.AllDay ?: return@mapNotNull null
            val (first, last) = daySpan(start, event.end, z)
            MultiDayEvent(event.title, first, last).takeIf { first != last && first <= tomorrow && last >= today }
        }
    }
}
