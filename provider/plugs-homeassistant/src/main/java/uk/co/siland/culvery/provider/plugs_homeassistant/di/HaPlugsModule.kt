package uk.co.siland.culvery.provider.plugs_homeassistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.core.homeassistant.HomeAssistantSection
import uk.co.siland.culvery.provider.plugs_homeassistant.HaPlugSystem
import uk.co.siland.culvery.provider.plugs_homeassistant.PlugsSettingsSection

@Module
@InstallIn(SingletonComponent::class)
abstract class HaPlugsModule {
    @Binds
    @IntoSet
    abstract fun system(impl: HaPlugSystem): PlugSystem

    @Binds
    @IntoSet
    abstract fun settings(impl: PlugsSettingsSection): HomeAssistantSection
}
