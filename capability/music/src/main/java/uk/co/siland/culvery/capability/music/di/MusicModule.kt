package uk.co.siland.culvery.capability.music.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.music.MusicCapability
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicHouseSeam
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.capability.music.MusicQuieter
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.plugin.AlertQuieter
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class MusicModule {
    // With no provider bound there is no Music tab (6 §3.1).
    @Multibinds
    abstract fun providers(): Set<MusicProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: MusicCapability): Capability

    @Binds
    @IntoSet
    abstract fun checks(impl: MusicChecks): Startable

    @Binds
    @IntoSet
    abstract fun quieter(impl: MusicQuieter): AlertQuieter

    @Binds
    abstract fun house(impl: MusicHouseSeam): HouseMusic
}
