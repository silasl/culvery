package uk.co.siland.culvery.capability.party.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.party.PartyCapability
import uk.co.siland.culvery.capability.party.PartyHouse
import uk.co.siland.culvery.capability.party.PartyPermissionSource
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.plugin.Capability

@Module
@InstallIn(SingletonComponent::class)
abstract class PartyModule {
    @Binds
    @IntoSet
    abstract fun permissions(impl: PartyPermissionSource): PermissionSource

    @Binds
    @IntoSet
    abstract fun capability(impl: PartyCapability): Capability

    @Binds
    @IntoSet
    abstract fun switch(impl: PartyHouse): HouseModeSwitch

    companion object {
        @Provides
        @IntoSet
        fun modeInfo(): HouseModeInfo = HouseModeInfo(HouseModes.PARTY, PartyWords.LABEL, 20, HousePermissions.PARTY_MODE)
    }
}
