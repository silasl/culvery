package uk.co.siland.culvery.provider.calendar_google

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection

private const val COULD_NOT_CONNECT = "Couldn't connect to Google Calendar — try again"

@RunWith(AndroidJUnit4::class)
class GoogleConnectFlowTest {
    private val google = FakeGoogleServer()
    private val authorizer = FakeAuthorizer()
    private val toasts = Toasts()
    private lateinit var api: GoogleApi
    private lateinit var flow: GoogleConnectFlow
    private val stored = Connection("g1", GOOGLE_PROVIDER_ID, GOOGLE_LABEL, mapOf(CONFIG_ACCOUNT to "family@example.com"))

    @Before
    fun setUp() {
        api = GoogleApi(google.start(), FakeTokenSource(), OkHttpClient())
        flow = GoogleConnectFlow(authorizer, api, toasts, PlayServicesCheck { true })
    }

    @After
    fun tearDown() {
        google.shutdown()
        // P8, for every test: nothing logged names an account or a token.
        ShadowLog.getLogs().forEach { log ->
            val causes = generateSequence(log.throwable) { it.cause }.joinToString(" ")
            val text = "${log.msg} $causes"
            assertWithMessage(text).that(text).doesNotContain("@")
            assertWithMessage(text).that(text).doesNotContain("token")
            assertWithMessage(text).that(text).doesNotContain("t1")
        }
    }

    /** M4: without a usable Play services, Google's screens would fail; say what to do and start nothing. */
    @Test
    fun withoutUsablePlayServicesNothingStartsAndItSaysWhy() = runTest {
        val stuck = GoogleConnectFlow(authorizer, api, toasts, PlayServicesCheck { false })
        assertThat(stuck.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly(UPDATE_PLAY_SERVICES)
        assertThat(authorizer.accounts).isEmpty()
    }

    @Test
    fun aGrantBecomesANewConnectionForTheAccountItsPrimaryCalendarNames() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        val step = flow.start(existing = null) as ConnectStep.Done
        // The chooser's grant, then the silent grant every later call will ask for, for the email Google named.
        assertThat(authorizer.accounts).containsExactly(null, "family@example.com").inOrder()
        assertThat(step.connection.providerId).isEqualTo(GOOGLE_PROVIDER_ID)
        assertThat(step.connection.label).isEqualTo(GOOGLE_LABEL)
        assertThat(step.connection.config).containsExactly(CONFIG_ACCOUNT, "family@example.com")
        assertThat(step.connection.id).isNotEmpty()
        assertThat(google.requests.single().getHeader("Authorization")).isEqualTo("Bearer token-granted")
    }

    @Test
    fun screensTheUserMustSeeComeFirstThenTheConnectionIsMade() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(flow.start(existing = null)).isInstanceOf(ConnectStep.ShowScreens::class.java)
        val done = flow.afterScreens(existing = null, data = Intent()) as ConnectStep.Done
        assertThat(done.connection.config[CONFIG_ACCOUNT]).isEqualTo("family@example.com")
    }

    @Test
    fun aReconnectAsksForTheStoredAccountAndKeepsTheConnectionsId() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        val done = flow.start(existing = stored) as ConnectStep.Done
        assertThat(authorizer.accounts).containsExactly("family@example.com", "family@example.com")
        assertThat(done.connection.id).isEqualTo("g1")
    }

    @Test
    fun aReconnectWithADifferentAccountIsRefused() = runTest {
        google.addCalendar("someone.else@example.com", "Theirs", primary = true)
        assertThat(flow.start(existing = stored)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly("That's a different Google account. Reconnect with family@example.com.")
        // Refused before the silent grant for the other account was asked for.
        assertThat(authorizer.accounts).containsExactly("family@example.com")
    }

    @Test
    fun aReconnectMatchesTheStoredAccountWhateverItsCase() = runTest {
        google.addCalendar("Family@Example.com", "Family", primary = true)
        val done = flow.start(existing = stored) as ConnectStep.Done
        assertThat(done.connection.id).isEqualTo("g1")
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun screensThatStillDoNotGrantStopAndSaySo() = runTest {
        authorizer.fromScreens = Authorization.NeedsUser(screens())
        assertThat(flow.afterScreens(existing = null, data = Intent())).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly(COULD_NOT_CONNECT)
    }

    @Test
    fun backingOutOfTheChooserStopsQuietly() = runTest {
        authorizer.failWith = authorizationFailure(CommonStatusCodes.CANCELED)
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun anyOtherPlayServicesFailureStopsAndSaysSo() = runTest {
        listOf(CommonStatusCodes.DEVELOPER_ERROR, CommonStatusCodes.INTERNAL_ERROR, CommonStatusCodes.SIGN_IN_REQUIRED).forEach { status ->
            toasts.messages.clear()
            authorizer.failWith = authorizationFailure(status)
            assertWithMessage("status $status").that(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
            assertWithMessage("status $status").that(toasts.messages).containsExactly(COULD_NOT_CONNECT)
        }
    }

    @Test
    fun aGrantWithoutBothScopesStoresNothingAndSaysSo() = runTest {
        google.addCalendar("family@example.com", "Family", primary = true)
        // The person unticked "see and edit events" on the consent screen.
        authorizer.next = Authorization.Granted("t1", listOf(CALENDAR_SCOPES.first()))
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly(COULD_NOT_CONNECT)
        assertThat(google.requests).isEmpty()
    }

    @Test
    fun anAccountPlayServicesWontGrantSilentlyStoresNothingAndSaysSo() = runTest {
        // E.g. the primary calendar says googlemail.com and the device's account is gmail.com.
        google.addCalendar("family@example.com", "Family", primary = true)
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(flow.start(existing = null)).isInstanceOf(ConnectStep.ShowScreens::class.java)
        authorizer.next = Authorization.NeedsUser(screens())
        assertThat(flow.afterScreens(existing = null, data = Intent())).isEqualTo(ConnectStep.Stopped)
        assertThat(authorizer.accounts).containsExactly(null, "family@example.com").inOrder()
        assertThat(toasts.messages).containsExactly(COULD_NOT_CONNECT)
    }

    @Test
    fun noNetworkStopsAndSaysSo() = runTest {
        authorizer.failWith = UnreachableException("offline")
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        google.addCalendar("family@example.com", "Family", primary = true)
        authorizer.failWith = null
        google.failNext(503)
        assertThat(flow.start(existing = null)).isEqualTo(ConnectStep.Stopped)
        assertThat(toasts.messages).containsExactly(COULD_NOT_CONNECT, COULD_NOT_CONNECT)
    }
}
