package uk.co.siland.househub.core.access

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.household.db.HouseholdDatabase
import uk.co.siland.househub.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class DefaultAccessControlTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prompt = PinPromptController()
    private val seen = mutableListOf<PinRequest>()

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

    private fun TestScope.access() = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource(), everyone)),
        pins = pins,
        lockout = LockoutStore(context),
        prompt = prompt,
        clock = WallClock { testScheduler.currentTime },
        scope = backgroundScope,
    )

    private suspend fun person(name: String, role: Role, pin: String): Person =
        household.addPerson(name, 0xFF4CB387, role).also { pins.setPin(it.id, pin) }

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
    fun sessionExpiresAfterSixtySecondsIdle() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(59_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(2_000); runCurrent()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).error).isNull()
    }

    @Test
    fun touchExtendsSessionButItStillExpires() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(50_000); runCurrent()
        access.touch()
        advanceTimeBy(50_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(11_000); runCurrent()
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
}
