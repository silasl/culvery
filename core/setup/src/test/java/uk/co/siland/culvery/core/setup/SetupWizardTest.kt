package uk.co.siland.culvery.core.setup

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.steps.YouStep
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

/** A step whose flows the test drives; [onNextCalls] counts forward taps that reached it. */
private class FakeStep(
    override val id: String,
    override val order: Int,
    done: Boolean = false,
    shown: Boolean = true,
    ready: Boolean = true,
    override val skippable: Boolean = false,
    var goesOn: Boolean = true,
    var hold: CompletableDeferred<Unit>? = null,
) : SetupStep {
    val shownFlow = MutableStateFlow(shown)
    val doneFlow = MutableStateFlow(done)
    var onNextCalls = 0
    override val shown: Flow<Boolean> = shownFlow
    override val done: Flow<Boolean> = doneFlow
    override val canGoOn: Flow<Boolean> = MutableStateFlow(ready)

    override suspend fun onNext(): Boolean {
        onNextCalls++
        hold?.await()
        return goesOn
    }

    @Composable
    override fun Content(onNext: () -> Unit) {
        Text("Step $id")
    }
}

/**
 * The setup file held in memory, so the wizard's writes don't meet Windows' file locks (DataStore's rename can fail on a
 * write straight after another; SetupStateTest covers the real file). [restart] is a new store over the same contents,
 * as after a kill. [edits] counts the edits that have finished.
 */
private class MemoryStore(private val file: MutableStateFlow<Preferences> = MutableStateFlow(emptyPreferences())) : DataStore<Preferences> {
    val edits = MutableStateFlow(0)
    private val writing = Mutex()
    override val data: Flow<Preferences> = file

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = writing.withLock {
        transform(file.value).also {
            file.value = it
            edits.update { n -> n + 1 }
        }
    }

    fun restart() = MemoryStore(file)
}

