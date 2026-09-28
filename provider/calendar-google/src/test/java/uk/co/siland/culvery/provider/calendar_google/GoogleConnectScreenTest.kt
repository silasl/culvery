package uk.co.siland.culvery.provider.calendar_google

import androidx.compose.ui.test.junit4.createComposeRule
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
