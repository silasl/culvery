package uk.co.siland.culvery.provider.music_sonos

import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertThrows

/** The tests' accept rule: speakers on this machine, on whatever port MockWebServer took. */
internal val onThisMachine: (String, Int) -> Boolean = { host, _ -> host == "localhost" || host == "127.0.0.1" }

private const val LOOPBACK_HOST = "127.0.0.1"
private val LOOPBACK: InetAddress = InetAddress.getByName(LOOPBACK_HOST)

/** assertThrows for a suspending [block]. */
internal inline fun <reified T : Throwable> throws(noinline block: suspend () -> Unit): T = assertThrows(T::class.java) { runBlocking { block() } }

/** The tests' client: gives up after 500 ms and never retries, so a silent speaker fails fast. */
internal fun testHttp(): OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofMillis(500)).retryOnConnectionFailure(false).build()

/** One Sonos speaker on localhost: answers by SOAP action, records each request; an action it wasn't given is fault 401. */
internal class FakeSpeaker : AutoCloseable {
    class Received(val path: String, val soapAction: String, val action: String, val body: String)

    private val server = MockWebServer()
    private val answers = ConcurrentHashMap<String, MockResponse>()
    private val browses = ConcurrentHashMap<String, MockResponse>()
    private val dropOnce = ConcurrentHashMap.newKeySet<String>()
    val received = CopyOnWriteArrayList<Received>()

    /** True: every request goes unanswered, so the call times out. */
    @Volatile var silent = false

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val soapAction = request.getHeader("SOAPACTION").orEmpty()
                val action = soapAction.trim('"').substringAfter('#')
                val body = request.body.readUtf8()
                received += Received(request.path.orEmpty(), soapAction, action, body)
                if (silent) return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                if (dropOnce.remove(action)) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                if (action == "Browse") browses[objectId(body)]?.let { return it }
                return answers[action] ?: MockResponse().setResponseCode(500).setBody(SonosFixtures.fault(401))
            }
        }
        server.start(LOOPBACK, 0)
    }

    // Not server.hostName: on Windows that resolves to the machine's name, which onThisMachine refuses.
    val host: String = LOOPBACK_HOST
    val port: Int = server.port
    val address: String = "$host:$port"

    fun answer(action: String, body: String) {
        answers[action] = MockResponse().setBody(body)
    }

    /** Answers a Browse of [objectId] (FV:2, SQ:); a Browse of any other object gets the answer given for "Browse". */
    fun answerBrowse(objectId: String, body: String) {
        browses[objectId] = MockResponse().setBody(body)
    }

    private fun objectId(body: String): String = Regex("<ObjectID>([^<]*)</ObjectID>").find(body)?.groupValues?.get(1).orEmpty()

    /** Answers [action], then closes the connection without saying so, as a speaker drops an idle keep-alive. */
    fun answerThenHangUp(action: String, body: String) {
        answers[action] = MockResponse().setBody(body).setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
    }

    /** The next request for [action] is read and then its connection dropped with no answer, as a busy network drops one. */
    fun dropNext(action: String) {
        dropOnce += action
    }

    /** Answers [action] only after [millis], so a test can look while the command is on its way. */
    fun answerAfter(action: String, body: String, millis: Long) {
        answers[action] = MockResponse().setBody(body).setBodyDelay(millis, TimeUnit.MILLISECONDS)
    }

    /** Answers [action] (a GET for art has none: "") with a redirect to [location]. */
    fun redirect(action: String, location: String) {
        answers[action] = MockResponse().setResponseCode(302).setHeader("Location", location)
    }

    fun refuse(action: String, code: Int) {
        answers[action] = MockResponse().setResponseCode(500).setBody(SonosFixtures.fault(code))
    }

    /** Answers [action] with HTTP [status] and no body: an error with no UPnP fault. */
    fun refuseWithoutFault(action: String, status: Int) {
        answers[action] = MockResponse().setResponseCode(status)
    }

    /** Every command that changes something answers with an empty answer. */
    fun acceptCommands() {
        listOf(
            "Play", "Pause", "Next", "RemoveAllTracksFromQueue", "AddURIToQueue", "SetPlayMode", "SetAVTransportURI",
            "SnapshotGroupVolume", "SetGroupVolume", "SetVolume",
        ).forEach { answer(it, SonosFixtures.soap(it)) }
    }

    fun actions(): List<String> = received.map { it.action }

    fun bodyOf(action: String): String = received.last { it.action == action }.body

    override fun close() = server.shutdown()
}
