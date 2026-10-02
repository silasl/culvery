package uk.co.siland.culvery.capability.calendar.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarCapability
import uk.co.siland.culvery.capability.calendar.CalendarPermissionSource
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSyncLoop
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.HouseholdFollower
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.ALL_MIGRATIONS
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Multibinds
    abstract fun providers(): Set<CalendarProvider>

    @Multibinds
    abstract fun writers(): Set<CalendarWriter>

    @Binds
    @IntoSet
    abstract fun capability(impl: CalendarCapability): Capability

    @Binds
    @IntoSet
    abstract fun syncLoop(impl: CalendarSyncLoop): Startable

    @Binds
    @IntoSet
    abstract fun householdFollower(impl: HouseholdFollower): Startable

    @Binds
    @IntoSet
    abstract fun permissions(impl: CalendarPermissionSource): PermissionSource

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): CalendarDatabase =
            Room.databaseBuilder(context, CalendarDatabase::class.java, "calendar.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
