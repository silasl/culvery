package uk.co.siland.culvery.capability.holiday

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import uk.co.siland.culvery.core.house.MultiDayEvent
import uk.co.siland.culvery.core.house.MultiDayEvents
import uk.co.siland.culvery.core.plugin.WallClock

/** A real [HolidayHouse] over an in-memory store and fake seams, at 09:00 on Wednesday 28 October 2026. */
internal class HouseRig(scope: CoroutineScope) {
    val lights = FakeLights()
    val heating = FakeHeating()
    val hoover = FakeHoover()
    var now: LocalDateTime = TODAY.atTime(9, 0)
    val house = HolidayHouse(
        HolidaySettings(MemoryStore(), scope), lights, heating, hoover,
        WallClock { now.atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli() }, { now },
    )

    /** Reads the stored record, as the scheduler's first tick does. */
    suspend fun loaded(): HolidayHouse = house.also { it.tick() }

    companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 28)
        val CORNWALL = MultiDayEvent("Cornwall", TODAY.minusDays(3), TODAY.plusDays(4))
    }
}

internal class FakeEvents(var events: List<MultiDayEvent> = emptyList()) : MultiDayEvents {
    override suspend fun around(today: LocalDate): List<MultiDayEvent> = events
}
