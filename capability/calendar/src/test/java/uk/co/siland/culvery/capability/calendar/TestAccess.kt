package uk.co.siland.culvery.capability.calendar

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.DefaultAccessControl
import uk.co.siland.culvery.core.access.LockoutStore
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinRequest
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.WallClock

/**
 * The real access rules over an in-memory household, with PIN pads answered from a queue.
 *
 * The session's 2-minute timer runs on its own scope, outside virtual time. Room does its work on its own
 * threads, and while a test waits for Room, runTest would otherwise skip virtual time ahead to the next delay,
 * which would end the session mid-test. Tests end a session with `control.lock()`, which is what the timer does.
 */
internal class TestAccess(
    val control: DefaultAccessControl,
    val prompt: PinPromptController,
    val toasts: RecordingToaster,
    val alex: Person,
    val sam: Person,
    val mia: Person,
) {
    val requests = mutableListOf<PinRequest>()
    private val answers = ArrayDeque<String?>()

    /** Answers the next PIN pads in order; null taps Cancel. A pad with no answer queued stays open. */
    fun answer(vararg pins: String?) {
        answers.addAll(pins)
    }

    fun listen(scope: CoroutineScope) {
        scope.launch {
            prompt.request.filterNotNull().collect { request ->
                requests += request
                if (answers.isEmpty()) return@collect
                val pin = answers.removeFirst()
                if (pin == null) prompt.cancel() else prompt.submit(pin)
            }
        }
    }

    companion object {
        const val ALEX = "1111"
        const val SAM = "2222"
        const val MIA = "3333"
    }
}

/** Alex, Sam and Mia with their PINs, and real access control. PIN pads are answered from [listenIn]. */
internal suspend fun testAccess(
    household: HouseholdRepository,
    listenIn: CoroutineScope,
    clock: WallClock,
    sessionScope: CoroutineScope,
): TestAccess {
    val pins = PinManager(household, PinHasher())
    suspend fun person(name: String, color: Long, role: Role, pin: String) =
        household.addPerson(name, color, role).also { pins.setPin(it.id, pin) }
    val alex = person("Alex", 0xFF4CB387, Role.ADMIN, TestAccess.ALEX)
    val sam = person("Sam", 0xFF5B9BE0, Role.ADULT, TestAccess.SAM)
    val mia = person("Mia", 0xFFE07BA8, Role.CHILD, TestAccess.MIA)
    val prompt = PinPromptController()
    val toasts = RecordingToaster()
    val control = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource(), CalendarPermissionSource())),
        pins = pins,
        lockout = LockoutStore(ApplicationProvider.getApplicationContext()),
        prompt = prompt,
        clock = clock,
        toaster = toasts,
        scope = sessionScope,
    )
    return TestAccess(control, prompt, toasts, alex, sam, mia).also { it.listen(listenIn) }
}

/** For runTest: PIN pads are answered on the test's background scope; the session timer runs outside it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.testAccess(household: HouseholdRepository): TestAccess {
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    backgroundScope.coroutineContext.job.invokeOnCompletion { sessionScope.cancel() }
    return testAccess(household, backgroundScope, WallClock { testScheduler.currentTime }, sessionScope)
}
