package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room; the follower runs on its own scope, so the waits are in real time.
@RunWith(AndroidJUnit4::class)
class HouseholdFollowerTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var people: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var store: CalendarStore
    private val syncs = AtomicInteger()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        calendar = calendarDb()
        people = householdDb()
        household = HouseholdRepository(people)
        store = CalendarStore(calendar)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        people.close()
    }

    private fun follow(): HouseholdFollower = HouseholdFollower(
        household,
        HouseholdZone(household),
        store,
        CalendarSetup(store, emptySet(), { emptyList() }, RecordingToaster(), WallClock { 0L }, EmptyCoroutineContext) { syncs.incrementAndGet() },
        scope,
    ).also { it.start() }

    private suspend fun waitFor(condition: suspend () -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!condition()) delay(10) } }

    @Test
    fun aCalendarMappedToSomeoneRemovedShowsAsFamily() = runBlocking {
        val mia = household.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        store.addConnection(
            Connection("c1", "calendar.test", "Test", emptyMap()),
            listOf(CalendarSource("swim", "Mia's swimming", writable = false)),
            mapOf("swim" to SourceMapping(mia.id, visible = true)),
        )
        follow()
        household.removePerson(mia.id)
        waitFor { store.source("c1", "swim")!!.mapping.person == PersonId.FAMILY }
    }

    @Test
    fun aChangeOfTimeZoneAsksForASyncAndTheSameZoneDoesNot() = runBlocking {
        household.setLocation(HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London"))
        val follower = follow()
        // The zone it starts with never asks for a sync.
        withTimeout(5_000) { follower.zoneRead.await() }
        household.setLocation(HomeLocation("Paris, Île-de-France, France", 48.8534, 2.3488, "Europe/Paris"))
        waitFor { syncs.get() == 1 }
        // Lyon shares Paris's zone, so only Berlin asks.
        household.setLocation(HomeLocation("Lyon, Auvergne-Rhône-Alpes, France", 45.7485, 4.8467, "Europe/Paris"))
        household.setLocation(HomeLocation("Berlin, Land Berlin, Germany", 52.5244, 13.4105, "Europe/Berlin"))
        waitFor { syncs.get() >= 2 }
        assertThat(syncs.get()).isEqualTo(2)
    }
}
