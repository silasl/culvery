package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class CalendarMultiDayEventsTest {
    private lateinit var db: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var multiDay: CalendarMultiDayEvents
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 10, 28)
    private val conn = Connection("c1", "calendar.test", "Test", emptyMap())

    @Before
    fun setUp() {
        db = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(db)
        val household = HouseholdRepository(householdDb)
        runBlocking { household.setLocation(HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London")) }
        multiDay = CalendarMultiDayEvents(store, HouseholdZone(household))
    }

    @After
    fun tearDown() {
        db.close()
        householdDb.close()
    }

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun allDay(id: String, title: String, first: LocalDate, endExclusive: LocalDate) =
        RemoteEvent(id, title, EventTime.AllDay(first), EventTime.AllDay(endExclusive), recurring = false)

    private fun timed(id: String, title: String, at: Instant) =
        RemoteEvent(id, title, EventTime.Timed(at), EventTime.Timed(at.plusSeconds(3600)), recurring = false)

    private fun load(vararg events: RemoteEvent, visible: Boolean = true) = runBlocking {
        val mapping = mapOf("s1" to SourceMapping(PersonId.FAMILY, visible = visible))
        store.addConnection(conn, listOf(CalendarSource("s1", "S1", writable = false)), mapping)
        val range = DateRange(date(10, 1), date(12, 31), zone)
        store.applySync("c1", "s1", range, SyncResult(events.toList(), emptyList(), SyncCursor("k"), fullReplace = true), today)
    }

    @Test
    fun theExclusiveEndBecomesTheLastDay() = runBlocking<Unit> {
        load(allDay("a", "Cornwall", date(10, 24), date(11, 2)))
        val event = multiDay.around(today).single()
        assertThat(event.first).isEqualTo(date(10, 24))
        assertThat(event.last).isEqualTo(date(11, 1))
        assertThat(event.title).isEqualTo("Cornwall")
    }

    @Test
    fun oneDayAndTimedEventsAreLeftOut() = runBlocking<Unit> {
        load(
            allDay("a", "Dentist", date(10, 28), date(10, 29)),
            timed("b", "Supper", date(10, 28).atTime(18, 0).atZone(zone).toInstant()),
            allDay("c", "Half term", date(10, 28), date(10, 31)),
        )
        assertThat(multiDay.around(today).map { it.title }).containsExactly("Half term")
    }

    @Test
    fun hiddenCalendarsAreLeftOut() = runBlocking<Unit> {
        load(allDay("a", "Cornwall", date(10, 24), date(11, 2)), visible = false)
        assertThat(multiDay.around(today)).isEmpty()
    }

    @Test
    fun onlyEventsAroundTodayAndTomorrow() = runBlocking<Unit> {
        load(
            allDay("a", "Ended", date(10, 24), date(10, 27)),
            allDay("b", "Later", date(10, 30), date(11, 3)),
            allDay("c", "Tomorrow", date(10, 29), date(11, 2)),
        )
        assertThat(multiDay.around(today).map { it.title }).containsExactly("Tomorrow")
    }

    @Test
    fun theTitleIsNotInToString() = runBlocking<Unit> {
        load(allDay("a", "Cornwall", date(10, 24), date(11, 2)))
        assertThat(multiDay.around(today).single().toString()).doesNotContain("Cornwall")
    }
}
