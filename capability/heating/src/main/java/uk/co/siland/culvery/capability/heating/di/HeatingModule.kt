package uk.co.siland.culvery.capability.heating.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import uk.co.siland.culvery.capability.heating.HeatingSystem
import uk.co.siland.culvery.capability.heating.HouseHeatingSeam
import uk.co.siland.culvery.core.house.HouseHeating

@Module
@InstallIn(SingletonComponent::class)
abstract class HeatingModule {
    // With no system bound there is no thermostat to read (11 §4.5).
    @Multibinds
    abstract fun systems(): Set<HeatingSystem>

    @Binds
    abstract fun house(impl: HouseHeatingSeam): HouseHeating
}
