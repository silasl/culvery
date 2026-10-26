package uk.co.siland.culvery.core.homeassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.net.homeHttpClient

@RunWith(AndroidJUnit4::class)
class HomeAssistantClientTest {
    private val server = MockWebServer()
    private val captured = mutableListOf<Pair<HaCall, String>>()
    private lateinit var link: HomeAssistantLink
    private lateinit var api: HomeAssistantClient

    @Before
    fun setUp() {
        server.start()
        link = HomeAssistantLink(server.url("/"), "test-token")
        api = HomeAssistantClient(homeHttpClient(COMMAND_TIMEOUT_MS), { true }) { what, text -> captured += what to text }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private suspend fun problemOf(block: suspend () -> Unit): HaProblem {
        try {
            block()
        } catch (e: HomeAssistantException) {
            assertThat(e.cause).isNull()
            return e.problem
        }
        throw AssertionError("nothing failed")
    }

    @Test
    fun everyCallCarriesTheBearerToken() = runBlocking<Unit> {
        answer(200, HaFixtures.API)
        answer(200, HaFixtures.STATES)
        answer(200, """{"entity_id":"vacuum.hoover","state":"docked","attributes":{}}""")
        answer(200, "[]")
        api.check(link)
        api.states(link)
        api.state(link, "vacuum.hoover")
        api.callService(link, "vacuum", "start", "vacuum.hoover")
        repeat(4) { assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer test-token") }
    }

    @Test
    fun checkAcceptsHomeAssistant() = runBlocking<Unit> {
        answer(200, HaFixtures.API)
        api.check(link)
        assertThat(server.takeRequest().path).isEqualTo("/api/")
        answer(200, """{"message":"Hello"}""")
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.NOT_HOME_ASSISTANT)
        answer(404, "Not found")
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.NOT_HOME_ASSISTANT)
        answer(200, "<html>router</html>")
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.NOT_HOME_ASSISTANT)
    }

    @Test
    fun statesAreReadFromTheFixture() = runBlocking<Unit> {
        answer(200, HaFixtures.STATES)
        val states = api.states(link)
        assertThat(server.takeRequest().path).isEqualTo("/api/states")
        assertThat(states.first { it.entityId == "vacuum.hoover" }.state).isEqualTo("docked")
        assertThat(captured.map { it.first }).containsExactly(HaCall.STATES)
    }

    @Test
    fun aRefusedTokenIsTokenRefused() = runBlocking<Unit> {
        repeat(3) { answer(401) }
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(problemOf { api.states(link) }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(problemOf { api.callService(link, "vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.TOKEN_REFUSED)
    }

    @Test
    fun aForbiddenIsNotTokenRefused() = runBlocking<Unit> {
        repeat(3) { answer(403) }
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.BLOCKED)
        assertThat(problemOf { api.states(link) }).isEqualTo(HaProblem.BLOCKED)
        assertThat(problemOf { api.callService(link, "vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.BLOCKED)
    }

    @Test
    fun aServiceCallSendsItsPathAndBody() = runBlocking<Unit> {
        answer(200, "[]")
        api.callService(link, "vacuum", "start", "vacuum.hoover")
        val start = server.takeRequest()
        assertThat(start.method).isEqualTo("POST")
        assertThat(start.path).isEqualTo("/api/services/vacuum/start")
        assertThat(start.body.readUtf8()).isEqualTo("""{"entity_id":"vacuum.hoover"}""")
        answer(200, "[]")
        api.callService(link, "climate", "set_preset_mode", "climate.hall", mapOf("preset_mode" to "eco"))
        val preset = server.takeRequest()
        assertThat(preset.path).isEqualTo("/api/services/climate/set_preset_mode")
        assertThat(preset.body.readUtf8()).isEqualTo("""{"entity_id":"climate.hall","preset_mode":"eco"}""")
    }

    @Test
    fun aMissingEntityIsNull() = runBlocking<Unit> {
        answer(404, """{"message":"Entity not found."}""")
        assertThat(api.state(link, "vacuum.hoover")).isNull()
        assertThat(server.takeRequest().path).isEqualTo("/api/states/vacuum.hoover")
    }

    @Test
    fun noAnswerInTimeIsUnreachable() = runBlocking<Unit> {
        val quick = HomeAssistantClient(homeHttpClient(100), { true }) { _, _ -> }
        server.enqueue(MockResponse().setBody("[]").setBodyDelay(1, TimeUnit.SECONDS))
        assertThat(problemOf { quick.states(link) }).isEqualTo(HaProblem.UNREACHABLE)
    }

    @Test
    fun theClientTheInjectorBuildsGivesCallsFiveSeconds() {
        val built = HomeAssistantClient(ApplicationProvider.getApplicationContext<Context>())
        assertThat(built.http.callTimeoutMillis).isEqualTo(5_000)
        assertThat(built.http.readTimeoutMillis).isEqualTo(5_000)
        assertThat(built.http.followRedirects).isFalse()
    }

    /** pin */
    @Test
    fun theCommandTimeoutIsFiveSeconds() {
        assertThat(COMMAND_TIMEOUT_MS).isEqualTo(5_000L)
    }

    @Test
    fun offlineIsOffline() = runBlocking<Unit> {
        val offline = HomeAssistantClient(homeHttpClient(COMMAND_TIMEOUT_MS), { false }) { _, _ -> }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThat(problemOf { offline.check(link) }).isEqualTo(HaProblem.OFFLINE)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.UNREACHABLE)
    }

    @Test
    fun aServiceHomeAssistantRefusesIsRefused() = runBlocking<Unit> {
        answer(400)
        answer(500)
        answer(500)
        assertThat(problemOf { api.callService(link, "vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.REFUSED)
        assertThat(problemOf { api.callService(link, "vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.REFUSED)
        assertThat(problemOf { api.states(link) }).isEqualTo(HaProblem.UNREACHABLE)
    }

    @Test
    fun anUnreadableAnswerIsUnreachable() = runBlocking<Unit> {
        answer(200, "<html>")
        assertThat(problemOf { api.states(link) }).isEqualTo(HaProblem.UNREACHABLE)
    }

    @Test
    fun cancellingTheCallerCancelsTheCall() = runBlocking<Unit> {
        val patient = homeHttpClient(30_000)
        val held = HomeAssistantClient(patient, { true }) { _, _ -> }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val call = scope.launch { held.states(link) }
        withTimeout(5_000) { while (patient.dispatcher.runningCallsCount() == 0) delay(10) }
        call.cancel()
        withTimeout(1_000) { call.join() }
        withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) }
    }

    @Test
    fun nothingPersonalReachesTheLog() = runBlocking<Unit> {
        ShadowLog.clear()
        answer(401)
        answer(403)
        answer(500)
        assertThat(problemOf { api.check(link) }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(problemOf { api.states(link) }).isEqualTo(HaProblem.BLOCKED)
        assertThat(problemOf { api.states(link) }).isEqualTo(HaProblem.UNREACHABLE)
        val quick = HomeAssistantClient(homeHttpClient(100), { true }) { _, _ -> }
        server.enqueue(MockResponse().setBody("[]").setBodyDelay(1, TimeUnit.SECONDS))
        assertThat(problemOf { quick.callService(link, "vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.UNREACHABLE)
        val logs = ShadowLog.getLogs().filter { it.tag == "HomeAssistantClient" }
        assertThat(logs).hasSize(4)
        logs.forEach { log ->
            val text = "${log.msg} ${log.throwable}"
            listOf("192.168", "127.0.0.1", "test-token", "hoover", "localhost").forEach { assertWithMessage(text).that(text).doesNotContain(it) }
        }
    }
}
