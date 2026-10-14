package uk.co.siland.culvery.provider.door_ring.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.door.DoorProvider
import uk.co.siland.culvery.provider.door_ring.RingProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class RingModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: RingProvider): DoorProvider
}
