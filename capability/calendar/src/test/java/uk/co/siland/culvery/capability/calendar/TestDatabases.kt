package uk.co.siland.culvery.capability.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase

internal fun calendarDb(): CalendarDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CalendarDatabase::class.java)
        .allowMainThreadQueries()
        .build()
