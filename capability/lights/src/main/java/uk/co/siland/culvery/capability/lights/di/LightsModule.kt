package uk.co.siland.culvery.capability.lights.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.lights.LightsCapability
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouseSeam
import uk.co.siland.culvery.capability.lights.LightsPermissionSource
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class LightsModule {
    // With no provider bound there is no Lights tab (7 §3.1).
    @Multibinds
    abstract fun providers(): Set<LightsProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: LightsCapability): Capability

    @Binds
    @IntoSet
    abstract fun permissions(impl: LightsPermissionSource): PermissionSource

    @Binds
    @IntoSet
    abstract fun follow(impl: LightsFollow): Startable

    @Binds
    abstract fun house(impl: LightsHouseSeam): HouseLights
}
