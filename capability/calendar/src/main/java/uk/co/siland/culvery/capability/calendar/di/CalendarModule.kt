package uk.co.siland.culvery.capability.calendar.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Multibinds
    abstract fun providers(): Set<CalendarProvider>

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): CalendarDatabase =
            Room.databaseBuilder(context, CalendarDatabase::class.java, "calendar.db").build()
    }
}
