package uk.co.siland.culvery.provider.calendar_google.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.core.plugin.AppVersion
import uk.co.siland.culvery.provider.calendar_google.Authorizer
import uk.co.siland.culvery.provider.calendar_google.GOOGLE_CALENDAR_BASE_URL
import uk.co.siland.culvery.provider.calendar_google.GoogleApi
import uk.co.siland.culvery.provider.calendar_google.GoogleCalendarProvider
import uk.co.siland.culvery.provider.calendar_google.GooglePlayServicesCheck
import uk.co.siland.culvery.provider.calendar_google.PlayServicesAuthorizer
import uk.co.siland.culvery.provider.calendar_google.PlayServicesCheck
import uk.co.siland.culvery.provider.calendar_google.PlayServicesTokenSource
import uk.co.siland.culvery.provider.calendar_google.TokenSource

// OkHttp's own timeouts sit inside the engine's (10 s editor, 60 s drain and sync), so the engine's decide (3a design §3.1).
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

// The engine gives a call 60 s too; this ends a body that drips in (4b follow-up).
private val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

/** 4c design §6.1: Google compresses only for a User-Agent that contains "gzip"; OkHttp sends Accept-Encoding itself. */
internal fun userAgent(version: String): String = "Culvery/$version (gzip)"

internal fun googleClient(version: String): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT)
    .readTimeout(READ_TIMEOUT)
    .callTimeout(CALL_TIMEOUT)
    .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent(version)).build()) }
    .build()

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
    abstract fun tokens(impl: PlayServicesTokenSource): TokenSource

    @Binds
    abstract fun authorizer(impl: PlayServicesAuthorizer): Authorizer

    @Binds
    abstract fun playServices(impl: GooglePlayServicesCheck): PlayServicesCheck

    companion object {
        // The client stays inside GoogleApi, not in the graph, so another module's OkHttpClient can't collide with it.
        @Provides
        @Singleton
        fun api(tokens: TokenSource, @AppVersion version: String): GoogleApi =
            GoogleApi(GOOGLE_CALENDAR_BASE_URL.toHttpUrl(), tokens, lazy { googleClient(version) })
    }
}
