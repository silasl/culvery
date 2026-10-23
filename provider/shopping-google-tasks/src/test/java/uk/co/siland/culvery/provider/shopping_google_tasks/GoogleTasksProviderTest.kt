package uk.co.siland.culvery.provider.shopping_google_tasks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.shopping.AccountGoneException
import uk.co.siland.culvery.capability.shopping.ItemGoneException
import uk.co.siland.culvery.capability.shopping.ListGoneException
import uk.co.siland.culvery.capability.shopping.ProviderAccounts
import uk.co.siland.culvery.capability.shopping.ShoppingRejectedException
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.test.FakeAuthorizer
import uk.co.siland.culvery.core.google.test.FakeGoogleAccounts
import uk.co.siland.culvery.core.google.test.FakeTokenSource
import uk.co.siland.culvery.core.google.test.failureOf
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.plugin.test.RecordingToaster

private const val LIST_PATH = "/tasks/v1/lists/L1/tasks"

@RunWith(AndroidJUnit4::class)
class GoogleTasksProviderTest {
    private val google = FakeTasksServer()
    private val tokens = FakeTokenSource()
    private val accounts = FakeGoogleAccounts(listOf(ACCOUNT))
    private lateinit var provider: GoogleTasksProvider

    @Before
    fun setUp() {
        // No retry on a dropped connection, so a drop is one failure rather than a quiet second try.
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(Duration.ofSeconds(2)).build()
        provider = GoogleTasksProvider(TasksApi(google.start(), tokens, client), FakeAuthorizer(), accounts, PlayServicesCheck { true }, RecordingToaster())
    }

    @After
    fun tearDown() {
        // Every item call is under the connection's own list, and nothing is ever deleted; connecting is the only other path.
        val items = google.seen.filterNot { it.path.startsWith("/tasks/v1/users/@me/lists") }
        items.forEach { assertThat(it.path).startsWith("/tasks/v1/lists/L1/") }
        assertThat(google.seen.map { it.method }).doesNotContain("DELETE")
        google.stop()
    }

    private fun serveOpenTasks() {
        google.on({ it.method == "GET" && it.path == LIST_PATH && it.query.queryParameter("pageToken") == null }, google.json(resource("tasks_page1.json")))
        google.on({ it.method == "GET" && it.path == LIST_PATH && it.query.queryParameter("pageToken") == "page2" }, google.json(resource("tasks_page2.json")))
    }

    @Test
    fun itemsAreTheOpenTasksInGoogleOrder() = runTest {
        serveOpenTasks()
        val items = provider.items(CONNECTION)
        // Milk (position 1) and its subtask Butter, then Bread (2), then Eggs whose parent isn't open (3); the blank title is skipped.
        assertThat(items.map { it.text }).containsExactly("Milk", "Butter", "Bread", "Eggs").inOrder()
        assertThat(items.first { it.text == "Milk" }.changedAt).isEqualTo(Instant.parse("2026-10-21T09:30:00.000Z"))
        assertThat(items.first { it.text == "Eggs" }.id).isEqualTo("t4")
        val first = google.seen.first().query
        assertThat(first.queryParameter("showCompleted")).isEqualTo("false")
        assertThat(first.queryParameter("showHidden")).isEqualTo("false")
        assertThat(first.queryParameter("maxResults")).isEqualTo("100")
        assertThat(google.seen.map { it.query.queryParameter("pageToken") }).containsExactly(null, "page2").inOrder()
    }

    @Test
    fun historyIsWhatWasTickedSince() = runTest {
        google.on("GET", LIST_PATH, google.json(resource("tasks_history.json")))
        val ticked = provider.history(CONNECTION, Instant.parse("2026-10-16T10:00:00Z"))
        assertThat(ticked.map { it.text to it.changedAt }).containsExactly(
            "Bananas" to Instant.parse("2026-10-17T12:00:00Z"),
            "Milk" to Instant.parse("2026-10-18T09:00:00Z"),
        ).inOrder()
        val query = google.seen.single().query
        assertThat(query.queryParameter("showCompleted")).isEqualTo("true")
        assertThat(query.queryParameter("showHidden")).isEqualTo("true")
        assertThat(query.queryParameter("completedMin")).isEqualTo("2026-10-16T10:00:00Z")
    }

