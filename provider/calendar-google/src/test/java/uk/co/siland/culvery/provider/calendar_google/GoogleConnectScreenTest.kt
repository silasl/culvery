package uk.co.siland.culvery.provider.calendar_google

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
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

@RunWith(AndroidJUnit4::class)
class GoogleConnectScreenTest {
    @get:Rule val compose = createComposeRule()

    private val google = FakeGoogleServer()
    private val authorizer = FakeAuthorizer()
    private val toasts = Toasts()
    private lateinit var provider: GoogleCalendarProvider

    @Before
    fun setUp() {
        provider = GoogleCalendarProvider(GoogleApi(google.start(), FakeTokenSource(), OkHttpClient()), authorizer, toasts)
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
        compose.setContent { provider.ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {}) }
        compose.waitUntil(5_000) { connected != null }
        assertThat(connected!!.config).containsExactly(CONFIG_ACCOUNT, "family@example.com")
    }

    /** Play services' screens, answering at once with [resultCode]. */
    private fun screensAnswering(resultCode: Int) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, resultCode, null)
            }
        }
    }

    private fun showWithScreens(resultCode: Int, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        authorizer.next = Authorization.NeedsUser(screens())
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides screensAnswering(resultCode)) {
                provider.ConnectScreen(existing = null, onConnected = onConnected, onCancel = onCancel)
            }
        }
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
        compose.setContent { provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ }) }
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun aPlayServicesFailureSaysSoAndCancels() {
        authorizer.failWith = authorizationFailure(CommonStatusCodes.DEVELOPER_ERROR)
        var cancelled = 0
        compose.setContent { provider.ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled++ }) }
        compose.waitUntil(5_000) { cancelled == 1 }
        assertThat(toasts.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }
}
