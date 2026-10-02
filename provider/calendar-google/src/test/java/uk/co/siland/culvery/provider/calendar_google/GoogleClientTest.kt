package uk.co.siland.culvery.provider.calendar_google

import com.google.common.truth.Truth.assertThat
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import uk.co.siland.culvery.provider.calendar_google.di.googleClient

class GoogleClientTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    /** E1: Google compresses only for a User-Agent containing "gzip"; OkHttp asks for gzip itself. */
    @Test
    fun everyCallSaysCulveryAndGzipSoGoogleCompresses() {
        server.enqueue(MockResponse().setBody("{}"))
        server.start()
        googleClient("1.0.0-beta1").newCall(Request.Builder().url(server.url("/calendar/v3/")).build()).execute().use { }
        val sent = server.takeRequest()
        assertThat(sent.getHeader("User-Agent")).isEqualTo("Culvery/1.0.0-beta1 (gzip)")
        assertThat(sent.getHeader("Accept-Encoding")).isEqualTo("gzip")
    }

    /** 4b follow-up: a body that drips in can't hold a sync pass past a minute. */
    @Test
    fun aWholeCallIsLimitedToAMinute() {
        assertThat(googleClient("1.0.0-beta1").callTimeoutMillis).isEqualTo(60_000)
    }
}