    @Test
    fun anAddPostsOnlyTheTitle() = runTest {
        google.on("POST", LIST_PATH, google.json(resource("task_inserted.json")))
        val added = provider.add(CONNECTION, "Milk", "key-1")
        val sent = google.seen.single()
        assertThat(sent.body).isEqualTo("""{"title":"Milk"}""")
        assertThat(sent.query.queryParameter("previous")).isNull()
        assertThat(sent.query.queryParameter("parent")).isNull()
        assertThat(added.id).isEqualTo("t-new")
        assertThat(added.text).isEqualTo("Milk")
    }

    @Test
    fun aTickAndAnUntickPatchTheStatus() = runTest {
        google.on("PATCH", "$LIST_PATH/t1", google.json("{}"))
        provider.tick(CONNECTION, "t1")
        provider.untick(CONNECTION, "t1")
        assertThat(google.seen.map { it.method to it.path }).containsExactly("PATCH" to "$LIST_PATH/t1", "PATCH" to "$LIST_PATH/t1")
        assertThat(google.seen.map { it.body }).containsExactly("""{"status":"completed"}""", """{"status":"needsAction","completed":null}""").inOrder()
    }

    @Test
    fun aMissingListIsListGone() = runTest {
        assertThat(failureOf { provider.items(CONNECTION) }).isInstanceOf(ListGoneException::class.java)
        assertThat(failureOf { provider.add(CONNECTION, "Milk", "k") }).isInstanceOf(ListGoneException::class.java)
        assertThat(failureOf { provider.history(CONNECTION, Instant.EPOCH) }).isInstanceOf(ListGoneException::class.java)
    }

    @Test
    fun aConnectionWithoutAListIsListGoneAndAsksNothing() = runTest {
        val none = CONNECTION.copy(config = CONNECTION.config - CONFIG_LIST)
        assertThat(failureOf { provider.items(none) }).isInstanceOf(ListGoneException::class.java)
        assertThat(google.seen).isEmpty()
    }

    @Test
    fun aMissingItemIsItemGone() = runTest {
        assertThat(failureOf { provider.tick(CONNECTION, "t1") }).isInstanceOf(ItemGoneException::class.java)
        google.on("PATCH", "$LIST_PATH/t2", google.json("{}", 410))
        assertThat(failureOf { provider.untick(CONNECTION, "t2") }).isInstanceOf(ItemGoneException::class.java)
    }

