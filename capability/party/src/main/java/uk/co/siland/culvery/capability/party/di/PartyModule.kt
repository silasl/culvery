package uk.co.siland.culvery.capability.party.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.party.ModesCapability
import uk.co.siland.culvery.capability.party.ModesShelf
import uk.co.siland.culvery.capability.party.PartyPermissionSource
import uk.co.siland.culvery.capability.party.StoredModesHouse
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.ModeShelf
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.Capability

@Module
@InstallIn(SingletonComponent::class)
abstract class PartyModule {
    @Binds
    @IntoSet
    abstract fun permissions(impl: PartyPermissionSource): PermissionSource

    @Binds
    @IntoSet
    abstract fun capability(impl: ModesCapability): Capability

    @Binds
    @IntoSet
    abstract fun switch(impl: StoredModesHouse): HouseModeSwitch

    @Binds
    abstract fun shelf(impl: ModesShelf): ModeShelf

    // Holiday binds its page's own parts here (15b decision 5); with none, its page has no Turn on now.
    @Multibinds
    abstract fun builtInPages(): Set<BuiltInModePage>
}
