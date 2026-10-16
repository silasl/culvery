package uk.co.siland.culvery.provider.lights_hue

import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertThrows

/** The tests' accept rule: a bridge on this machine, on whatever port MockWebServer took. */
internal val onThisMachine: (String, Int) -> Boolean = { host, _ -> host == "127.0.0.1" }

/** assertThrows for a suspending [block]. */
internal inline fun <reified T : Throwable> throws(noinline block: suspend () -> Unit): T =
    assertThrows(T::class.java) { runBlocking { block() } }

private const val LOOPBACK_HOST = "127.0.0.1"

/**
 * A Hue Bridge on localhost over HTTPS with the [keystore]'s certificate (`bridge` names [HueFixtures.BRIDGE_ID],
 * `other` another bridge): answers from [HueFixtures], records each request, and has switches for each failure.
 */
internal class FakeBridge(keystore: String = "bridge") : AutoCloseable {
    class Received(val method: String, val path: String, val body: String, val key: String?, val accept: String?)

    private val server = MockWebServer()
    val received = CopyOnWriteArrayList<Received>()

    /** Each answer is a bare 403, with no body to say why. */
    @Volatile var forbidden = false

    /** Each answer is 200 with v1's unauthorised error. */
    @Volatile var v1Unauthorised = false

    /** Each answer is a 500. */
    @Volatile var failing = false

    /** Each answer is a 200 that lists an error, as when a command reaches a light that is soft off. */
    @Volatile var answersWithErrors = false

    /** Each answer's headers come only after this many milliseconds. */
    @Volatile var delayMs = 0L

    /** Each answer is a 302 to this path on the same bridge. */
    @Volatile var redirectTo: String? = null

    /** Pairing answers "link button not pressed". */
    @Volatile var buttonPressed = true

    /** The stream's events come only after this many milliseconds. */
    @Volatile var streamDelayMs = 0L

    /** The stream stays open after its events, for [STREAM_HOLD_MS], as a live bridge's does. */
    @Volatile var streamHeld = false

    init {
        server.useHttps(TestCerts.serverSocketFactory(keystore), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                received += Received(
                    request.method.orEmpty(), path, request.body.readUtf8(), request.getHeader("hue-application-key"),
                    request.getHeader("Accept"),
                )
                return answer(request.method.orEmpty(), path).setHeadersDelay(delayMs, TimeUnit.MILLISECONDS)
            }
        }
        server.start(InetAddress.getByName(LOOPBACK_HOST), 0)
    }

    // Not server.hostName: on Windows that resolves to the machine's name, which onThisMachine refuses.
    val address: String = "$LOOPBACK_HOST:${server.port}"

    val requestCount: Int get() = server.requestCount

    private fun answer(method: String, path: String): MockResponse {
        redirectTo?.let { return MockResponse().setResponseCode(302).setHeader("Location", it) }
        if (forbidden) return MockResponse().setResponseCode(403)
        if (v1Unauthorised) return MockResponse().setBody(HueFixtures.UNAUTHORISED_V1)
        if (failing) return MockResponse().setResponseCode(500).setBody("""{"errors":[{"description":"internal error"}]}""")
        if (answersWithErrors) return MockResponse().setBody("""{"data":[],"errors":[{"description":"command may not have effect"}]}""")
        if (method == "PUT") return MockResponse().setBody("""{"data":[{"rid":"${path.substringAfterLast('/')}"}],"errors":[]}""")
        return when (path) {
            "/api" -> MockResponse().setBody(if (buttonPressed) HueFixtures.PAIRED else HueFixtures.NOT_PRESSED)
            "/api/config" -> MockResponse().setBody(HueFixtures.CONFIG)
            "/eventstream/clip/v2" -> stream()
            else -> RESOURCES[path.removePrefix("/clip/v2/resource/")]?.let { MockResponse().setBody(it) }
                ?: MockResponse().setResponseCode(404).setBody("""{"errors":[{"description":"not found"}]}""")
        }
    }

    private fun stream(): MockResponse {
        val events = HueFixtures.EVENTS + "\n\n"
        val response = MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBodyDelay(streamDelayMs, TimeUnit.MILLISECONDS)
        if (!streamHeld) return response.setBody(events)
        // The events, then a keep-alive comment only after the hold: the bridge looks open until then.
        val body = events + ": keep\n"
        return response.setBody(body).throttleBody(events.toByteArray().size.toLong(), STREAM_HOLD_MS, TimeUnit.MILLISECONDS)
    }

    override fun close() = server.shutdown()

    companion object {
        const val STREAM_HOLD_MS = 2_000L

        private val RESOURCES = mapOf(
            "room" to HueFixtures.ROOMS,
            "grouped_light" to HueFixtures.GROUPED_LIGHTS,
            "light" to HueFixtures.LIGHTS,
            "scene" to HueFixtures.SCENES,
            "behavior_instance" to HueFixtures.BEHAVIORS,
            "bridge" to HueFixtures.BRIDGE,
        )
    }
}
