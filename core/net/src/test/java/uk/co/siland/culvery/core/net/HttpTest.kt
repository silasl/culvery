package uk.co.siland.culvery.core.net

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class HttpTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun call() = client.newCall(Request.Builder().url(server.url("/")).build())

    /** Fails if a timeout is left at OkHttp's default, a redirect is followed, a failed call is retried or the pool given is not used. */
    @Test
    fun aHomeNetworkClientHasOneTimeoutNoRedirectAndNoRetry() {
        val pool = ConnectionPool(2, 5, TimeUnit.SECONDS)
        val home = homeHttpClient(timeoutMs = 1_234, pool = pool)
        assertThat(listOf(home.connectTimeoutMillis, home.readTimeoutMillis, home.writeTimeoutMillis, home.callTimeoutMillis))
            .containsExactly(1_234, 1_234, 1_234, 1_234)
        assertThat(home.followRedirects).isFalse()
        assertThat(home.followSslRedirects).isFalse()
        assertThat(home.retryOnConnectionFailure).isFalse()
        assertThat(home.connectionPool).isSameInstanceAs(pool)
    }

    /** Fails if the code or the whole body is lost on the way to the caller. */
    @Test
    fun aWholeBodyIsRead() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("hello"))
        val answer = call().await(maxBytes = 1_024)
        assertThat(answer.code).isEqualTo(201)
        assertThat(answer.text).isEqualTo("hello")
        assertThat(answer.bytes).isEqualTo("hello".toByteArray())
    }

    /** Fails if a body past the limit is returned, or read in full, whether its length is announced or chunked. */
    @Test
    fun aBodyOverTheLimitIsBodyTooLarge() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(2_048)))
        assertThrows(BodyTooLarge::class.java) { runBlocking { call().await(maxBytes = 1_024) } }
        server.enqueue(MockResponse().setChunkedBody("x".repeat(2_048), 256))
        assertThrows(BodyTooLarge::class.java) { runBlocking { call().await(maxBytes = 1_024) } }
        server.enqueue(MockResponse().setBody("x".repeat(1_024)))
        assertThat(call().await(maxBytes = 1_024).bytes).hasLength(1_024)
    }

    /** Fails if cancelling the coroutine leaves the call running, so the server never sees the socket close. */
    @Test
    fun cancellingCancelsTheCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val held = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { call().await(maxBytes = 1_024) }
        assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull()
        held.cancelAndJoin()
        assertThat(held.isCancelled).isTrue()
        assertThat(socketClosed()).isTrue()
    }

    private fun socketClosed(): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (client.connectionPool.connectionCount() == 0) return true
            Thread.sleep(20)
        }
        return false
    }
}
