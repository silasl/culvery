package uk.co.siland.culvery.capability.plugs.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsPermissionSource
import uk.co.siland.culvery.core.access.PermissionSource

@Module
@InstallIn(SingletonComponent::class)
abstract class PlugsModule {
    // With no system bound there is no Plugs tab (12 §4.3).
    @Multibinds
    abstract fun systems(): Set<PlugSystem>

    @Binds
    @IntoSet
    abstract fun permissions(impl: PlugsPermissionSource): PermissionSource
}
