package uk.co.siland.culvery.provider.calendar_midsussex.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.core.plugin.AppVersion
import uk.co.siland.culvery.provider.calendar_midsussex.COUNCIL_BASE_URL
import uk.co.siland.culvery.provider.calendar_midsussex.CouncilClient
import uk.co.siland.culvery.provider.calendar_midsussex.MidSussexBinsProvider
import uk.co.siland.culvery.provider.calendar_midsussex.councilHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CouncilBaseUrl

/** Interceptors added to the council's client; empty unless the debug build serves a sample council. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CouncilInterceptors

@Module
@InstallIn(SingletonComponent::class)
abstract class MidSussexModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: MidSussexBinsProvider): CalendarProvider

    @Multibinds
    @CouncilInterceptors
    abstract fun interceptors(): Set<Interceptor>

    companion object {
        @Provides
        @CouncilBaseUrl
        fun baseUrl(): HttpUrl = COUNCIL_BASE_URL.toHttpUrl()

        // The client stays inside CouncilClient, not in the graph, so another module's OkHttpClient can't collide with it.
        @Provides
        @Singleton
        fun council(
            @CouncilBaseUrl baseUrl: HttpUrl,
            @AppVersion version: String,
            @CouncilInterceptors interceptors: Set<@JvmSuppressWildcards Interceptor>,
        ): CouncilClient = CouncilClient(baseUrl, lazy { councilHttpClient(version, interceptors) })
    }
}
