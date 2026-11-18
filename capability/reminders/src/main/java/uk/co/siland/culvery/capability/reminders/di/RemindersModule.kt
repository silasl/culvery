package uk.co.siland.culvery.capability.reminders.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import uk.co.siland.culvery.capability.reminders.RemindersCapability
import uk.co.siland.culvery.capability.reminders.RemindersEngine
import uk.co.siland.culvery.capability.reminders.RemindersForEvents
import uk.co.siland.culvery.capability.reminders.RemindersPermissionSource
import uk.co.siland.culvery.capability.reminders.db.RemindersDatabase
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.EventReminders
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {
    @Binds
    @IntoSet
    abstract fun engine(impl: RemindersEngine): Startable

    @Binds
    @IntoSet
    abstract fun capability(impl: RemindersCapability): Capability

    @Binds
    @IntoSet
    abstract fun permissions(impl: RemindersPermissionSource): PermissionSource

    @Binds
    abstract fun eventReminders(impl: RemindersForEvents): EventReminders

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): RemindersDatabase =
            Room.databaseBuilder(context, RemindersDatabase::class.java, "reminders.db").build()
    }
}
