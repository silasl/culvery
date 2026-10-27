package uk.co.siland.culvery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.core.plugin.WallClock

/** The wall's clock is the system's; debug builds swap in one with an offset (11 §8.1). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
}
