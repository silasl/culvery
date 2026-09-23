package uk.co.siland.culvery.capability.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
