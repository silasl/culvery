package uk.co.siland.culvery.core.homeassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.net.homeHttpClient

/** The test plays Home Assistant's end of the socket: each `after…` says what the server sends next. */
@RunWith(AndroidJUnit4::class)
class HomeAssistantSocketTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()
    private val serverClosed = CountDownLatch(1)
    private lateinit var link: HomeAssistantLink
    private lateinit var api: HomeAssistantClient

    private var greeting: (WebSocket) -> Unit = { it.send(HaFixtures.AUTH_REQUIRED) }
    private var afterAuth: (WebSocket) -> Unit = { it.send(HaFixtures.AUTH_OK) }
    private var afterSubscribe: (WebSocket) -> Unit = { it.send(HaFixtures.ADDED) }

    private val ids = linkedSetOf("vacuum.hoover", "sensor.hoover_battery")

    @Before
    fun setUp() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = greeting(webSocket)

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        received += text
                        when (received.size) {
                            1 -> afterAuth(webSocket)
                            2 -> afterSubscribe(webSocket)
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        serverClosed.countDown()
                        webSocket.close(code, null)
                    }
                },
            ),
        )
        server.start()
        link = HomeAssistantLink(server.url("/"), "test-token")
        api = HomeAssistantClient(homeHttpClient(COMMAND_TIMEOUT_MS), { true }) { _, _ -> }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun Flow<Map<String, EntityState?>>.firstReads(count: Int) = withTimeout(5_000) { take(count).toList() }

    private suspend fun failureOf(flow: Flow<*>): HomeAssistantException {
        var caught: HomeAssistantException? = null
        withTimeout(5_000) { flow.catch { caught = it as HomeAssistantException }.collect {} }
        return checkNotNull(caught) { "the flow ended without failing" }
    }

    @Test
    fun theSocketAuthsThenSubscribes() = runBlocking<Unit> {
        api.follow(link, ids).firstReads(1)
        assertThat(server.takeRequest().path).isEqualTo("/api/websocket")
        assertThat(received.toList()).containsExactly(
            """{"type":"auth","access_token":"test-token"}""",
            """{"id":1,"type":"subscribe_entities","entity_ids":["vacuum.hoover","sensor.hoover_battery"]}""",
        ).inOrder()
    }

    @Test
    fun theFirstEventIsTheFullRead() = runBlocking<Unit> {
        afterSubscribe = {
            it.send("""{"id":1,"type":"event","event":{"a":{"vacuum.hoover":{"s":"docked","a":{"friendly_name":"Hoover"}}}}}""")
        }
        val read = api.follow(link, ids).firstReads(1).single()
        assertThat(read.keys).containsExactly("vacuum.hoover", "sensor.hoover_battery").inOrder()
        assertThat(read.getValue("vacuum.hoover")?.state).isEqualTo("docked")
        assertThat(read.getValue("sensor.hoover_battery")).isNull()
    }

    @Test
    fun changesApplyToWhatWasRead() = runBlocking<Unit> {
        afterSubscribe = {
            it.send(HaFixtures.ADDED)
            it.send(HaFixtures.CHANGED)
            it.send(HaFixtures.REMOVED)
        }
        val reads = api.follow(link, ids).firstReads(3)
        assertThat(reads[0].getValue("vacuum.hoover")?.state).isEqualTo("docked")
        assertThat(reads[0].getValue("sensor.hoover_battery")?.state).isEqualTo("86")
        assertThat(reads[1].getValue("vacuum.hoover")?.state).isEqualTo("cleaning")
        assertThat(reads[1].getValue("sensor.hoover_battery")?.state).isEqualTo("86")
        assertThat(reads[2].getValue("vacuum.hoover")).isNull()
        assertThat(reads[2].getValue("sensor.hoover_battery")?.state).isEqualTo("86")
    }

    @Test
    fun anInvalidAuthIsTokenRefused() = runBlocking<Unit> {
        afterAuth = { it.send(HaFixtures.AUTH_INVALID) }
        val failure = failureOf(api.follow(link, ids))
        assertThat(failure.problem).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(received).hasSize(1)
    }

    @Test
    fun aRefusedSubscriptionIsRefused() = runBlocking<Unit> {
        afterSubscribe = { it.send("""{"id":1,"type":"result","success":false,"error":{"code":"x"}}""") }
        assertThat(failureOf(api.follow(link, ids)).problem).isEqualTo(HaProblem.REFUSED)
    }

    @Test
    fun aClosedSocketEndsTheFlow() = runBlocking<Unit> {
        afterSubscribe = {
            it.send(HaFixtures.ADDED)
            it.close(1001, null)
        }
        val seen = mutableListOf<Map<String, EntityState?>>()
        var failure: HomeAssistantException? = null
        withTimeout(5_000) { api.follow(link, ids).onEach { seen += it }.catch { failure = it as HomeAssistantException }.collect {} }
        assertThat(seen).hasSize(1)
        assertThat(failure?.problem).isEqualTo(HaProblem.UNREACHABLE)
        assertThat(failure?.cause).isNull()
    }

    @Test
    fun cancellingTheCollectorClosesTheSocket() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val read = CountDownLatch(1)
        api.follow(link, ids).onEach { read.countDown() }.launchIn(scope)
        assertThat(read.await(5, TimeUnit.SECONDS)).isTrue()
        scope.cancel()
        assertThat(serverClosed.await(5, TimeUnit.SECONDS)).isTrue()
    }

    /** pin: the REST timeout (5 s in production) doesn't end an open socket. */
    @Test
    fun theSocketOutlivesTheCommandTimeout() = runBlocking<Unit> {
        val quick = HomeAssistantClient(homeHttpClient(100), { true }) { _, _ -> }
        afterSubscribe = {
            Thread {
                Thread.sleep(500)
                it.send(HaFixtures.ADDED)
            }.start()
        }
        assertThat(quick.follow(link, ids).firstReads(1)).hasSize(1)
    }

    private fun socketLines() = ShadowLog.getLogs().filter { it.tag == "HomeAssistantClient" }

    @Test
    fun endingTheSocketOurselvesLogsNothing() = runBlocking<Unit> {
        ShadowLog.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val read = CountDownLatch(1)
        api.follow(link, ids).onEach { read.countDown() }.launchIn(scope)
        assertThat(read.await(5, TimeUnit.SECONDS)).isTrue()
        scope.cancel()
        assertThat(serverClosed.await(5, TimeUnit.SECONDS)).isTrue()
        Thread.sleep(300)
        assertThat(socketLines()).isEmpty()
    }

    @Test
    fun aRefusedAuthLogsOnceAndNothingPersonal() = runBlocking<Unit> {
        ShadowLog.clear()
        afterAuth = { it.send(HaFixtures.AUTH_INVALID) }
        failureOf(api.follow(link, ids))
        assertThat(serverClosed.await(5, TimeUnit.SECONDS)).isTrue()
        Thread.sleep(300)
        val lines = socketLines()
        assertThat(lines).hasSize(1)
        listOf("192.168", "127.0.0.1", "test-token", "hoover", "localhost").forEach { assertThat(lines.single().msg).doesNotContain(it) }
    }

    @Test
    fun aServerClosingLogsOnce() = runBlocking<Unit> {
        ShadowLog.clear()
        afterSubscribe = { it.close(1001, null) }
        failureOf(api.follow(link, ids))
        Thread.sleep(300)
        assertThat(socketLines()).hasSize(1)
    }

    @Test
    fun registryAsksAndCloses() = runBlocking<Unit> {
        afterSubscribe = { it.send(HaFixtures.REGISTRY) }
        val entries = withTimeout(5_000) { api.registry(link) }
        assertThat(entries).hasSize(6)
        assertThat(server.takeRequest().path).isEqualTo("/api/websocket")
        assertThat(received.toList()).containsExactly(
            """{"type":"auth","access_token":"test-token"}""",
            """{"id":1,"type":"config/entity_registry/list_for_display"}""",
        ).inOrder()
        assertThat(serverClosed.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun aRefusedRegistryIsRefused() = runBlocking<Unit> {
        afterSubscribe = { it.send("""{"id":1,"type":"result","success":false,"error":{"code":"unauthorized"}}""") }
        val failure = runCatching { withTimeout(5_000) { api.registry(link) } }.exceptionOrNull() as HomeAssistantException
        assertThat(failure.problem).isEqualTo(HaProblem.REFUSED)
    }

    @Test
    fun aSocketClosedBeforeTheRegistryIsUnreachable() = runBlocking<Unit> {
        afterSubscribe = { it.close(1001, null) }
        val failure = runCatching { withTimeout(5_000) { api.registry(link) } }.exceptionOrNull() as HomeAssistantException
        assertThat(failure.problem).isEqualTo(HaProblem.UNREACHABLE)
    }

    @Test
    fun aRegistryThatNeverComesIsUnreachableAfterTheCommandTimeout() = runBlocking<Unit> {
        val quick = HomeAssistantClient(homeHttpClient(COMMAND_TIMEOUT_MS), { true }, registryTimeoutMs = 200) { _, _ -> }
        afterSubscribe = {}
        val failure = runCatching { withTimeout(5_000) { quick.registry(link) } }.exceptionOrNull() as HomeAssistantException
        assertThat(failure.problem).isEqualTo(HaProblem.UNREACHABLE)
    }
}
