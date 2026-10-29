package uk.co.siland.culvery.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.sample.SampleLightsProvider
import uk.co.siland.culvery.sample.SampleMusicProvider

/**
 * Debug builds only: speakers and lights in memory, so Party mode can be walked through with no Sonos or Hue (13 §8). Find
 * and a typed address skip them, as neither can be connected by a household.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SampleHouseModule {
    @Binds
    @IntoSet
    abstract fun music(impl: SampleMusicProvider): MusicProvider

    @Binds
    @IntoSet
    abstract fun lights(impl: SampleLightsProvider): LightsProvider
}
