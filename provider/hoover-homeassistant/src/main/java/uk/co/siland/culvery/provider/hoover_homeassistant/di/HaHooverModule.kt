package uk.co.siland.culvery.provider.hoover_homeassistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.hoover.HooverSystem
import uk.co.siland.culvery.core.homeassistant.HomeAssistantDevice
import uk.co.siland.culvery.provider.hoover_homeassistant.HaHooverDevice
import uk.co.siland.culvery.provider.hoover_homeassistant.HaHooverSystem

@Module
@InstallIn(SingletonComponent::class)
abstract class HaHooverModule {
    @Binds
    @IntoSet
    abstract fun system(impl: HaHooverSystem): HooverSystem

    @Binds
    @IntoSet
    abstract fun device(impl: HaHooverDevice): HomeAssistantDevice
}
