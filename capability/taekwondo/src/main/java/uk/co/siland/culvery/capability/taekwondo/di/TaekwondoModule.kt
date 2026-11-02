package uk.co.siland.culvery.capability.taekwondo.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.taekwondo.Speaker
import uk.co.siland.culvery.capability.taekwondo.TaekwondoCapability
import uk.co.siland.culvery.capability.taekwondo.TtsSpeaker
import uk.co.siland.culvery.core.plugin.Capability

@Module
@InstallIn(SingletonComponent::class)
abstract class TaekwondoModule {
    @Binds
    @IntoSet
    abstract fun capability(impl: TaekwondoCapability): Capability

    @Binds
    abstract fun speaker(impl: TtsSpeaker): Speaker
}