    @Test
    fun aMissingScopeNeedsSigningIn() = runTest {
        google.on("GET", LIST_PATH, google.json(resource("error_scope.json"), 403))
        assertThat(failureOf { provider.items(CONNECTION) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun a401TwiceNeedsSigningInAfterClearingTheTokenOnce() = runTest {
        google.on("GET", LIST_PATH, google.json("{}", 401))
        assertThat(failureOf { provider.items(CONNECTION) }).isInstanceOf(NeedsSignInException::class.java)
        assertThat(tokens.invalidated).containsExactly("token-1")
        assertThat(google.seen).hasSize(2)
    }

    @Test
    fun tryLaterAnswersAreUnreachable() = runTest {
        val answers = listOf(
            google.json("{}", 429),
            google.json("{}", 503),
            google.json("""{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}""", 403),
        )
        for (answer in answers) {
            google.on("GET", LIST_PATH, answer, once = true)
            assertThat(failureOf { provider.items(CONNECTION) }).isInstanceOf(UnreachableException::class.java)
        }
    }

    @Test
    fun aDroppedConnectionIsUnreachable() = runTest {
        google.on("GET", LIST_PATH, MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failureOf { provider.items(CONNECTION) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aRefusedAddIsRejected() = runTest {
        google.on("POST", LIST_PATH, google.json("""{"error":{"code":400,"errors":[{"reason":"invalid"}]}}""", 400))
        assertThat(failureOf { provider.add(CONNECTION, "Milk", "k") }).isInstanceOf(ShoppingRejectedException::class.java)
    }

    @Test
    fun aRefusedTickIsRejected() = runTest {
        google.on("PATCH", "$LIST_PATH/t1", google.json("{}", 400))
        assertThat(failureOf { provider.tick(CONNECTION, "t1") }).isInstanceOf(ShoppingRejectedException::class.java)
    }

    @Test
    fun onlyA400IsRejectedEveryOtherRefusalIsTryLater() = runTest {
        val unknown403 = google.json("""{"error":{"code":403,"errors":[{"reason":"accessNotConfigured"}]}}""", 403)
        val unparseable403 = google.json("<html>Forbidden</html>", 403)
        for (answer in listOf(unknown403, unparseable403, google.json("{}", 408))) {
            google.on("PATCH", "$LIST_PATH/t1", answer, once = true)
            assertThat(failureOf { provider.tick(CONNECTION, "t1") }).isInstanceOf(UnreachableException::class.java)
            google.on("PATCH", "$LIST_PATH/t1", answer, once = true)
            assertThat(failureOf { provider.untick(CONNECTION, "t1") }).isInstanceOf(UnreachableException::class.java)
            google.on("POST", LIST_PATH, answer, once = true)
            assertThat(failureOf { provider.add(CONNECTION, "Milk", "k") }).isInstanceOf(UnreachableException::class.java)
        }
    }

    @Test
    fun theFieldsAskedForCoverEveryFieldTheMappingReadsAndThePageToken() = runTest {
        serveOpenTasks()
        provider.items(CONNECTION)
        val open = google.seen.first().query.queryParameter("fields").orEmpty()
        listOf("id", "title", "status", "updated", "parent", "position", "nextPageToken").forEach { assertThat(open).contains(it) }
        google.seen.clear()
        provider.history(CONNECTION, Instant.EPOCH)
        val history = google.seen.first().query.queryParameter("fields").orEmpty()
        listOf("id", "title", "status", "completed", "nextPageToken").forEach { assertThat(history).contains(it) }
    }

    @Test
    fun anAccountNoLongerConnectedIsAccountGone() = runTest {
        accounts.connected.value = emptyList()
        assertThat(failureOf { provider.items(CONNECTION) }).isInstanceOf(AccountGoneException::class.java)
        assertThat(failureOf { provider.tick(CONNECTION, "t1") }).isInstanceOf(AccountGoneException::class.java)
        assertThat(failureOf { provider.remakeList(CONNECTION) }).isInstanceOf(AccountGoneException::class.java)
        assertThat(google.seen).isEmpty()
    }

    @Test
    fun theAccountIsMatchedWhateverItsCase() = runTest {
        accounts.connected.value = listOf("Family@Example.com")
        serveOpenTasks()
        assertThat(provider.items(CONNECTION)).isNotEmpty()
    }

    @Test
    fun everyTokenIsForTheTasksScope() = runTest {
        serveOpenTasks()
        google.on("PATCH", "$LIST_PATH/t1", google.json("{}"))
        provider.items(CONNECTION)
        provider.tick(CONNECTION, "t1")
        assertThat(tokens.scopesAsked).isNotEmpty()
        tokens.scopesAsked.forEach { assertThat(it).isEqualTo(TASKS_SCOPES) }
        assertThat(TASKS_SCOPES).containsExactly("https://www.googleapis.com/auth/tasks")
    }

    @Test
    fun noAccountsBlocksWithTheCalendarLine() = runTest {
        accounts.connected.value = emptyList()
        assertThat(provider.accounts.first())
            .isEqualTo(ProviderAccounts.Blocked("Connect Google Calendar first. Shopping uses the same account."))
        accounts.connected.value = listOf("family@example.com", "sam@example.com")
        assertThat(provider.accounts.first()).isEqualTo(ProviderAccounts.Ready(listOf("family@example.com", "sam@example.com")))
    }

    @Test
    fun remakingTheListFindsOrMakesItAndKeepsTheRest() = runTest {
        google.on("GET", "/tasks/v1/users/@me/lists", google.json(resource("tasklists_none.json")))
        google.on("POST", "/tasks/v1/users/@me/lists", google.json("""{"id":"S2","title":"Shopping"}"""))
        val remade = provider.remakeList(CONNECTION)
        assertThat(remade.config[CONFIG_LIST]).isEqualTo("S2")
        assertThat(remade.id).isEqualTo(CONNECTION.id)
        assertThat(remade.config.keys).containsExactlyElementsIn(CONNECTION.config.keys)
    }
}
