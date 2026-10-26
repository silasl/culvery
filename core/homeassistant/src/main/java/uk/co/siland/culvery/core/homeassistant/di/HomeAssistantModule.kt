package uk.co.siland.culvery.core.homeassistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.HomeAssistantClient
import uk.co.siland.culvery.core.homeassistant.HomeAssistantDevice
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeAssistantModule {
    @Binds
    abstract fun api(impl: HomeAssistantClient): HomeAssistantApi

    // With no provider bound, Settings › Other integrations shows no pickers.
    @Multibinds
    abstract fun devices(): Set<HomeAssistantDevice>

    @Binds
    @IntoSet
    abstract fun follow(impl: HomeAssistant): Startable
}
