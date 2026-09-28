package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CHANGES_SAVED
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarPermissionSource
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.CalendarSync
import uk.co.siland.culvery.capability.calendar.CalendarSyncLoop
import uk.co.siland.culvery.capability.calendar.CalendarWriteLock
import uk.co.siland.culvery.capability.calendar.EVENT_ADDED
import uk.co.siland.culvery.capability.calendar.EVENT_DELETED
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.OUTBOX_BACKOFF_MS
import uk.co.siland.culvery.capability.calendar.newClientKey
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.DefaultAccessControl
import uk.co.siland.culvery.core.access.LockoutStore
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/**
 * 2b-2 design §5 on the sample calendar, end to end: an event added while the calendar can't be reached shows at
 * once, can be changed and deleted before it syncs, and once the calendar is back nothing of it remains. The real
 * store, access rules, editor, sync engine, repository and fake provider; Alex answers every PIN pad.
 */
@RunWith(AndroidJUnit4::class)
class SampleAddTest {
    private class Toasts : Toaster {
        val messages = mutableListOf<String>()

        override fun show(message: String, icon: String) {
            messages += message
        }
    }

    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private val fake = FakeCalendarProvider()
    private val toasts = Toasts()
    private val london = ZoneId.of("Europe/London")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        householdDb.close()
        calendarDb.close()
    }

    @Test
    fun anEventAddedOfflineCanBeChangedAndDeletedBeforeItSyncsAndNothingRemains() = runTest {
        val household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val pins = PinManager(household, PinHasher())
        val store = CalendarStore(calendarDb)
        val zone = HouseholdZone(household)
        seedDebugData(household, pins, CalendarSetup(store, setOf(fake)), setOf(fake))
        var now = System.currentTimeMillis()
        val clock = WallClock { now }
        val prompt = PinPromptController()
        backgroundScope.launch { prompt.request.filterNotNull().collect { prompt.submit("1234") } }
        val access = DefaultAccessControl(
            PermissionRegistry(setOf(CorePermissionSource(), CalendarPermissionSource())),
            pins,
            LockoutStore(ApplicationProvider.getApplicationContext()),
            prompt,
            clock,
            toasts,
            backgroundScope,
        )
        val lock = CalendarWriteLock()
        val sync = CalendarSync(store, setOf(fake), setOf(fake), toasts, zone, clock, lock)
        val editor = CalendarEditor(store, setOf(fake), access, toasts, zone, clock, backgroundScope, CalendarSyncLoop(sync, store, clock, backgroundScope), lock)
        val repo = CalendarRepository(store, household, zone, setOf(fake), setOf(fake))
        sync.syncAll()

        val today = LocalDate.now(london)
        val start = today.atTime(20, 0).atZone(london).toInstant()
        fun draft(title: String) = EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), PersonId.FAMILY.value, null)
        val titles = listOf("Parents evening", "Parents' evening at school")

        fake.setOffline(true)
        assertThat(editor.create(draft("Parents evening"), newClientKey())).isEqualTo(EditResult.Queued)
        val added = repo.day(today).first().single { it.title == "Parents evening" }
        assertThat(added.syncing).isTrue()

        assertThat(editor.update(added.ref, draft("Parents' evening at school"))).isEqualTo(EditResult.Queued)
        assertThat(repo.event(added.ref, today).first()?.event?.title).isEqualTo("Parents' evening at school")

        assertThat(editor.delete(added.ref)).isEqualTo(EditResult.Queued)
        assertThat(repo.day(today).first().map { it.title }).containsNoneIn(titles)

        fake.setOffline(false)
        now += OUTBOX_BACKOFF_MS.first()
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(repo.day(today).first().map { it.title }).containsNoneIn(titles)
        assertThat(toasts.messages).containsExactly(EVENT_ADDED, CHANGES_SAVED, EVENT_DELETED).inOrder()
    }
}
