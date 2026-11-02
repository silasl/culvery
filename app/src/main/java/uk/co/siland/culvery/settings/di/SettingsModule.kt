package uk.co.siland.culvery.settings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.settings.AppSettingsPage

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    @IntoSet
    abstract fun appSettings(page: AppSettingsPage): SettingsPage
}
