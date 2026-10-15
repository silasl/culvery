package uk.co.siland.culvery.provider.music_sonos.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.provider.music_sonos.SonosProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class SonosModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: SonosProvider): MusicProvider
}
