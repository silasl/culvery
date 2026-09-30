package uk.co.siland.culvery

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Optional
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
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.DefaultAccessControl
import uk.co.siland.culvery.core.access.LockoutStore
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.SetupSessionGate
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.SetupWizard
import uk.co.siland.culvery.core.setup.setupStore
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/** Welcome's Use a sample household in the real wizard, whose PIN gate rises as soon as the sample's Admin exists. */
@RunWith(AndroidJUnit4::class)
class SampleWizardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var store: CalendarStore
    private lateinit var state: SetupState
    private val fake = FakeCalendarProvider()
    private val noToasts = object : Toaster {
        override fun show(message: String, icon: String) = Unit
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(householdDb)
        store = CalendarStore(calendarDb)
        state = SetupState(setupStore(scope) { File(folder.root, "setup.preferences_pb") }, household)
    }

    @After
    fun tearDown() {
        scope.cancel()
        householdDb.close()
        calendarDb.close()
    }

    @Test
    fun theWholeSampleHouseholdIsMadeAndSetupCompletes() {
        val pins = PinManager(household, PinHasher())
        val clock = WallClock { System.currentTimeMillis() }
        // Nobody answers the PIN pad, so a gate that rises stays up.
        val access = DefaultAccessControl(
            PermissionRegistry(setOf(CorePermissionSource())), pins, LockoutStore(ApplicationProvider.getApplicationContext()),
            PinPromptController(), clock, noToasts, scope,
        )
        val calendar = CalendarSetup(store, setOf(fake), { household.people.first() }, noToasts, clock)
        val sample = DebugSampleHousehold(household, pins, calendar, setOf(fake), state::markComplete)
        // The app's first read, before anyone exists.
        assertThat(runBlocking { state.setupComplete.first() }).isFalse()
        val gate = SetupSessionGate(household, access)
        val welcome = WelcomeStep(state, household, gate, Optional.of(sample))
        compose.setContent { CulveryTheme(dark = true) { SetupWizard(listOf(welcome), gate) } }

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("welcome_sample").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("welcome_sample").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            runBlocking { state.setupComplete.first() } || compose.onAllNodesWithTag("wizard_gate").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onAllNodesWithTag("wizard_gate").assertCountEquals(0)
        runBlocking {
            assertThat(household.people.first().map { it.name }).containsExactly("Alex", "Sam", "Mia")
            assertThat(household.location.first()).isEqualTo(SAMPLE_HOME)
            assertThat(store.connectionsNow().map { it.connection.id }).containsExactly(DEBUG_CONNECTION_ID)
            assertThat(store.master().first()?.source?.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
            assertThat(state.setupComplete.first()).isTrue()
        }
    }
}
