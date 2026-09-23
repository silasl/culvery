package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

@RunWith(AndroidJUnit4::class)
class CalendarStoreTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val zone = ZoneId.of("Europe/London")
    private val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), zone)
    private val conn = Connection("c1", "calendar.test", "Test", mapOf("url" to "https://example.com/a?b=1&c=\"d\""))

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(zone).toInstant()

    private fun timed(id: String, title: String, day: Int, hour: Int, minutes: Long = 60) =
        RemoteEvent(id, title, EventTime.Timed(at(day, hour)), EventTime.Timed(at(day, hour).plusSeconds(minutes * 60)), recurring = false)

    private fun allDay(id: String, title: String, fromDay: Int, toDayExclusive: Int) = RemoteEvent(
        id, title,
        EventTime.AllDay(LocalDate.of(2026, 9, fromDay)),
        EventTime.AllDay(LocalDate.of(2026, 9, toDayExclusive)),
        recurring = false,
    )

    private fun full(vararg events: RemoteEvent) = SyncResult(events.toList(), emptyList(), SyncCursor("k1"), fullReplace = true)

    private fun millis(day: Int) = LocalDate.of(2026, 9, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private suspend fun connect(vararg sourceIds: String, mapping: Map<String, SourceMapping> = emptyMap()) =
        store.addConnection(conn, sourceIds.map { CalendarSource(it, it.uppercase(), writable = false) }, mapping)

    private suspend fun titlesBetween(fromDay: Int, toDayExclusive: Int) =
        store.eventsBetween(millis(fromDay), millis(toDayExclusive)).first().map { it.title }

    @Test
    fun newConnectionIsOkNeverSyncedAndKeepsItsConfig() = runTest {
        connect("s1")
        val stored = store.connections().first().single()
        assertThat(stored.connection).isEqualTo(conn)
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.lastSyncMillis).isNull()
        assertThat(store.connectionIds().first()).containsExactly("c1")
    }

    @Test
    fun healthRoundTripsIncludingTheErrorMessage() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.Error("quota exceeded"))
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Error("quota exceeded"))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.NeedsSignIn)
        store.setHealth("c1", ConnectionHealth.Unreachable)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Unreachable)
    }

    @Test
    fun markSyncedSetsOkAndTheTime() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn)
        store.markSynced("c1", 1234L)
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.lastSyncMillis).isEqualTo(1234L)
    }

    @Test
    fun fullReplaceDropsEventsMissingFromTheNewSet() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "Old", 23, 9), timed("b", "Kept", 23, 10)))
        store.applySync("c1", "s1", window, full(timed("b", "Kept", 23, 10), timed("c", "New", 23, 11)))
        assertThat(titlesBetween(23, 24)).containsExactly("Kept", "New").inOrder()
    }

    @Test
    fun incrementalSyncUpsertsAndRemoves() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "Gone", 23, 9), timed("b", "Before", 23, 10)))
        store.applySync(
            "c1", "s1", window,
            SyncResult(listOf(timed("b", "After", 23, 10), timed("c", "Added", 23, 11)), listOf("a"), SyncCursor("k2"), fullReplace = false),
        )
        assertThat(titlesBetween(23, 24)).containsExactly("After", "Added").inOrder()
    }

    @Test
    fun eventsBetweenReturnsOverlapsInStartOrder() = runTest {
        connect("s1")
        store.applySync(
            "c1", "s1", window,
            full(
                timed("late", "Late", 23, 19),
                timed("early", "Early", 23, 7),
                timed("tomorrow", "Tomorrow", 24, 10),
                allDay("half", "Half term", 21, 25),
            ),
        )
        assertThat(titlesBetween(23, 24)).containsExactly("Half term", "Early", "Late").inOrder()
    }

    @Test
    fun allDayEventIsNotReturnedOnItsExclusiveEndDay() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(allDay("bins", "Bin day", 23, 24)))
        assertThat(titlesBetween(23, 24)).containsExactly("Bin day")
        assertThat(titlesBetween(24, 25)).isEmpty()
    }

    @Test
    fun hiddenSourcesAreExcluded() = runTest {
        connect("s1", "s2", mapping = mapOf("s2" to SourceMapping(PersonId.FAMILY, visible = false)))
        store.applySync("c1", "s1", window, full(timed("a", "Shown", 23, 9)))
        store.applySync("c1", "s2", window, full(timed("b", "Hidden", 23, 10)))
        assertThat(titlesBetween(23, 24)).containsExactly("Shown")
        assertThat(store.visibleSourcesFor("c1").map { it.source.id }).containsExactly("s1")
    }

    @Test
    fun unmappedSourceDefaultsToFamilyAndVisible() = runTest {
        connect("s1", mapping = emptyMap())
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        assertThat(store.visibleSourcesFor("c1").single().mapping).isEqualTo(SourceMapping.Default)
        assertThat(store.eventsBetween(millis(23), millis(24)).first().single().sourcePerson).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun mappedSourceCarriesItsPerson() = runTest {
        connect("s1", mapping = mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        assertThat(store.eventsBetween(millis(23), millis(24)).first().single().sourcePerson).isEqualTo(PersonId("alex"))
    }

    @Test
    fun removalsBeyondSqlitesVariableLimitAreApplied() = runTest {
        connect("s1")
        val many = (1..1_200).map { timed("e$it", "Event $it", 23, 9) }
        store.applySync("c1", "s1", window, full(*many.toTypedArray(), timed("keep", "Keep", 23, 10)))
        store.applySync(
            "c1", "s1", window,
            SyncResult(emptyList(), many.map { it.remoteId }, SyncCursor("k2"), fullReplace = false),
        )
        assertThat(titlesBetween(23, 24)).containsExactly("Keep")
    }

    @Test
    fun eventTimesRoundTrip() = runTest {
        connect("s1")
        val timedEvent = timed("a", "Walk", 23, 9, minutes = 45)
        val allDayEvent = allDay("b", "Holiday", 23, 26)
        store.applySync("c1", "s1", window, full(timedEvent, allDayEvent))
        val stored = store.eventsBetween(millis(23), millis(24)).first().associateBy { it.remoteId }
        assertThat(stored.getValue("a").start).isEqualTo(timedEvent.start)
        assertThat(stored.getValue("a").end).isEqualTo(timedEvent.end)
        assertThat(stored.getValue("b").start).isEqualTo(allDayEvent.start)
        assertThat(stored.getValue("b").end).isEqualTo(allDayEvent.end)
    }

    @Test
    fun cursorIsStoredPerSource() = runTest {
        connect("s1", "s2")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        assertThat(store.cursor("c1", "s1", window)).isEqualTo(SyncCursor("k1"))
        assertThat(store.cursor("c1", "s2", window)).isNull()
    }

    @Test
    fun cursorIsDroppedWhenTheWindowMoves() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        val nextDay = DateRange(window.start.plusDays(1), window.endExclusive.plusDays(1), zone)
        assertThat(store.cursor("c1", "s1", nextDay)).isNull()
    }

    @Test
    fun cursorIsDroppedWhenTheZoneChanges() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        val sameDatesElsewhere = DateRange(window.start, window.endExclusive, ZoneId.of("Pacific/Auckland"))
        assertThat(store.cursor("c1", "s1", sameDatesElsewhere)).isNull()
        assertThat(store.cursor("c1", "s1", window)).isEqualTo(SyncCursor("k1"))
    }
}
