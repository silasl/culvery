package uk.co.siland.culvery.capability.weather

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase

internal fun weatherDb(): WeatherDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WeatherDatabase::class.java)
        .allowMainThreadQueries()
        .build()
