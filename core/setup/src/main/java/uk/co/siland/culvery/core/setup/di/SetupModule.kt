package uk.co.siland.culvery.core.setup.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.SampleHousehold
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.setup.pages.LocationPage
import uk.co.siland.culvery.core.setup.pages.PeoplePage
import uk.co.siland.culvery.core.setup.steps.DoneStep
import uk.co.siland.culvery.core.setup.steps.HouseholdStep
import uk.co.siland.culvery.core.setup.steps.LocationStep
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.setup.steps.YouStep

@Module
@InstallIn(SingletonComponent::class)
abstract class SetupModule {
    @Binds
    @IntoSet
    abstract fun welcome(step: WelcomeStep): SetupStep

    @Binds
    @IntoSet
    abstract fun location(step: LocationStep): SetupStep

    @Binds
    @IntoSet
    abstract fun you(step: YouStep): SetupStep

    @Binds
    @IntoSet
    abstract fun household(step: HouseholdStep): SetupStep

    @Binds
    @IntoSet
    abstract fun done(step: DoneStep): SetupStep

    @Binds
    @IntoSet
    abstract fun locationPage(page: LocationPage): SettingsPage

    @Binds
    @IntoSet
    abstract fun peoplePage(page: PeoplePage): SettingsPage

    @Binds
    @IntoSet
    abstract fun kioskPage(page: KioskPage): SettingsPage

    /** Present only in debug builds (4a design D11). */
    @BindsOptionalOf
    abstract fun sampleHousehold(): SampleHousehold
}
