package uk.co.siland.culvery.provider.calendar_fake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class FakeCalendarModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: FakeCalendarProvider): CalendarProvider

    @Binds
    @IntoSet
    abstract fun writer(impl: FakeCalendarProvider): CalendarWriter
}
