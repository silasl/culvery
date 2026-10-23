package uk.co.siland.culvery.provider.shopping_google_tasks.di

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
import uk.co.siland.culvery.capability.shopping.ShoppingProvider
import uk.co.siland.culvery.core.google.TokenSource
import uk.co.siland.culvery.core.plugin.AppVersion
import uk.co.siland.culvery.provider.shopping_google_tasks.GoogleTasksProvider
import uk.co.siland.culvery.provider.shopping_google_tasks.TASKS_BASE_URL
import uk.co.siland.culvery.provider.shopping_google_tasks.TasksApi

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)
private val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

/** Google compresses only for a User-Agent that contains "gzip"; OkHttp sends Accept-Encoding itself. */
private fun tasksClient(version: String): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT)
    .readTimeout(READ_TIMEOUT)
    .callTimeout(CALL_TIMEOUT)
    .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "Culvery/$version (gzip)").build()) }
    .build()

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleTasksModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: GoogleTasksProvider): ShoppingProvider

    companion object {
        // The client stays inside TasksApi, not in the graph, so another module's OkHttpClient can't collide with it.
        @Provides
        @Singleton
        fun api(tokens: TokenSource, @AppVersion version: String): TasksApi =
            TasksApi(TASKS_BASE_URL.toHttpUrl(), tokens, lazy { tasksClient(version) })
    }
}
