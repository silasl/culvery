package uk.co.siland.culvery.capability.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

internal fun calendarDb(): CalendarDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CalendarDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun householdDb(): HouseholdDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal val TEST_TODAY: LocalDate = LocalDate.of(2026, 9, 23)
internal val TEST_ZONE: ZoneId = ZoneId.of("Europe/London")

/** A sync applied at noon on [today]. */
internal suspend fun CalendarStore.applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult, today: LocalDate): List<EventChange> =
    applySync(connectionId, sourceId, range, result, today, nowMillis = today.atTime(12, 0).atZone(range.zone).toInstant().toEpochMilli())
