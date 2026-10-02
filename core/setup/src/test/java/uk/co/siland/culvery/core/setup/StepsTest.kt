package uk.co.siland.culvery.core.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.setup.pages.LocationPage
import uk.co.siland.culvery.core.setup.steps.DoneStep
import uk.co.siland.culvery.core.setup.steps.HouseholdStep
import uk.co.siland.culvery.core.setup.steps.LocationStep
import uk.co.siland.culvery.core.setup.steps.WelcomeStep
import uk.co.siland.culvery.core.setup.steps.YouStep
import uk.co.siland.culvery.core.ui.PersonPalette

/** A search nobody should reach: these tests save towns directly. */
private object NoSearch : LocationSearch {
    override suspend fun search(query: String): List<PlaceMatch> = error("not searched in these tests")
}

/** A setup store that runs [beforeWrite] ahead of every write, so a test sees what the app looked like at that moment. */
private class SpyStore(private val inner: DataStore<Preferences>, private val beforeWrite: () -> Unit) : DataStore<Preferences> {
    override val data = inner.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        beforeWrite()
        return inner.updateData(transform)
    }
}

// Robolectric for Room and DataStore's files.
@RunWith(AndroidJUnit4::class)
class StepsTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var states: SetupStates
    private val canterbury = PlaceMatch("Canterbury", "England", "United Kingdom", 51.27904, 1.07992, "Europe/London")

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        states = SetupStates(folder, household)
    }

    @After
    fun tearDown() {
        states.close()
        db.close()
    }

    @Test
    fun welcomeIsDoneOnceStartedAndStaysDoneAfterAKill() = runTest {
        val gate = SetupSessionGate(household, testAccess(household).control)
        val welcome = WelcomeStep(states.start(), household, gate, Optional.empty())
        assertThat(welcome.nextLabel).isEqualTo("Start")
        assertThat(welcome.canGoOn.first()).isTrue()
        assertThat(welcome.done.first()).isFalse()
        assertThat(welcome.onNext()).isTrue()
        assertThat(WelcomeStep(states.start(), household, gate, Optional.empty()).done.first()).isTrue()
    }

    @Test
    fun beforeAnyAdminAPlaceIsSavedWithoutAPin() = runTest {
        val access = testAccess(household)
        val step = LocationStep(household, NoSearch, access.control)
        assertThat(step.done.first()).isFalse()
        assertThat(step.saveHome(canterbury)).isTrue()
        assertThat(household.location.first()).isEqualTo(canterbury.toHome())
        assertThat(household.location.first()?.name).isEqualTo("Canterbury, England, United Kingdom")
        assertThat(access.requests).isEmpty()
        assertThat(step.done.first()).isTrue()
    }

    @Test
    fun onceAnAdminExistsSavingAPlaceAsksForTheirPin() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val step = LocationStep(household, NoSearch, access.control)
        access.answer(null)
        assertThat(step.saveHome(canterbury)).isFalse()
        assertThat(household.location.first()).isNull()
        access.answer("1234")
        assertThat(step.saveHome(canterbury)).isTrue()
        assertThat(access.requests).hasSize(2)
    }

    @Test
    fun theHomeLocationPageAlwaysTakesTheOpenSession() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val page = LocationPage(household, NoSearch, access.control)
        access.answer(null)
        assertThat(page.saveHome(canterbury)).isFalse()
        assertThat(household.location.first()).isNull()
        access.answer("1234")
        assertThat(page.saveHome(canterbury)).isTrue()
        assertThat(household.location.first()).isEqualTo(canterbury.toHome())
    }

    private fun youStep(access: TestAccess, gate: SetupSessionGate = SetupSessionGate(household, access.control)) =
        YouStep(household, access.pins, access.control, PeopleEditor(household, access.pins, access.control, access.toasts), gate)

    @Test
    fun youCanGoOnOnceThereIsANameAndAPin() = runTest {
        val you = youStep(testAccess(household))
        assertThat(you.canGoOn.first()).isFalse()
        you.form.name = "Alex"
        assertThat(you.canGoOn.first()).isFalse()
        you.form.pin = "1234"
        assertThat(you.canGoOn.first()).isTrue()
    }

    @Test
    fun nextMakesOneAdminWithTheirPinAndSignsThemInForSetup() = runTest {
        val access = testAccess(household)
        val you = youStep(access)
        you.form.name = "Alex"
        you.form.color = PersonPalette.colors[3]
        you.form.pin = "1234"
        assertThat(you.onNext()).isTrue()
        val admin = household.members.first().single()
        assertThat(listOf(admin.person.name, admin.person.color, admin.role, admin.hasPin))
            .containsExactly("Alex", PersonPalette.colors[3], Role.ADMIN, true).inOrder()
        assertThat(access.pins.identify("1234")?.person?.id).isEqualTo(admin.person.id)
        assertThat(access.control.authorise(CorePermissions.PEOPLE_MANAGE)).isNotNull()
        assertThat(access.requests).isEmpty()
        assertThat(you.done.first()).isTrue()
        // The PIN isn't held once the Admin has it.
        assertThat(you.form.pin).isNull()
    }

    @Test
    fun theGateStaysDownWhileTheFirstAdminIsMadeAndSignedIn() = runTest {
        val access = testAccess(household)
        val gate = SetupSessionGate(household, access.control)
        val you = youStep(access, gate)
        you.form.name = "Alex"
        you.form.pin = "1234"
        val seen = CopyOnWriteArrayList<Boolean>()
        val watch = CoroutineScope(Dispatchers.Unconfined).launch { gate.needsPin.collect { seen += it } }
        val next = async { you.onNext() }
        // Runs onNext until the Admin is stored, then holds it there, before the setup session, while Room tells the gate.
        while (storedPeople() == 0) {
            runCurrent()
            Thread.sleep(5)
        }
        Thread.sleep(500)
        assertThat(next.await()).isTrue()
        watch.cancel()
        assertThat(seen).doesNotContain(true)
        assertThat(access.control.session.value).isNotNull()
        assertThat(gate.needsPin.first()).isFalse()
    }

    private fun storedPeople(): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM person").use { c ->
        c.moveToFirst()
        c.getInt(0)
    }

    @Test
    fun aSecondNextAfterTheAdminExistsAddsNobody() = runTest {
        val you = youStep(testAccess(household))
        you.form.name = "Alex"
        you.form.pin = "1234"
        you.onNext()
        assertThat(you.onNext()).isTrue()
        assertThat(household.members.first()).hasSize(1)
    }

    @Test
    fun aKillAfterYouResumesPastIt() = runTest {
        val you = youStep(testAccess(household))
        you.form.name = "Alex"
        you.form.pin = "1234"
        you.onNext()
        // The app starts again: a new step over the same household.
        assertThat(youStep(testAccess(household)).done.first()).isTrue()
    }

    @Test
    fun nobodyCanBeCalledFamilyEvenFirst() = runTest {
        val you = youStep(testAccess(household))
        you.form.name = "Family"
        you.form.pin = "1234"
        assertThat(you.onNext()).isFalse()
        assertThat(you.form.message).isEqualTo("Someone is already called Family.")
        assertThat(household.members.first()).isEmpty()
    }

    @Test
    fun theHouseholdStepIsDoneOnceSomeoneElseLivesHere() = runTest {
        val access = testAccess(household)
        access.addAdmin()
        val step = HouseholdStep(PeopleEditor(household, access.pins, access.control, access.toasts))
        assertThat(step.skippable).isTrue()
        assertThat(step.done.first()).isFalse()
        access.pins.addPerson("Sam", PersonPalette.colors[1], Role.ADULT, null)
        assertThat(step.done.first()).isTrue()
    }

    @Test
    fun openCulveryEndsTheSetupSessionBeforeSetupIsComplete() = runTest {
        val access = testAccess(household)
        // A fresh install: its first read stores "not complete" before the wizard makes the Admin (ruling 2).
        val state = states.start()
        assertThat(state.setupComplete.first()).isFalse()
        val alex = access.addAdmin()
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
        val done = DoneStep(state, access.control, SetupSessionGate(household, access.control), FakeHomeApp())
        assertThat(done.nextLabel).isEqualTo("Open Culvery")
        assertThat(done.done.first()).isFalse()
        assertThat(done.onNext()).isTrue()
        assertThat(access.control.session.value).isNull()
        assertThat(state.setupComplete.first()).isTrue()
        access.answer(null)
        assertThat(access.control.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun withNobodySignedInOpenCulveryAsksForAnAdmin() = runTest {
        val access = testAccess(household)
        val state = states.start()
        assertThat(state.setupComplete.first()).isFalse()
        access.addAdmin()
        val done = DoneStep(state, access.control, SetupSessionGate(household, access.control), FakeHomeApp())
        access.answer(null)
        assertThat(done.onNext()).isFalse()
        assertThat(state.setupComplete.first()).isFalse()
        access.answer("1234")
        assertThat(done.onNext()).isTrue()
        assertThat(state.setupComplete.first()).isTrue()
    }

    private fun spyState(beforeWrite: () -> Unit): Pair<SetupState, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return SetupState(SpyStore(setupStore(scope) { File(folder.root, "spy.preferences_pb") }, beforeWrite), household) to scope
    }

    @Test
    fun setupIsMarkedCompleteOnlyOnceTheSetupSessionIsOver() = runTest {
        val access = testAccess(household)
        val sessionsAtWrites = CopyOnWriteArrayList<Identified?>()
        val (state, scope) = spyState { sessionsAtWrites += access.control.session.value }
        try {
            // A fresh install: its first read stores "not complete".
            assertThat(state.setupComplete.first()).isFalse()
            val alex = access.addAdmin()
            access.control.beginSetupSession(Identified(alex, Role.ADMIN))
            val done = DoneStep(state, access.control, SetupSessionGate(household, access.control), FakeHomeApp())
            assertThat(done.onNext()).isTrue()
            // Two writes: the first read's, then markComplete, which ran with nobody signed in (spec §9).
            assertThat(sessionsAtWrites).hasSize(2)
            assertThat(sessionsAtWrites.last()).isNull()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ifDoneCannotFinishTheGateComesBack() = runTest {
        val access = testAccess(household)
        val alex = access.addAdmin()
        access.control.beginSetupSession(Identified(alex, Role.ADMIN))
        val (state, scope) = spyState { throw IOException("disk full") }
        try {
            val gate = SetupSessionGate(household, access.control)
            val done = DoneStep(state, access.control, gate, FakeHomeApp())
            assertThat(runCatching { done.onNext() }.exceptionOrNull()).isInstanceOf(IOException::class.java)
            // Signed out, an Admin exists, and Done is no longer finishing: the PIN gate asks again.
            assertThat(gate.needsPin.first()).isTrue()
        } finally {
            scope.cancel()
        }
    }
}
