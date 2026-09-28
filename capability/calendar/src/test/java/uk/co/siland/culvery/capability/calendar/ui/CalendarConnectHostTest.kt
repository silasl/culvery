package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.PROVIDER_TIMEOUT_MS
import uk.co.siland.culvery.capability.calendar.ScriptedProvider
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The connecting card over real access rules, setup and store; PIN pads are answered from a queue. */
@RunWith(AndroidJUnit4::class)
class CalendarConnectHostTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var access: TestAccess
    private lateinit var connections: CalendarConnections

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val overlay = RecordingOverlay()
    private val google = ScriptedProvider(
        "calendar.google",
        sourceList = listOf(CalendarSource("family@example.com", "Family", writable = true, primary = true)),
        features = setOf(Feature.READ, Feature.WRITE),
        displayName = "Google Calendar",
    )
    private val googleConnection = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        val clock = WallClock { System.currentTimeMillis() }
        access = testAccess(household, scope, clock, scope)
        val setup = CalendarSetup(store, setOf(google), { household.people.first() }, access.toasts, clock, EmptyCoroutineContext, PROVIDER_TIMEOUT_MS)
        connections = CalendarConnections(store, setup, access.control, setOf(google), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    /** Robolectric's main looper runs only when the test idles it, so each check lets the UI and its effects run first. */
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(5_000) {
        compose.waitForIdle()
        condition()
    }

    private fun show(existing: Connection?) {
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) { Box { overlay.content?.invoke() } }
            }
        }
        compose.runOnIdle { overlay.showConnect(ConnectRequest(google, existing), connections) }
    }

    @Test
    fun anAdminConnectsAndTheCalendarIsSetUpWithDefaults() {
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX)
        show(existing = null)
        waitFor { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Google Calendar connected")
        assertThat(overlay.dismissed).isEqualTo(1)
        runBlocking { assertThat(store.master().first()?.source?.id).isEqualTo("family@example.com") }
    }

    @Test
    fun anAdultCannotConnect() {
        google.connectsAs = googleConnection
        // Sam's PIN is refused in the pad, which asks again; then Cancel.
        access.answer(TestAccess.SAM, null)
        show(existing = null)
        waitFor { overlay.dismissed == 1 }
        runBlocking { assertThat(store.connectionsNow()).isEmpty() }
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun cancelClosesTheCardWithNothingSaid() {
        access.answer(TestAccess.ALEX)
        show(existing = null)
        waitFor { compose.onAllNodesWithText("Connecting to Google Calendar…").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("connecting_cancel").performClick()
        waitFor { overlay.dismissed == 1 }
        assertThat(access.toasts.messages).isEmpty()
        runBlocking { assertThat(store.connectionsNow()).isEmpty() }
    }

    @Test
    fun aReconnectRecordsTheSameConnectionHealthyAgain() {
        runBlocking {
            store.addConnection(googleConnection, google.sourceList, emptyMap())
            store.setHealth("g1", ConnectionHealth.NeedsSignIn, 0L)
        }
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX)
        show(existing = googleConnection)
        waitFor { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Google Calendar reconnected")
        runBlocking { assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Ok) }
    }

    @Test
    fun aFailedSetupSaysSoAndStoresNothing() {
        google.connectsAs = googleConnection
        google.sourcesFailWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        show(existing = null)
        waitFor { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Couldn't connect to Google Calendar — try again")
        assertThat(overlay.dismissed).isEqualTo(1)
        runBlocking { assertThat(store.connectionsNow()).isEmpty() }
    }
}
