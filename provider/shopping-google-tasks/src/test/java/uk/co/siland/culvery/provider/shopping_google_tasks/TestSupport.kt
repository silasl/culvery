package uk.co.siland.culvery.provider.shopping_google_tasks

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.core.plugin.Connection

internal const val ACCOUNT = "family@example.com"

/** The connection a provider test uses: Google's list "L1" for [ACCOUNT]. */
internal val CONNECTION = Connection("c1", TASKS_PROVIDER_ID, "Google Tasks", mapOf(SHOPPING_CONFIG_ACCOUNT to ACCOUNT, CONFIG_LIST to "L1"))

/** What [block] threw, or null if it returned; a cancellation is never swallowed. */
internal suspend fun failureOf(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

/** A file of Google's answers under src/test/resources/tasks. */
internal fun resource(name: String): String =
    checkNotNull(object {}.javaClass.classLoader?.getResource("tasks/$name")) { "missing tasks/$name" }.readText()

/** Stands in for Play services' account chooser and consent screens; needs Robolectric. */
internal fun screens(): PendingIntent =
    PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

/** A request as it arrived: what the server saw, with its body read once. */
internal class Seen(val method: String, val path: String, val query: HttpUrl, val body: String, val auth: String?)

/** Google Tasks on a MockWebServer: the first matching rule answers (a `once` rule only once); no rule is a 404. */
internal class FakeTasksServer : Dispatcher() {
    private class Rule(val matches: (Seen) -> Boolean, val response: MockResponse, val once: Boolean)

    private val server = MockWebServer()
    private val rules = CopyOnWriteArrayList<Rule>()
    val seen = CopyOnWriteArrayList<Seen>()

    fun start(): HttpUrl {
        server.dispatcher = this
        server.start()
        return server.url("/tasks/v1/")
    }

    fun stop() = server.shutdown()

    fun on(matches: (Seen) -> Boolean, response: MockResponse, once: Boolean = false) {
        rules += Rule(matches, response, once)
    }

    /** [path] is the whole path, [method] the verb. */
    fun on(method: String, path: String, response: MockResponse, once: Boolean = false) =
        on({ it.method == method && it.path == path }, response, once)

    fun json(body: String, code: Int = 200): MockResponse = MockResponse().setResponseCode(code).setBody(body)

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val arrived = Seen(request.method.orEmpty(), url.encodedPath, url, request.body.clone().readUtf8(), request.getHeader("Authorization"))
        seen += arrived
        val rule = rules.firstOrNull { it.matches(arrived) } ?: return json(resource("error_404.json"), 404)
        if (rule.once) rules.remove(rule)
        return rule.response
    }
}
