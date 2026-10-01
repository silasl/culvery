package uk.co.siland.culvery.capability.weather.di

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
import uk.co.siland.culvery.capability.weather.WeatherCapability
import uk.co.siland.culvery.capability.weather.WeatherProvider
import uk.co.siland.culvery.capability.weather.WeatherRepository
import uk.co.siland.culvery.capability.weather.WeatherSyncLoop
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherModule {
    // With no provider bound the set is empty and the card waits (§5).
    @Multibinds
    abstract fun providers(): Set<WeatherProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: WeatherCapability): Capability

    @Binds
    @IntoSet
    abstract fun syncLoop(impl: WeatherSyncLoop): Startable

    @Binds
    abstract fun daylight(impl: WeatherRepository): Daylight

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): WeatherDatabase =
            Room.databaseBuilder(context, WeatherDatabase::class.java, "weather.db").build()
    }
}
