package uk.co.siland.culvery.provider.heating_homeassistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.heating.HeatingSystem
import uk.co.siland.culvery.core.homeassistant.HomeAssistantDevice
import uk.co.siland.culvery.provider.heating_homeassistant.HaHeatingSystem
import uk.co.siland.culvery.provider.heating_homeassistant.HaThermostatDevice

@Module
@InstallIn(SingletonComponent::class)
abstract class HaHeatingModule {
    @Binds
    @IntoSet
    abstract fun system(impl: HaHeatingSystem): HeatingSystem

    @Binds
    @IntoSet
    abstract fun device(impl: HaThermostatDevice): HomeAssistantDevice
}
