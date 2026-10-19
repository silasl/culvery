package uk.co.siland.culvery.display.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.display.DisplayState

@Module
@InstallIn(SingletonComponent::class)
abstract class DisplayModule {
    @Binds
    abstract fun nightHours(impl: DisplayState): NightHours
}
