package uk.co.siland.culvery.capability.hoover.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.hoover.HooverCapability
import uk.co.siland.culvery.capability.hoover.HooverPermissionSource
import uk.co.siland.culvery.capability.hoover.HooverSystem
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.plugin.Capability

@Module
@InstallIn(SingletonComponent::class)
abstract class HooverModule {
    // With no system bound there is no tray row (11 §4.4).
    @Multibinds
    abstract fun systems(): Set<HooverSystem>

    @Binds
    @IntoSet
    abstract fun capability(impl: HooverCapability): Capability

    @Binds
    @IntoSet
    abstract fun permissions(impl: HooverPermissionSource): PermissionSource
}
