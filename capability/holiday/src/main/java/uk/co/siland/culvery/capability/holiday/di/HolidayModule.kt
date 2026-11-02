package uk.co.siland.culvery.capability.holiday.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.holiday.HolidayCapability
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayPermissionSource
import uk.co.siland.culvery.capability.holiday.ui.HolidayModePage
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HolidayLightsSection

@Module
@InstallIn(SingletonComponent::class)
abstract class HolidayModule {
    @Binds
    abstract fun mode(impl: HolidayHouse): HouseMode

    @Binds
    @IntoSet
    abstract fun switch(impl: HolidayHouse): HouseModeSwitch

    @Binds
    @IntoSet
    abstract fun permissions(impl: HolidayPermissionSource): PermissionSource

    @Binds
    @IntoSet
    abstract fun capability(impl: HolidayCapability): Capability

    @Binds
    @IntoSet
    abstract fun modePage(impl: HolidayModePage): BuiltInModePage

    // Lights binds its automations here (15b decision 5); with none, Holiday's page has only its fixed lines.
    @Multibinds
    abstract fun lightsSections(): Set<HolidayLightsSection>
}
