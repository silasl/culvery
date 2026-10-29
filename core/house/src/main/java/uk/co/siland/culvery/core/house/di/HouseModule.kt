package uk.co.siland.culvery.core.house.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.house.HouseModeScheduler
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.MultiDayEvents
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class HouseModule {
    @BindsOptionalOf
    abstract fun lights(): HouseLights

    @BindsOptionalOf
    abstract fun heating(): HouseHeating

    @BindsOptionalOf
    abstract fun hoover(): HouseHoover

    @BindsOptionalOf
    abstract fun plugs(): HousePlugs

    // With no mode bound the Plugs tab has no mode column (12 §5.1).
    @Multibinds
    abstract fun modes(): Set<HouseModeInfo>

    @BindsOptionalOf
    abstract fun multiDayEvents(): MultiDayEvents

    @BindsOptionalOf
    abstract fun music(): HouseMusic

    // With no mode bound nothing blocks a mode (13 §2.6).
    @Multibinds
    abstract fun modeSwitches(): Set<HouseModeSwitch>

    @Binds
    @IntoSet
    abstract fun scheduler(impl: HouseModeScheduler): Startable
}
