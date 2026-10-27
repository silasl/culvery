package uk.co.siland.culvery.capability.holiday.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.holiday.HolidayCapability
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayPermissionSource
import uk.co.siland.culvery.capability.holiday.HolidayScheduler
import uk.co.siland.culvery.capability.holiday.HolidayWords
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class HolidayModule {
    @Binds
    abstract fun mode(impl: HolidayHouse): HouseMode

    @Binds
    @IntoSet
    abstract fun scheduler(impl: HolidayScheduler): Startable

    @Binds
    @IntoSet
    abstract fun permissions(impl: HolidayPermissionSource): PermissionSource

    @Binds
    @IntoSet
    abstract fun capability(impl: HolidayCapability): Capability

    companion object {
        @Provides
        @IntoSet
        fun modeInfo(): HouseModeInfo = HouseModeInfo(HouseModes.HOLIDAY, HolidayWords.LABEL, 10, HousePermissions.HOLIDAY_MODE)
    }
}
