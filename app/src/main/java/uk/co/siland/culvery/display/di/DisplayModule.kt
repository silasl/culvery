package uk.co.siland.culvery.display.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.display.AndroidTabletHeat
import uk.co.siland.culvery.display.DisplayState
import uk.co.siland.culvery.display.HeatLog
import uk.co.siland.culvery.display.TabletHeat

@Module
@InstallIn(SingletonComponent::class)
abstract class DisplayModule {
    @Binds
    abstract fun nightHours(impl: DisplayState): NightHours

    @Binds
    abstract fun tabletHeat(impl: AndroidTabletHeat): TabletHeat

    @Binds
    @IntoSet
    abstract fun heatLog(impl: HeatLog): Startable
}
