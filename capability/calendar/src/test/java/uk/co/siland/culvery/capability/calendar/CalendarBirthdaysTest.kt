package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class CalendarBirthdaysTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private lateinit var scope: CoroutineScope
    private val zone = ZoneId.of("Europe/London")
    private val sam = Person(PersonId("sam"), "Sam", 0xFFE07BA8)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF5B9BE0)
    private val conn = Connection("c1", "calendar.test", "Test", emptyMap())
    private val now = MutableStateFlow<LocalDateTime?>(march(12, 9, 0))
    private val household = MutableStateFlow(listOf(sam, alex))
    private var queries = 0

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
        scope = CoroutineScope(Job() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun march(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 3, day, hour, minute)

    private fun birthdays(clock: StateFlow<LocalDateTime?> = now) = CalendarBirthdays(
        now = clock,
        events = { from, to ->
            queries++
            store.eventsBetween(from, to)
        },
        people = household,
        zone = flowOf(zone),
        scope = scope,
    )

    private fun yearly(title: String, forPerson: String?) = RemoteEvent(
        remoteId = "b1", title = title,
        start = EventTime.AllDay(LocalDate.of(2026, 3, 12)), end = EventTime.AllDay(LocalDate.of(2026, 3, 13)),
        recurring = true, forPerson = forPerson, recurrenceRule = "RRULE:FREQ=YEARLY",
    )

    private fun load(vararg events: RemoteEvent, visible: Boolean = true) = runBlocking {
        val mapping = mapOf("s1" to SourceMapping(PersonId.FAMILY, visible = visible))
        store.addConnection(conn, listOf(CalendarSource("s1", "S1", writable = false)), mapping)
        sync("s1", events.toList())
    }

    private suspend fun sync(source: String, events: List<RemoteEvent>) {
        val range = DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), zone)
        store.applySync("c1", source, range, SyncResult(events, emptyList(), SyncCursor("k"), fullReplace = true), LocalDate.of(2026, 3, 12))
    }

    private suspend fun Flow<List<Person>>.untilIs(expected: List<Person>) = withTimeout(10_000) { first { it == expected } }

    @Test
    fun samsBirthdayIsSam() = runBlocking<Unit> {
        load(yearly("Sam’s birthday", "sam"))
        val today = birthdays().today.untilIs(listOf(sam))
        assertThat(today.single().name).isEqualTo("Sam")
        assertThat(today.single().color).isEqualTo(0xFFE07BA8)
    }

    @Test
    fun aBirthdayEndsAtMidnight() = runBlocking<Unit> {
        load(yearly("Sam’s birthday", "sam"))
        now.value = march(12, 23, 59)
        val birthdays = birthdays()
        birthdays.today.untilIs(listOf(sam))
        now.value = march(13, 0, 0)
        birthdays.today.untilIs(emptyList())
    }

    @Test
    fun nobodyBeforeTheClockIsRead() = runBlocking<Unit> {
        load(yearly("Sam’s birthday", "sam"))
        val clock = MutableStateFlow<LocalDateTime?>(null)
        val birthdays = birthdays(clock = clock)
        delay(300)
        assertThat(birthdays.today.value).isEmpty()
        assertThat(queries).isEqualTo(0)
        clock.value = march(12, 9, 0)
        birthdays.today.untilIs(listOf(sam))
        assertThat(queries).isEqualTo(1)
    }

    @Test
    fun theQueryFollowsTheDateNotTheMinute() = runBlocking<Unit> {
        load(yearly("Sam’s birthday", "sam"))
        val birthdays = birthdays()
        birthdays.today.untilIs(listOf(sam))
        now.value = march(12, 9, 1)
        household.value = listOf(sam, alex)
        delay(300)
        assertThat(queries).isEqualTo(1)
        now.value = march(13, 0, 0)
        birthdays.today.untilIs(emptyList())
        assertThat(queries).isEqualTo(2)
    }

    @Test
    fun aHiddenCalendarGreetsNobody() = runBlocking<Unit> {
        val mapping = mapOf("s1" to SourceMapping(PersonId.FAMILY, visible = false), "s2" to SourceMapping(PersonId.FAMILY, visible = true))
        store.addConnection(conn, listOf(CalendarSource("s1", "S1", writable = false), CalendarSource("s2", "S2", writable = false)), mapping)
        sync("s1", listOf(yearly("Sam’s birthday", "sam")))
        sync("s2", listOf(yearly("Alex’s birthday", "alex").copy(remoteId = "b2")))
        // Alex's, on the visible calendar, shows that the query has run; Sam's, on the hidden one, must not.
        birthdays().today.untilIs(listOf(alex))
    }

    @Test
    fun aQueuedCreateWaitsForItsSync() = runBlocking<Unit> {
        load(yearly("Alex’s birthday", "alex"))
        val draft = EventDraft(
            "Sam’s birthday", EventTime.AllDay(LocalDate.of(2026, 3, 12)), EventTime.AllDay(LocalDate.of(2026, 3, 13)),
            forPerson = "sam", createdBy = "sam",
        )
        store.enqueue(
            PendingChange(0, "c1", "s1", null, ChangeKind.CREATE, draft, attempts = 1, nextAttemptMillis = 0, createdMillis = 0, clientKey = "0123456789abcdef0123456789abcdef"),
        )
        // Alex's synced one shows that the query has run; Sam's, only queued, must not.
        birthdays().today.untilIs(listOf(alex))
    }

    @Test
    fun aStaleSyncStillGreets() = runBlocking<Unit> {
        load(yearly("Sam’s birthday", "sam"))
        store.markSynced("c1", LocalDateTime.of(2026, 2, 12, 9, 0).atZone(zone).toInstant().toEpochMilli())
        birthdays().today.untilIs(listOf(sam))
    }

    @Test
    fun aRenameShowsAtOnce() = runBlocking<Unit> {
        load(yearly("Sam’s birthday", "sam"))
        val birthdays = birthdays()
        birthdays.today.untilIs(listOf(sam))
        val mia = sam.copy(name = "Mia")
        household.value = listOf(mia, alex)
        birthdays.today.untilIs(listOf(mia))
    }
}
