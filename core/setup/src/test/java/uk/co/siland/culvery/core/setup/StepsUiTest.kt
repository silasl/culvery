package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.steps.DoneStep
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.setup.steps.YouStep
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

@RunWith(AndroidJUnit4::class)
class StepsUiTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var states: SetupStates
    private lateinit var access: TestAccess
    private val overlay = RecordingOverlay()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var samples = 0

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        states = SetupStates(folder, household)
        access = TestAccess(household, WallClock { System.currentTimeMillis() }, scope).also { it.listen(scope) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        states.close()
        db.close()
    }

    private fun welcome(sample: Boolean) {
        val step = WelcomeStep(runBlocking { states.start() }, household, if (sample) Optional.of(SampleHousehold { samples++ }) else Optional.empty())
        compose.setContent { CulveryTheme(dark = true) { Column { step.Content(onNext = {}) } } }
    }

    @Test
    fun aDebugBuildOffersTheSampleHouseholdToAnEmptyHousehold() {
        welcome(sample = true)
        // Offered only once the household is read as empty.
        compose.awaitText("Use a sample household")
        compose.onNodeWithText("Use a sample household").performClick()
        compose.waitUntil(5_000) { samples == 1 }
    }

    @Test
    fun aReleaseBuildNeverOffersIt() {
        welcome(sample = false)
        compose.awaitText("Welcome to Culvery")
        compose.onNodeWithTag("welcome_sample").assertDoesNotExist()
    }

    @Test
    fun itIsNotOfferedOnceSomeoneLivesHere() {
        runBlocking { access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, null) }
        welcome(sample = true)
        compose.awaitText("Welcome to Culvery")
        compose.onNodeWithTag("welcome_sample").assertDoesNotExist()
    }

    @Test
    fun setYourPinChoosesItTwiceOverThePad() {
        val you = YouStep(household, access.pins, access.control, PeopleEditor(household, access.pins, access.control, access.toasts))
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box {
                        Column { you.Content(onNext = {}) }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.awaitTag("you_set_pin")
        compose.onNodeWithTag("you_set_pin").performClick()
        repeat(2) { "1357".forEach { d -> compose.onNodeWithTag("pin_key_$d").performClick() } }
        compose.waitUntil(5_000) { overlay.content == null }
        compose.onNodeWithTag("you_pin_set").assertExists()
        assertThat(you.form.pin).isEqualTo("1357")
    }

    @Test
    fun openCulveryNeverShowsTheGate() {
        val alex = runBlocking { access.addAdmin() }
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
        val state = runBlocking { states.start() }
        val gate = SetupSessionGate(household, access.control)
        val done = DoneStep(state, access.control, gate)
        compose.setContent { CulveryTheme(dark = true) { SetupWizard(listOf(done), gate) } }
        compose.awaitText("Culvery is ready")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.waitUntil(5_000) { runBlocking { state.setupComplete.first() } }
        compose.waitForIdle()
        // Signed out, and no PIN pad or gate on the way out.
        assertThat(access.control.session.value).isNull()
        assertThat(access.requests).isEmpty()
        compose.onNodeWithTag("wizard_gate").assertDoesNotExist()
    }
}
