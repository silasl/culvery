package uk.co.siland.culvery.capability.lights.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.lights.LightsCapability
import uk.co.siland.culvery.capability.lights.LightsPermissionSource
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.plugin.Capability

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
}
