package uk.co.siland.culvery.core.access

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class DefaultAccessControlTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prompt = PinPromptController()
    private val seen = mutableListOf<PinRequest>()
    private val toasts = RecordingToaster()

    private val everyone = object : PermissionSource {
        override val permissions = listOf(PermissionDef("test.any", "Do a thing", Role.entries.toSet()))
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(db)
        pins = PinManager(household, PinHasher())
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.access(timers: CoroutineScope = backgroundScope, nowMillis: () -> Long = { testScheduler.currentTime }) = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource(), everyone)),
        pins = pins,
        lockout = LockoutStore(context),
        prompt = prompt,
        clock = WallClock { nowMillis() },
        toaster = toasts,
        scope = timers,
    )

    private var nextColour = 0xFF101010L

    private suspend fun person(name: String, role: Role, pin: String): Person =
        pins.addPerson(name, nextColour++, role, pin)

    /** Answers successive PIN pad requests in order; null = tap Cancel. */
    private fun TestScope.answerPins(vararg answers: String?) {
        val queue = ArrayDeque(answers.toList())
        backgroundScope.launch {
            prompt.request.filterNotNull().collect { req ->
                seen += req
                if (queue.isEmpty()) return@collect
                val pin = queue.removeFirst()
                if (pin == null) prompt.cancel() else prompt.submit(pin)
            }
        }
    }

    /** Starts [permission] and returns the first PIN pad request it shows, then cancels it. */
    private suspend fun TestScope.firstPromptFor(access: DefaultAccessControl, permission: String): PinRequest {
        val job = launch { access.authorise(permission) }
        val request = prompt.request.filterNotNull().first()
        job.cancel()
        return request
    }

    @Test
    fun correctPinAuthorisesAndStartsSession() = runTest {
        val alex = person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        val result = access.authorise(CorePermissions.SETTINGS_MANAGE)
        assertThat(result?.person).isEqualTo(alex)
        assertThat(result?.granted).containsExactly(CorePermissions.SETTINGS_MANAGE)
        assertThat(access.session.value?.person).isEqualTo(alex)
        assertThat(prompt.request.value).isNull()
    }

    @Test
    fun activeSessionSkipsThePrompt() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        val again = withTimeout(1_000) { access.authorise("test.any") }
        assertThat(again).isNotNull()
        assertThat(seen).hasSize(1)
    }

    @Test
    fun freshPinPermissionPromptsEvenDuringSession() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        val request = firstPromptFor(access, CorePermissions.KIOSK_EXIT)
        assertThat(request.label).isEqualTo("Exit kiosk mode")
    }

    @Test
    fun sessionLastsTwoMinutesAfterTheLastAuthorisedAction() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(119_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(2_000); runCurrent()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).error).isNull()
    }

    @Test
    fun onlyAnAuthorisedActionExtendsTheSession() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise("test.any")
        advanceTimeBy(100_000); runCurrent()
        // Passes on the session shortcut, without a pad, and restarts the two minutes.
        assertThat(access.authorise("test.any")).isNotNull()
        assertThat(seen).hasSize(1)
        advanceTimeBy(119_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(2_000); runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun lockEndsSessionImmediately() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        access.lock()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun wrongPinShowsErrorThenAcceptsCorrectPin() = runTest {
        val alex = person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("0000", "1234")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)?.person).isEqualTo(alex)
        assertThat(seen.map { it.error }).containsExactly(null, PinError.WrongPin).inOrder()
    }

    @Test
    fun aChildIsRefusedConnecting() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876", null)
        assertThat(access.authorise(CorePermissions.CONNECTIONS_MANAGE)).isNull()
        assertThat(seen.last().error).isEqualTo(PinError.NotAllowed("Mia"))
        assertThat(access.session.value).isNull()
    }

    @Test
    fun personWithoutPermissionIsToldAndNotSignedIn() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876", null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(seen.last().error).isEqualTo(PinError.NotAllowed("Mia"))
        assertThat(access.session.value).isNull()
    }

    @Test
    fun sessionPersonWithoutPermissionIsPromptedAgain() = runTest {
        person("Mia", Role.CHILD, "9876")
        val alex = person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("9876", "1234")
        access.authorise("test.any")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)?.person).isEqualTo(alex)
    }

    @Test
    fun grantedContainsOnlyHeldPermissions() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE, "test.any")?.granted).containsExactly("test.any")
    }

    @Test
    fun cancelReturnsNullAndHidesPad() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins(null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(prompt.request.value).isNull()
    }

    @Test
    fun correctPinIsRefusedDuringLockout() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "0000", "1234", null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(seen[5].lockedUntilMillis).isEqualTo(30_000L)
        assertThat(access.session.value).isNull()

        advanceTimeBy(30_001)
        answerPins("1234")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNotNull()
    }

    @Test
    fun notAllowedPinDoesNotResetLockout() = runTest {
        person("Alex", Role.ADMIN, "1234")
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "9876", "0000", null)
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        assertThat(seen.last().lockedUntilMillis).isNotNull()
    }

    @Test
    fun notAllowedPinDoesNotCountTowardLockout() = runTest {
        person("Alex", Role.ADMIN, "1234")
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "9876", null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(seen.last().lockedUntilMillis).isNull()
    }

    @Test
    fun authorisedPinResetsLockoutCounter() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        access.lock()
        answerPins("0000", null)
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        assertThat(seen.last().lockedUntilMillis).isNull()
    }

    @Test
    fun unknownPermissionThrows() = runTest {
        val access = access()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { access.authorise("nope") } }
    }

    @Test
    fun reasonReachesThePinPad() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        val job = launch { access.authorise("test.any", reason = PinReason.Delete) }
        assertThat(prompt.request.filterNotNull().first().reason).isEqualTo(PinReason.Delete)
        job.cancel()
    }

    @Test
    fun theReasonIsGenericByDefault() = runTest {
        val access = access()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).reason).isEqualTo(PinReason.Generic)
    }

    @Test
    fun aRefusalOnTheSessionShortcutToastsThenLocks() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234", "1234")
        access.authorise("test.any")
        val refused = access.authorise("test.any", allow = { _, _ -> false }, refusal = Refusal.Toast { "$it may not" })
        assertThat(refused).isNull()
        assertThat(seen).hasSize(1)
        assertThat(toasts.messages).containsExactly("Alex may not")
        assertThat(access.session.value).isNull()
        // The next tap brings up the PIN pad, so someone else can take over.
        assertThat(access.authorise("test.any")).isNotNull()
        assertThat(seen).hasSize(2)
    }

    @Test
    fun allowIsAppliedAfterAPinAndARefusalToastClosesThePad() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876")
        val result = access.authorise(
            "test.any",
            allow = { who, _ -> who.person.name != "Mia" },
            refusal = Refusal.Toast { "$it can only change events they created." },
        )
        assertThat(result).isNull()
        assertThat(toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(prompt.request.value).isNull()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun aRefusalInThePadCarriesItsMessageAndAsksAgain() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876", null)
        assertThat(access.authorise("test.any", allow = { _, _ -> false })).isNull()
        assertThat(seen.last().error).isEqualTo(PinError.NotAllowed("Mia", "Mia can't do that"))
        assertThat(toasts.messages).isEmpty()
    }

    @Test
    fun allowSeesOnlyTheGrantedPermissions() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876")
        var seenGrants: Set<String>? = null
        access.authorise(CorePermissions.SETTINGS_MANAGE, "test.any", allow = { _, g -> seenGrants = g; true })
        assertThat(seenGrants).containsExactly("test.any")
    }

    @Test
    fun aRefusedPinDoesNotResetTheLockoutCounter() = runTest {
        person("Alex", Role.ADMIN, "1234")
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "9876", "0000", null)
        access.authorise("test.any", allow = { who, _ -> who.person.name != "Mia" })
        assertThat(seen.last().lockedUntilMillis).isNotNull()
    }

    @Test
    fun pinReasonTextNamesTheActionOrThePermission() {
        assertThat(pinReasonText(PinReason.Delete, "Change any event"))
            .isEqualTo("Enter your PIN to delete this event. It also records who made the change.")
        assertThat(pinReasonText(PinReason.Assign, "Assign events"))
            .isEqualTo("Enter your PIN to assign this event. It also records who made the change.")
        assertThat(pinReasonText(PinReason.Generic, "Change settings")).isEqualTo("Enter your PIN to change settings.")
    }

    private suspend fun alex(): Identified = Identified(person("Alex", Role.ADMIN, "1234"), Role.ADMIN)

    @Test
    fun aSetupSessionPassesEveryPermissionWithoutAPin() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        for (permission in listOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.PEOPLE_MANAGE, CorePermissions.KIOSK_EXIT, CorePermissions.CONNECTIONS_MANAGE)) {
            assertThat(withTimeout(1_000) { access.authorise(permission) }).isNotNull()
        }
        assertThat(prompt.request.value).isNull()
    }

    /** L3: connecting can lead out of the app through Google's screens, so it always takes a fresh Admin PIN. */
    @Test
    fun connectingAsksForAFreshPinEvenDuringASession() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        val request = firstPromptFor(access, CorePermissions.CONNECTIONS_MANAGE)
        assertThat(request.label).isEqualTo("Connect calendars")
    }

    @Test
    fun aSetupSessionOutlastsTheUsualTwoMinutes() = runTest {
        val access = access()
        val admin = alex()
        access.beginSetupSession(admin)
        advanceTimeBy(SESSION_TIMEOUT_MS * 4)
        runCurrent()
        assertThat(access.session.value).isEqualTo(admin)
    }

    @Test
    fun aSetupSessionEndsAfterTenMinutesWithoutATouch() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        advanceTimeBy(SETUP_IDLE_MS + 1)
        runCurrent()
        assertThat(access.session.value).isNull()
        // Gone for good: the next fresh-PIN permission asks.
        assertThat(firstPromptFor(access, CorePermissions.PEOPLE_MANAGE).label).isEqualTo("Manage people")
    }

    @Test
    fun aTouchRestartsTheSetupSessionsTenMinutes() = runTest {
        val access = access()
        val admin = alex()
        access.beginSetupSession(admin)
        advanceTimeBy(SETUP_IDLE_MS - 60_000)
        access.touch()
        advanceTimeBy(SETUP_IDLE_MS - 60_000)
        runCurrent()
        assertThat(access.session.value).isEqualTo(admin)
        advanceTimeBy(60_001)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun endingTheSetupSessionStartsTheUsualTwoMinutes() = runTest {
        val access = access()
        val admin = alex()
        access.beginSetupSession(admin)
        access.endSetupSession()
        assertThat(access.session.value).isEqualTo(admin)
        assertThat(firstPromptFor(access, CorePermissions.PEOPLE_MANAGE).label).isEqualTo("Manage people")
        advanceTimeBy(SESSION_TIMEOUT_MS + 1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun lockEndsTheSetupSession() = runTest {
        val access = access()
        access.beginSetupSession(alex())
        access.lock()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.PEOPLE_MANAGE).label).isEqualTo("Manage people")
    }

    @Test
    fun aPinEnteredDuringSetupEndsTheSetupSession() = runTest {
        // Timers on their own scheduler: waiting on Room's real threads would otherwise let runTest skip ahead ten minutes.
        val timers = TestScope()
        val access = access(timers.backgroundScope)
        access.beginSetupSession(alex())
        person("Mia", Role.CHILD, "9876")
        answerPins("9876")
        // Refused for Alex by the check, so the pad asks; Mia's PIN starts an ordinary session.
        val mia = access.authorise("test.any", allow = { who, _ -> who.person.name == "Mia" })
        assertThat(mia?.person?.name).isEqualTo("Mia")
        access.touch()
        timers.advanceTimeBy(SESSION_TIMEOUT_MS + 1)
        timers.runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun aTouchRestartsTheTwoMinutes() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(100_000)
        access.touch()
        advanceTimeBy(100_000)
        runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(20_001)
        runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun aTouchWithNobodySignedInSignsNobodyIn() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        access.touch()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).label).isEqualTo("Change settings")
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(SESSION_TIMEOUT_MS + 1)
        runCurrent()
        assertThat(access.session.value).isNull()
    }
}
