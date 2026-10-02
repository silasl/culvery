package uk.co.siland.culvery.provider.calendar_google

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalShellNavigator

@RunWith(AndroidJUnit4::class)
class GoogleConnectScreenTest {
    @get:Rule val compose = createComposeRule()

    private val google = FakeGoogleServer()
    private val authorizer = FakeAuthorizer()
    private val toasts = Toasts()
    private lateinit var provider: GoogleCalendarProvider

    /** What happened, in order: the navigator's "leave pinning" and the registry's "launch". */
    private val calls = mutableListOf<String>()

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(calls)) { content() }
    }

    @Before
    fun setUp() {
        provider = testProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), authorizer, toasts)
        google.addCalendar("family@example.com", "Family", primary = true)
    }

    @After
    fun tearDown() {
        google.shutdown()
        assertLogsHoldNoPersonalData()
    }

    @Test
    fun anAccountAlreadyGrantedConnectsWithNoScreens() {
        var connected: Connection? = null
        screen { provider.ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {}) }
        compose.waitUntil(5_000) { connected != null }
        assertThat(connected!!.config).containsExactly(CONFIG_ACCOUNT, "family@example.com")
    }

    /** Play services' screens, answering at once with [resultCode]. */
    private fun screensAnswering(resultCode: Int) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                calls += "launch"
                dispatchResult(requestCode, resultCode, null)
            }
        }
    }

    private fun showWithScreens(resultCode: Int, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        authorizer.next = Authorization.NeedsUser(screens())
        screen {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides screensAnswering(resultCode)) {
                provider.ConnectScreen(existing = null, onConnected = onConnected, onCancel = onCancel)
            }
        }
    }

    /** D5: Google's screens can't open over a pinned app, so pinning is left first. */
    @Test
    fun theChooserOpensOnlyAfterLeavingPinning() {
        var connected: Connection? = null
        showWithScreens(Activity.RESULT_OK, onConnected = { connected = it }, onCancel = {})
        compose.waitUntil(5_000) { connected != null }
        assertThat(calls).containsExactly("leave pinning", "launch", "return to pinning").inOrder()
    }

    /** The result arrives, so the kiosk is restored, whatever the answer (the normal path then pins on resume). */
    @Test
    fun aCancelledResultReturnsToPinning() {
        var cancelled = 0
        showWithScreens(Activity.RESULT_CANCELED, onConnected = {}, onCancel = { cancelled++ })
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(calls).containsExactly("leave pinning", "launch", "return to pinning").inOrder()
    }

    /** androidx turns a failed IntentSender launch into a posted RESULT_CANCELED; Culvery never paused, so it restores. */
    @Test
    fun aLaunchThatNeverOpensRestoresTheKioskAndCancels() {
        authorizer.next = Authorization.NeedsUser(screens())
        var cancelled = 0
        screen {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides unlaunchable { dispatch -> dispatch(Activity.RESULT_CANCELED) }) {
                provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ })
            }
        }
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(calls).containsExactly("leave pinning", "return to pinning").inOrder()
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun aLaunchThatThrowsRestoresTheKioskAndCancels() {
        authorizer.next = Authorization.NeedsUser(screens())
        var cancelled = 0
        screen {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides unlaunchable { error("no screen") }) {
                provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ })
            }
        }
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(calls).containsExactly("leave pinning", "return to pinning").inOrder()
    }

    /** A registry whose launch opens nothing: [behaviour] may throw, or report a cancelled result as androidx does. */
    private fun unlaunchable(behaviour: (dispatch: (Int) -> Unit) -> Unit) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                behaviour { code -> dispatchResult(requestCode, code, null) }
            }
        }
    }

    @Test
    fun anAccountAlreadyGrantedNeverLeavesPinning() {
        var connected: Connection? = null
        screen { provider.ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {}) }
        compose.waitUntil(5_000) { connected != null }
        assertThat(calls).isEmpty()
    }

    @Test
    fun backingOutOfTheScreensCancelsWithoutAskingPlayServicesForAnAnswer() {
        var cancelled = 0
        showWithScreens(Activity.RESULT_CANCELED, onConnected = {}, onCancel = { cancelled++ })
        compose.waitUntil(5_000) { cancelled == 1 }
        // With no answer in the intent, Play services would report an internal error, and that would toast.
        assertThat(authorizer.readFromScreens).isEqualTo(0)
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun theScreensGrantingConnects() {
        var connected: Connection? = null
        showWithScreens(Activity.RESULT_OK, onConnected = { connected = it }, onCancel = {})
        compose.waitUntil(5_000) { connected != null }
        assertThat(authorizer.readFromScreens).isEqualTo(1)
        assertThat(connected!!.config).containsExactly(CONFIG_ACCOUNT, "family@example.com")
    }

    @Test
    fun backingOutCancelsWithNothingSaid() {
        authorizer.failWith = authorizationFailure(CommonStatusCodes.CANCELED)
        var cancelled = 0
        screen { provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ }) }
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun aPlayServicesFailureSaysSoAndCancels() {
        authorizer.failWith = authorizationFailure(CommonStatusCodes.DEVELOPER_ERROR)
        var cancelled = 0
        screen { provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ }) }
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(toasts.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }
}
