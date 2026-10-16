package uk.co.siland.culvery.provider.lights_hue

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    /** [at] is [now] when the request arrived. */
    class Received(val method: String, val path: String, val body: String, val key: String?, val accept: String?, val at: Long)

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

    /** Each command is answered 200 with an error listed, while reads answer as usual. */
    @Volatile var refusingCommands = false

    /** Commands to these resource ids are answered 200 with an error listed. */
    @Volatile var refusedIds: Set<String> = emptySet()

    /** Called with each request before it is answered, as the bridge acting on it: a test may change [resources] here. */
    @Volatile var onRequest: (Received) -> Unit = {}

    /** The time stamped on each request; a test passes its scheduler's. */
    @Volatile var now: () -> Long = { 0L }

    /** While set, each command's answer waits for it to open; [held] gets a permit as each one starts waiting. */
    @Volatile var holdCommands: CountDownLatch? = null
    val held = Semaphore(0)

    /** What each resource read answers, by resource type; a test may change one. */
    val resources: MutableMap<String, String> = ConcurrentHashMap(RESOURCES)

    /** Each answer's headers come only after this many milliseconds. */
    @Volatile var delayMs = 0L

    /** Each answer is a 302 to this path on the same bridge. */
    @Volatile var redirectTo: String? = null

    /** Pairing answers "link button not pressed". */
    @Volatile var buttonPressed = true

    /** The stream's body: comment, `id:` and `data:` lines. */
    @Volatile var events: String = HueFixtures.EVENTS

    /** The stream's events come only after this many milliseconds. */
    @Volatile var streamDelayMs = 0L

    /** The stream stays open after its events, for [STREAM_HOLD_MS], as a live bridge's does. */
    @Volatile var streamHeld = false

    init {
        server.useHttps(TestCerts.serverSocketFactory(keystore), false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val got = Received(
                    request.method.orEmpty(), path, request.body.readUtf8(), request.getHeader("hue-application-key"),
                    request.getHeader("Accept"), now(),
                )
                received += got
                onRequest(got)
                if (request.method == "PUT") holdCommands?.let { hold ->
                    held.release()
                    hold.await(HOLD_LIMIT_S, TimeUnit.SECONDS)
                }
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
        if (method == "PUT" && (refusingCommands || path.substringAfterLast('/') in refusedIds)) return MockResponse().setBody("""{"data":[],"errors":[{"description":"device (grouped_light) is soft off"}]}""")
        if (method == "PUT") return MockResponse().setBody("""{"data":[{"rid":"${path.substringAfterLast('/')}"}],"errors":[]}""")
        return when (path) {
            "/api" -> MockResponse().setBody(if (buttonPressed) HueFixtures.PAIRED else HueFixtures.NOT_PRESSED)
            "/api/config" -> MockResponse().setBody(HueFixtures.CONFIG)
            "/eventstream/clip/v2" -> stream()
            else -> resource(path.removePrefix("/clip/v2/resource/"))?.let { MockResponse().setBody(it) }
                ?: MockResponse().setResponseCode(404).setBody("""{"errors":[{"description":"not found"}]}""")
        }
    }

    /** A resource type's page, or `type/id`'s page of that one; null when there is none. */
    private fun resource(path: String): String? {
        val page = resources[path.substringBefore('/')] ?: return null
        if ('/' !in path) return page
        val id = path.substringAfter('/')
        val one = Json.parseToJsonElement(page).jsonObject.getValue("data").jsonArray
            .filter { it.jsonObject["id"]?.jsonPrimitive?.content == id }
            .ifEmpty { return null }
        return buildJsonObject {
            put("errors", JsonArray(emptyList()))
            put("data", JsonArray(one))
        }.toString()
    }

    private fun stream(): MockResponse {
        val sent = events + "\n\n"
        val response = MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBodyDelay(streamDelayMs, TimeUnit.MILLISECONDS)
        if (!streamHeld) return response.setBody(sent)
        // The events, then a keep-alive comment only after the hold: the bridge looks open until then.
        val body = sent + ": keep\n"
        return response.setBody(body).throttleBody(sent.toByteArray().size.toLong(), STREAM_HOLD_MS, TimeUnit.MILLISECONDS)
    }

    override fun close() = server.shutdown()

    companion object {
        const val STREAM_HOLD_MS = 2_000L
        private const val HOLD_LIMIT_S = 5L

        private val RESOURCES = mapOf(
            "room" to HueFixtures.ROOMS,
            "grouped_light" to HueFixtures.GROUPED_LIGHTS,
            "light" to HueFixtures.LIGHTS,
            "scene" to HueFixtures.SCENES,
            "behavior_instance" to HueFixtures.BEHAVIORS,
        )
    }
}
