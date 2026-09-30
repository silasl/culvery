package uk.co.siland.culvery.core.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
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
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.PersonPalette

internal fun householdDb(): HouseholdDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal class RecordingToaster : Toaster {
    // Written from Room's and the app scope's threads, read on the test's.
    val messages: MutableList<String> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String) {
        messages += message
    }
}

/**
 * The real access rules over [household], with PIN pads answered in order from [answer] (null taps Cancel); a pad with
 * no answer queued stays open. The session's timer runs on [sessionScope].
 */
internal class TestAccess(household: HouseholdRepository, clock: WallClock, sessionScope: CoroutineScope) {
    val prompt = PinPromptController()
    val pins = PinManager(household, PinHasher())
    val toasts = RecordingToaster()
    val requests: MutableList<PinRequest> = CopyOnWriteArrayList()
    private val answers = ArrayDeque<String?>()

    val control = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource())),
        pins = pins,
        lockout = LockoutStore(ApplicationProvider.getApplicationContext()),
        prompt = prompt,
        clock = clock,
        toaster = toasts,
        scope = sessionScope,
    )

    fun answer(vararg pins: String?) = synchronized(answers) { answers.addAll(pins) }

    fun listen(scope: CoroutineScope) {
        scope.launch {
            prompt.request.filterNotNull().collect { request ->
                requests += request
                val next = synchronized(answers) { if (answers.isEmpty()) return@collect else answers.removeFirst() }
                if (next == null) prompt.cancel() else prompt.submit(next)
            }
        }
    }
}

/** For runTest: PIN pads are answered on the test's background scope; the session timer runs outside it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.testAccess(household: HouseholdRepository): TestAccess {
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    backgroundScope.coroutineContext.job.invokeOnCompletion { sessionScope.cancel() }
    return TestAccess(household, WallClock { testScheduler.currentTime }, sessionScope).also { it.listen(backgroundScope) }
}

internal suspend fun TestAccess.addAdmin(name: String = "Alex", pin: String = "1234"): Person =
    pins.addPerson(name, PersonPalette.colors.first(), Role.ADMIN, pin)
