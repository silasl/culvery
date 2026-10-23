package uk.co.siland.culvery.provider.shopping_google_tasks

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.core.google.Authorization
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.UPDATE_PLAY_SERVICES
import uk.co.siland.culvery.core.google.authorizationFailure
import uk.co.siland.culvery.core.google.test.FakeAuthorizer
import uk.co.siland.culvery.core.google.test.FakeTokenSource
import uk.co.siland.culvery.core.google.test.granted
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.couldNotConnect
import uk.co.siland.culvery.core.plugin.test.RecordingToaster

private const val SAM = "sam@example.com"
private const val LISTS = "/tasks/v1/users/@me/lists"

@RunWith(AndroidJUnit4::class)
class TasksConnectFlowTest {
    private val google = FakeTasksServer()
    private val authorizer = FakeAuthorizer()
    private val toaster = RecordingToaster()
    private val baseUrl = google.start()
    private val flow = flowWith(authorizer)

    @After
    fun tearDown() = google.stop()

    private fun flowWith(authorizer: Authorizer, playServices: PlayServicesCheck = PlayServicesCheck { true }): TasksConnectFlow {
        val api = TasksApi(baseUrl, FakeTokenSource(), OkHttpClient())
        return TasksConnectFlow(authorizer, api, toaster, playServices)
    }

    @Test
    fun aGrantFindsTheShoppingList() = runTest {
        google.on("GET", LISTS, google.json(resource("tasklists.json")))
        val done = flow.start(SAM, existing = null) as TasksConnectStep.Done
        assertThat(done.connection.providerId).isEqualTo(TASKS_PROVIDER_ID)
        assertThat(done.connection.label).isEqualTo("Google Tasks")
        assertThat(done.connection.config).containsExactly(SHOPPING_CONFIG_ACCOUNT, SAM, CONFIG_LIST, "S1")
        assertThat(done.connection.id).isNotEmpty()
        assertThat(google.seen.map { it.method }).containsExactly("GET")
    }

    @Test
    fun noShoppingListMakesOne() = runTest {
        google.on("GET", LISTS, google.json(resource("tasklists_none.json")))
        google.on("POST", LISTS, google.json("""{"id":"S9","title":"Shopping"}"""))
        val done = flow.start(SAM, existing = null) as TasksConnectStep.Done
        assertThat(done.connection.config[CONFIG_LIST]).isEqualTo("S9")
        val posts = google.seen.filter { it.method == "POST" }
        assertThat(posts.map { it.body }).containsExactly("""{"title":"Shopping"}""")
    }

    @Test
    fun withoutTheTasksScopeNothingIsStored() = runTest {
        authorizer.next = granted("t", listOf("https://www.googleapis.com/auth/calendar.events"))
        assertThat(flow.start(SAM, existing = null)).isEqualTo(TasksConnectStep.Stopped)
        assertThat(toaster.shown).containsExactly("Google Tasks wasn’t allowed. Try again and tick Google Tasks.")
        assertThat(google.seen).isEmpty()
    }

    @Test
    fun screensAreAskedForFirst() = runTest {
        google.on("GET", LISTS, google.json(resource("tasklists.json")))
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(flow.start(SAM, existing = null)).isInstanceOf(TasksConnectStep.ShowScreens::class.java)
        val done = flow.afterScreens(SAM, existing = null, data = Intent()) as TasksConnectStep.Done
        assertThat(done.connection.config[CONFIG_LIST]).isEqualTo("S1")
        assertThat(toaster.shown).isEmpty()
    }

    @Test
    fun aCancelAfterTheScreensSaysNothing() = runTest {
        val cancelling = object : Authorizer by authorizer {
            override fun authorizationFrom(data: Intent?): Authorization = throw authorizationFailure(CommonStatusCodes.CANCELED)
        }
        assertThat(flowWith(cancelling).afterScreens(SAM, existing = null, data = Intent())).isEqualTo(TasksConnectStep.Stopped)
        assertThat(toaster.shown).isEmpty()
    }

    @Test
    fun screensThatStillDoNotGrantStopAndSaySo() = runTest {
        authorizer.fromScreens = Authorization.NeedsUser(screens())
        assertThat(flow.afterScreens(SAM, existing = null, data = Intent())).isEqualTo(TasksConnectStep.Stopped)
        assertThat(toaster.shown).containsExactly(couldNotConnect("Google Tasks", unreachable = false))
    }

    @Test
    fun theAccountAskedForIsTheOneChosen() = runTest {
        google.on("GET", LISTS, google.json(resource("tasklists.json")))
        flow.start(SAM, existing = null)
        assertThat(authorizer.accounts).containsExactly(SAM)
        assertThat(authorizer.scopesAsked).containsExactly(TASKS_SCOPES)
    }

    @Test
    fun aReAllowKeepsTheListAndNeverLooksItUpOrMakesAnother() = runTest {
        val existing = Connection("c7", TASKS_PROVIDER_ID, "Google Tasks", mapOf(SHOPPING_CONFIG_ACCOUNT to SAM, CONFIG_LIST to "L7"))
        val done = flow.start(SAM, existing) as TasksConnectStep.Done
        assertThat(done.connection).isEqualTo(existing)
        assertThat(google.seen).isEmpty()
    }

    @Test
    fun aReAllowWithoutTheScopeStillStops() = runTest {
        val existing = Connection("c7", TASKS_PROVIDER_ID, "Google Tasks", mapOf(SHOPPING_CONFIG_ACCOUNT to SAM, CONFIG_LIST to "L7"))
        authorizer.next = granted("t", emptyList())
        assertThat(flow.start(SAM, existing)).isEqualTo(TasksConnectStep.Stopped)
        assertThat(toaster.shown).hasSize(1)
    }

    @Test
    fun aListLookupThatFailsSaysCouldNotConnect() = runTest {
        google.on("GET", LISTS, google.json("{}", 503))
        assertThat(flow.start(SAM, existing = null)).isEqualTo(TasksConnectStep.Stopped)
        assertThat(toaster.shown).containsExactly(couldNotConnect("Google Tasks", unreachable = true))
    }

    @Test
    fun playServicesThatCantSignInStartNothing() = runTest {
        val stuck = flowWith(authorizer, PlayServicesCheck { false })
        assertThat(stuck.start(SAM, existing = null)).isEqualTo(TasksConnectStep.Stopped)
        assertThat(toaster.shown).containsExactly(UPDATE_PLAY_SERVICES)
        assertThat(authorizer.accounts).isEmpty()
    }
}
