package uk.co.siland.culvery.provider.weather_openmeteo.di

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenMeteoModuleTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private val clients: Map<String, OkHttpClient> get() = mapOf("forecast" to forecastClient(), "search" to searchClient())

    @Test
    fun noClientFollowsASchemeRedirect() {
        clients.forEach { (name, client) ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://localhost:${server.port}/next"))
            val code = client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.code }
            assertThat(code).isEqualTo(302)
        }
        assertThat(server.requestCount).isEqualTo(2)
    }

    /** Regression guard. */
    @Test
    fun theClientsKeepTheirTimeouts() {
        assertThat(forecastClient().connectTimeoutMillis).isEqualTo(15_000)
        assertThat(forecastClient().readTimeoutMillis).isEqualTo(30_000)
        assertThat(forecastClient().callTimeoutMillis).isEqualTo(60_000)
        assertThat(searchClient().connectTimeoutMillis).isEqualTo(10_000)
        assertThat(searchClient().readTimeoutMillis).isEqualTo(15_000)
    }
}