// The gate reads Room, so each test waits for the wizard's first content before checking or tapping it.
@RunWith(AndroidJUnit4::class)
class SetupWizardTest {
    // After the compose rule has disposed the composition: no query can outlive the database.
    @get:Rule(order = 0) val closing = object : ExternalResource() {
        override fun after() {
            scope.cancel()
            db.close()
        }
    }

    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val overlay = RecordingOverlay()

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        access = TestAccess(household, WallClock { System.currentTimeMillis() }, scope).also { it.listen(scope) }
    }

    private fun show(vararg steps: SetupStep, gate: SetupSessionGate = SetupSessionGate(household, access.control)) {
        val progress = SetupState(MemoryStore(), household)
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) { CulveryTheme(dark = true) { SetupWizard(steps.toList(), gate, progress) } }
        }
    }

    /**
     * The wizard in a [key] the test can change: a new value is a new instance, with nothing saved but [progress].
     * No wizard shows while [progress] is null.
     */
    private fun showAgainAndAgain(steps: List<SetupStep>, progress: () -> SetupState?, instance: () -> Int) {
        val gate = SetupSessionGate(household, access.control)
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) { progress()?.let { key(instance()) { SetupWizard(steps, gate, it) } } }
            }
        }
    }

    /** Alex, the Admin, in the setup session, as the You step leaves them. */
    private fun alexSettingUp() {
        val alex = runBlocking { access.addAdmin() }
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
    }

    @Test
    fun itOpensAtTheFirstStepNotDoneWithADotForEachShownStep() {
        show(FakeStep("a", 0, done = true), FakeStep("b", 1), FakeStep("c", 2, shown = false), FakeStep("d", 3))
        compose.awaitText("Step b")
        compose.onAllNodesWithTag("wizard_dot").assertCountEquals(3)
    }

    @Test
    fun nextRunsTheStepThenMovesOn() {
        val a = FakeStep("a", 0)
        show(a, FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step b").assertExists()
        assertThat(a.onNextCalls).isEqualTo(1)
    }

    @Test
    fun aStepThatCannotGoOnDisablesNext() {
        show(FakeStep("a", 0, ready = false), FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").assertIsNotEnabled()
    }

    @Test
    fun aStepThatSaysNoStays() {
        show(FakeStep("a", 0, goesOn = false), FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step a").assertExists()
    }

    @Test
    fun skipForNowMovesOnWithoutTheStep() {
        val a = FakeStep("a", 0, skippable = true)
        show(a, FakeStep("b", 1))
        compose.awaitText("Skip for now")
        compose.onNodeWithText("Skip for now").performClick()
        compose.onNodeWithText("Step b").assertExists()
        assertThat(a.onNextCalls).isEqualTo(0)
    }

    @Test
    fun backGoesToTheShownStepBeforeAndTheFirstHasNone() {
        show(FakeStep("a", 0, done = true), FakeStep("b", 1, shown = false), FakeStep("c", 2))
        compose.awaitText("Step c")
        compose.onNodeWithTag("wizard_back").performClick()
        compose.onNodeWithText("Step a").assertExists()
        compose.onNodeWithTag("wizard_back").assertDoesNotExist()
    }

    @Test
    fun aStepThatAppearsIsReachedByNext() {
        val review = FakeStep("review", 1, shown = false)
        show(FakeStep("connect", 0), review, FakeStep("done", 2))
        compose.awaitText("Step connect")
        review.shownFlow.value = true
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step review").assertExists()
    }

    @Test
    fun aStepHiddenWhileShowingGivesWayToTheOneBefore() {
        // Disconnect on Review hides it: the wizard goes back to Connect, not on to Done.
        val review = FakeStep("review", 1)
        show(FakeStep("connect", 0, done = true), review, FakeStep("done", 2))
        compose.awaitText("Step review")
        review.shownFlow.value = false
        compose.awaitText("Step connect")
        compose.onNodeWithText("Step done").assertDoesNotExist()
        // Reviewing again (Connect reconnected) doesn't pull the wizard back without Next.
        review.shownFlow.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Step connect").assertExists()
        compose.onNodeWithText("Step review").assertDoesNotExist()
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithText("Step review").assertExists()
    }

    /**
     * The emulator walkthrough: Location and Household skipped, then a second Culvery (the home task's) opened the
     * wizard at Location again. Where the wizard got to is in the setup file, so a new instance opens at Done.
     */
    @Test
    fun aNewInstanceResumesPastTheStepsSkippedBefore() {
        val steps = listOf(
            FakeStep("welcome", 0, done = true),
            FakeStep("location", 1, skippable = true),
            FakeStep("household", 2, skippable = true),
            FakeStep("done", 3),
        )
        val store = MemoryStore()
        var progress by mutableStateOf<SetupState?>(SetupState(store, household))
        var instance by mutableIntStateOf(0)
        showAgainAndAgain(steps, { progress }, { instance })
        compose.awaitText("Step location")
        compose.onNodeWithTag("wizard_skip").performClick()
        compose.awaitText("Step household")
        compose.onNodeWithTag("wizard_skip").performClick()
        compose.awaitText("Step done")
        // The writes run on the main thread's coroutines, so wait while the compose rule keeps that thread going.
        compose.waitUntil(5_000) { store.edits.value == 2 }
        // A kill: the wizard goes, then a new store over the same contents.
        progress = null
        compose.waitForIdle()
        progress = SetupState(store.restart(), household)
        instance++
        compose.awaitText("Step done")
        compose.onNodeWithText("Step location").assertDoesNotExist()
        // Back still reaches the skipped steps.
        compose.onNodeWithTag("wizard_back").performClick()
        compose.onNodeWithText("Step household").assertExists()
    }

    /** Going Back doesn't move the furthest step passed back, so a new instance still resumes past it. */
    @Test
    fun backThenANewInstanceResumesAfterTheFurthestStepPassed() {
        val steps = listOf(FakeStep("a", 0, skippable = true), FakeStep("b", 1, skippable = true), FakeStep("c", 2))
        val store = MemoryStore()
        val progress = SetupState(store, household)
        var instance by mutableIntStateOf(0)
        showAgainAndAgain(steps, { progress }, { instance })
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_skip").performClick()
        compose.awaitText("Step b")
        compose.onNodeWithTag("wizard_skip").performClick()
        compose.awaitText("Step c")
        compose.onNodeWithTag("wizard_back").performClick()
        compose.awaitText("Step b")
        compose.onNodeWithTag("wizard_back").performClick()
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_skip").performClick()
        compose.awaitText("Step b")
        // a, b, then a again have all reached the store; the last changed nothing.
        compose.waitUntil(5_000) { store.edits.value == 3 }
        assertThat(runBlocking { progress.passedStep.first() }).isEqualTo("b")
        instance++
        compose.awaitText("Step c")
    }

    /** 4a's rule holds: a step passed before whose required input has gone can't be passed over on resume. */
    @Test
    fun aPassedStepThatCanNoLongerGoOnIsWhereItResumes() {
        val progress = SetupState(MemoryStore(), household).also { runBlocking { it.markPassed("you", listOf("welcome", "you", "done")) } }
        val steps = listOf(FakeStep("welcome", 0, done = true), FakeStep("you", 1, ready = false), FakeStep("done", 2))
        showAgainAndAgain(steps, { progress }, { 0 })
        compose.awaitText("Step you")
    }

    @Test
    fun aDoubleTapOnNextRunsTheStepOnce() {
        val hold = CompletableDeferred<Unit>()
        val a = FakeStep("a", 0, hold = hold)
        show(a, FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.onNodeWithTag("wizard_next").assertIsNotEnabled().performClick()
        hold.complete(Unit)
        compose.awaitText("Step b")
        assertThat(a.onNextCalls).isEqualTo(1)
    }

    @Test
    fun withNoAdminYetTheStepShowsAtOnce() {
        show(FakeStep("a", 0))
        compose.awaitText("Step a")
        assertThat(access.prompt.request.value).isNull()
    }

    @Test
    fun anAdminWithNobodySignedInIsAskedForTheirPinBeforeAnyStep() {
        runBlocking { access.addAdmin() }
        show(FakeStep("a", 0))
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        assertThat(access.prompt.request.value!!.reason).isEqualTo(PinReason.ContinueSetup)
        compose.onNodeWithText("Step a").assertDoesNotExist()
        // The pad is already open, so it is answered directly rather than through the listener's queue.
        access.prompt.submit("1234")
        compose.awaitText("Step a")
        // The setup session is back: even a fresh-PIN permission passes without a pad.
        val before = access.requests.size
        assertThat(runBlocking { access.control.authorise(CorePermissions.PEOPLE_MANAGE) }).isNotNull()
        assertThat(access.requests.size).isEqualTo(before)
    }

    @Test
    fun whenTheSetupSessionEndsTheWizardAsksForThePinAgain() {
        // The session's 10 idle minutes are Task 4's tests; here it ends the way they end it, by clearing the session.
        alexSettingUp()
        show(FakeStep("a", 0), FakeStep("b", 1))
        compose.awaitText("Step a")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.awaitText("Step b")
        access.control.lock()
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        assertThat(access.prompt.request.value!!.reason).isEqualTo(PinReason.ContinueSetup)
        access.prompt.submit("1234")
        // It carries on where it was.
        compose.awaitText("Step b")
    }

    @Test
    fun aRestoredWizardAsksForThePinAgain() {
        // No setup session: the wizard is let in by the gate's PIN pad, as after a kill.
        runBlocking { access.addAdmin() }
        val progress = SetupState(MemoryStore(), household)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) { SetupWizard(listOf(FakeStep("a", 0)), SetupSessionGate(household, access.control), progress) }
            }
        }
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        access.prompt.submit("1234")
        compose.awaitText("Step a")
        // A process death loses the in-memory session; nothing saved may let the restored wizard in without a PIN.
        access.control.lock()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        assertThat(access.prompt.request.value!!.reason).isEqualTo(PinReason.ContinueSetup)
        compose.onNodeWithText("Step a").assertDoesNotExist()
    }

    @Test
    fun aChildsPinDoesNotOpenTheGate() {
        runBlocking {
            access.addAdmin()
            access.pins.addPerson("Sam", PersonPalette.colors[1], Role.CHILD, "5678")
        }
        show(FakeStep("a", 0))
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        val first = access.prompt.request.value
        access.prompt.submit("5678")
        compose.waitUntil(5_000) { access.prompt.request.value.let { it !== first && it?.error != null } }
        assertThat(access.prompt.request.value!!.reason).isEqualTo(PinReason.ContinueSetup)
        compose.onNodeWithText("Step a").assertDoesNotExist()
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun aCancelledPinLeavesTheGateWithEnterPin() {
        runBlocking { access.addAdmin() }
        show(FakeStep("a", 0))
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        access.prompt.cancel()
        compose.awaitText("Enter your PIN to carry on setting up")
        compose.onNodeWithTag("wizard_enter_pin").assertExists()
        compose.onNodeWithText("Step a").assertDoesNotExist()
    }

    @Test
    fun theGateClosesAnySheetOpenWhenItComesUp() {
        alexSettingUp()
        show(FakeStep("a", 0))
        compose.awaitText("Step a")
        compose.runOnIdle { overlay.show { Text("Sam's sheet") } }
        access.control.lock()
        compose.waitUntil(5_000) { access.prompt.request.value != null }
        compose.waitForIdle()
        assertThat(overlay.content).isNull()
    }

    @Test
    fun theNewAdminCarriesOnInTheSetupSessionWithNoPinPad() {
        val gate = SetupSessionGate(household, access.control)
        val you = YouStep(household, access.pins, access.control, PeopleEditor(household, access.pins, access.control, access.toasts), gate)
        you.form.name = "Alex"
        you.form.pin = "1234"
        show(you, FakeStep("b", 1), gate = gate)
        compose.awaitTag("you_pin_set")
        compose.onNodeWithTag("wizard_next").performClick()
        compose.awaitText("Step b")
        compose.waitForIdle()
        assertThat(access.control.session.value).isNotNull()
        assertThat(access.requests).isEmpty()
        compose.onNodeWithTag("wizard_gate").assertDoesNotExist()
    }
}
