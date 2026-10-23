package uk.co.siland.culvery.provider.calendar_google.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.core.google.GoogleAccounts
import uk.co.siland.culvery.core.google.TokenSource
import uk.co.siland.culvery.core.google.googleHttpClient
import uk.co.siland.culvery.core.plugin.AppVersion
import uk.co.siland.culvery.provider.calendar_google.GOOGLE_CALENDAR_BASE_URL
import uk.co.siland.culvery.provider.calendar_google.GoogleApi
import uk.co.siland.culvery.provider.calendar_google.GoogleCalendarAccounts
import uk.co.siland.culvery.provider.calendar_google.GoogleCalendarProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleCalendarModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: GoogleCalendarProvider): CalendarProvider

    @Binds
    @IntoSet
    abstract fun writer(impl: GoogleCalendarProvider): CalendarWriter

    @Binds
    abstract fun accounts(impl: GoogleCalendarAccounts): GoogleAccounts

    companion object {
        // The client stays inside GoogleApi, not in the graph, so another module's OkHttpClient can't collide with it.
        @Provides
        @Singleton
        fun api(tokens: TokenSource, @AppVersion version: String): GoogleApi =
            GoogleApi(GOOGLE_CALENDAR_BASE_URL.toHttpUrl(), tokens, lazy { googleHttpClient(version) })
    }
}
