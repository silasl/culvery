package uk.co.siland.culvery.capability.plugs.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsCapability
import uk.co.siland.culvery.capability.plugs.PlugsHouse
import uk.co.siland.culvery.capability.plugs.PlugsPermissionSource
import uk.co.siland.culvery.capability.plugs.ui.PlugsSection
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.LightsTabSection

@Module
@InstallIn(SingletonComponent::class)
abstract class PlugsModule {
    // With no system bound the Lights tab has no plugs (12 §4.3).
    @Multibinds
    abstract fun systems(): Set<PlugSystem>

    @Binds
    @IntoSet
    abstract fun permissions(impl: PlugsPermissionSource): PermissionSource

    @Binds
    abstract fun plugs(impl: PlugsHouse): HousePlugs

    @Binds
    @IntoSet
    abstract fun capability(impl: PlugsCapability): Capability

    @Binds
    @IntoSet
    abstract fun lightsSection(impl: PlugsSection): LightsTabSection
}
