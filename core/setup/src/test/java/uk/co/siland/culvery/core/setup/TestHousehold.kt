package uk.co.siland.culvery.core.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

internal fun householdDb(): HouseholdDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()
