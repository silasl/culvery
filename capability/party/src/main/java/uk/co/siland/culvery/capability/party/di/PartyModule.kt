package uk.co.siland.culvery.capability.party.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.party.PartyPermissionSource
import uk.co.siland.culvery.core.access.PermissionSource

@Module
@InstallIn(SingletonComponent::class)
abstract class PartyModule {
    @Binds
    @IntoSet
    abstract fun permissions(impl: PartyPermissionSource): PermissionSource
}
