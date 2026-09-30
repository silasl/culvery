package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.CalendarSync
import uk.co.siland.culvery.capability.calendar.CalendarWriteLock
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.SourceRefresher
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

/**
 * A delete queued while offline hides the event; when the sample calendar later refuses it, the event comes back
 * and a toast says why. The real store, sync engine, repository and fake provider, end to end.
 */
@RunWith(AndroidJUnit4::class)
class SampleRollbackTest {
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
    fun aRefusedOfflineDeleteComesBackWithAToast() = runTest {
        val household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val store = CalendarStore(calendarDb)
        val zone = HouseholdZone(household)
        DebugSampleHousehold(
            household,
            PinManager(household, PinHasher()),
            CalendarSetup(store, setOf(fake), { household.people.first() }, toasts, WallClock { System.currentTimeMillis() }),
            setOf(fake),
        ) {}.create()
        val sync = CalendarSync(
            store, setOf(fake), setOf(fake), toasts, zone, WallClock { System.currentTimeMillis() }, CalendarWriteLock(),
            SourceRefresher(store, household, WallClock { System.currentTimeMillis() }, toasts),
        )
        val repo = CalendarRepository(store, household, zone, setOf(fake), setOf(fake))
        sync.syncAll()
        val today = LocalDate.now(london)
        val boiler = repo.day(today).first().single { it.title == "Boiler service" }

        // What the editor queues when the provider can't be reached (CalendarEditorTest covers that step).
        val now = System.currentTimeMillis()
        store.enqueue(
            PendingChange(
                0, boiler.ref.connectionId, boiler.ref.sourceId, boiler.ref.remoteId, ChangeKind.DELETE,
                draft = null, attempts = 1, nextAttemptMillis = now, createdMillis = now,
            ),
        )
        assertThat(repo.day(today).first().map { it.title }).doesNotContain("Boiler service")

        fake.rejectNextWrite("Boiler service is locked")
        sync.syncAll()
        assertThat(repo.day(today).first().map { it.title }).contains("Boiler service")
        assertThat(toasts.messages).containsExactly("Couldn't save to Sample calendar (debug) — Boiler service is locked")
    }
}
