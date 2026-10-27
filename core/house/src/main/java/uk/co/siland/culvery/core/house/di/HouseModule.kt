package uk.co.siland.culvery.core.house.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.MultiDayEvents

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
    abstract fun multiDayEvents(): MultiDayEvents
}
