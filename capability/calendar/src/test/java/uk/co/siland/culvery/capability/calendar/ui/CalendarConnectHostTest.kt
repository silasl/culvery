package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
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
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.PROVIDER_TIMEOUT_MS
import uk.co.siland.culvery.capability.calendar.ScriptedProvider
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.stubEditor
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/** settings.manage's name on the PIN pad. */
private const val SETTINGS_LABEL = "Change settings"

/** The connecting card over real access rules, setup and store; PIN pads are answered from a queue. */
@RunWith(AndroidJUnit4::class)
class CalendarConnectHostTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var access: TestAccess
    private lateinit var connections: CalendarConnections
    private lateinit var household: HouseholdRepository

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
        household = HouseholdRepository(householdDb)
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
        assertThat(access.requests.map { it.label }).containsExactly(SETTINGS_LABEL)
        assertThat(google.connectScreenShown).isEqualTo(1)
    }

    @Test
    fun onceConnectedTheWizardsConnectStepSaysSo() {
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX)
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay, LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = true) {
                    Box {
                        ConnectStepHost(connections)
                        overlay.content?.invoke()
                    }
                }
            }
        }
        waitFor { compose.onAllNodesWithText("Connect Google Calendar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Connect Google Calendar").performClick()
        waitFor { compose.onAllNodesWithText("Google Calendar · family@example.com").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Connected").assertExists()
        compose.onAllNodesWithText("Connect a calendar").assertCountEquals(0)
        compose.onAllNodesWithText("Open settings").assertCountEquals(0)
    }

    @Test
    fun theWizardsConnectStepNeverFlashesOpenSettings() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay, LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = true) { ConnectStepHost(connections) }
            }
        }
        // The first frame, before the store has answered.
        compose.onAllNodesWithText("Open settings").assertCountEquals(0)
        compose.mainClock.autoAdvance = true
        waitFor { compose.onAllNodesWithText("Connect Google Calendar").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Open settings").assertCountEquals(0)
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
        // One settings.manage check: its pad, then the same pad again saying Sam may not.
        assertThat(access.requests.map { it.label }).containsExactly(SETTINGS_LABEL, SETTINGS_LABEL)
        assertThat(access.requests.last().error).isEqualTo(PinError.NotAllowed("Sam"))
        assertThat(google.connectScreenShown).isEqualTo(0)
        compose.onAllNodesWithText("Connecting to Google Calendar…").assertCountEquals(0)
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

    @Test
    fun theWeeksReconnectChipReconnectsTheConnectionThatNeedsIt() {
        val other = Connection("a0", "calendar.other", "School", emptyMap())
        runBlocking {
            store.addConnection(other, emptyList(), emptyMap())
            store.addConnection(googleConnection, google.sourceList, emptyMap())
            store.setHealth("g1", ConnectionHealth.NeedsSignIn, 0L)
        }
        google.connectsAs = googleConnection
        access.answer(TestAccess.ALEX)
        val zone = HouseholdZone(household)
        val repo = CalendarRepository(store, household, zone, emptySet(), emptySet())
        val editor = stubEditor(store, zone)
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay, LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = true) {
                    Box {
                        val connector = rememberConnector(connections)
                        WeekViewHost(repo, editor, LocalDate.of(2026, 9, 23), nowMillis = 0L, onReconnect = connector::reconnect)
                        overlay.content?.invoke()
                    }
                }
            }
        }
        waitFor { compose.onAllNodesWithText("Google needs reconnecting").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Google needs reconnecting").performClick()
        waitFor { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Google Calendar reconnected")
        runBlocking {
            assertThat(store.connectionsNow().associate { it.connection.id to it.health })
                .containsExactly("a0", ConnectionHealth.Ok, "g1", ConnectionHealth.Ok)
        }
    }
}
