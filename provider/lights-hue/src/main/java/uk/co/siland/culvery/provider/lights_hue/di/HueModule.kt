package uk.co.siland.culvery.provider.lights_hue.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.provider.lights_hue.HueProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class HueModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: HueProvider): LightsProvider
}
