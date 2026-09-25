package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.ConnectionEntity
import uk.co.siland.culvery.capability.calendar.db.OutboxEntity
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

    private val alexId = "alex-id"

    private fun eventDraft(title: String, day: Int, hour: Int, forPerson: String? = alexId, createdBy: String? = alexId) =
        EventDraft(title, EventTime.Timed(at(day, hour)), EventTime.Timed(at(day, hour + 1)), forPerson, createdBy)

    private fun change(
        kind: ChangeKind,
        remoteId: String? = "e1",
        draft: EventDraft? = eventDraft("Swim", 23, 9),
        next: Long = 0L,
        attempts: Int = 0,
    ) = PendingChange(0, "c1", "s1", remoteId, kind, draft, attempts, next, createdMillis = 100L)

    @Test
    fun newSourcesAreNotTheMaster() = runTest {
        connect("s1", "s2")
        assertThat(store.sources().first().map { it.isMaster }).containsExactly(false, false)
        assertThat(store.master().first()).isNull()
    }

    @Test
    fun setMasterMarksOneSourceAsTheWritableMasterAndClearsTheOthers() = runTest {
        connect("s1", "s2")
        store.setMaster("c1", "s1")
        store.setMaster("c1", "s2")
        val master = store.master().first()!!
        assertThat(master.source.id).isEqualTo("s2")
        assertThat(master.source.writable).isTrue()
        assertThat(master.isMaster).isTrue()
        assertThat(store.sources().first().filter { it.isMaster }.map { it.source.id }).containsExactly("s2")
        assertThat(store.source("c1", "s1")!!.isMaster).isFalse()
    }

    @Test
    fun setMasterForAnUnknownSourceChangesNothing() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.setMaster("c1", "nope") } }
        assertThat(store.master().first()!!.source.id).isEqualTo("s1")
    }

    @Test
    fun outboxRoundTripsEveryKindAndDraftShape() = runTest {
        connect("s1")
        val allDay = EventDraft("Half term", EventTime.AllDay(LocalDate.of(2026, 10, 1)), EventTime.AllDay(LocalDate.of(2026, 10, 4)), null, null)
        val create = change(ChangeKind.CREATE, remoteId = null, draft = allDay)
        val update = change(ChangeKind.UPDATE, draft = eventDraft("Swim", 23, 9, forPerson = "family"))
        val delete = change(ChangeKind.DELETE, draft = null)
        val assign = change(ChangeKind.ASSIGN, draft = eventDraft("Swim", 23, 9, forPerson = "sam-id"))
        val ids = listOf(store.enqueue(create), store.enqueue(update), store.enqueue(delete), store.enqueue(assign))
        assertThat(store.pendingNow()).containsExactly(
            create.copy(id = ids[0]),
            update.copy(id = ids[1]),
            delete.copy(id = ids[2]),
            assign.copy(id = ids[3]),
        ).inOrder()
        assertThat(store.pending().first().map { it.ref }).containsExactly(
            null,
            EventRef("c1", "s1", "e1"),
            EventRef("c1", "s1", "e1"),
            EventRef("c1", "s1", "e1"),
        ).inOrder()
    }

    @Test
    fun aQueuedCreateKeepsItsClientKeyAndItsRefUsesIt() = runTest {
        connect("s1")
        val key = "0123456789abcdef0123456789abcdef"
        val create = change(ChangeKind.CREATE, remoteId = null).copy(clientKey = key)
        val id = store.enqueue(create)
        val read = store.pendingNow().single()
        assertThat(read).isEqualTo(create.copy(id = id))
        assertThat(read.ref).isEqualTo(EventRef("c1", "s1", key))
    }

    @Test
    fun onlyACreateTakesItsRefFromItsClientKey() {
        assertThat(change(ChangeKind.UPDATE).copy(clientKey = "stray").ref).isEqualTo(EventRef("c1", "s1", "e1"))
        assertThat(change(ChangeKind.CREATE, remoteId = null).ref).isNull()
    }

    @Test
    fun droppingACreateTakesEveryChangeQueuedForItsEvent() = runTest {
        connect("s1")
        val key = "0123456789abcdef0123456789abcdef"
        store.enqueue(change(ChangeKind.CREATE, remoteId = null).copy(clientKey = key))
        store.enqueue(change(ChangeKind.UPDATE, remoteId = key))
        store.enqueue(change(ChangeKind.DELETE, remoteId = key, draft = null))
        val other = store.enqueue(change(ChangeKind.UPDATE, remoteId = "e1"))
        assertThat(store.dropCreate(EventRef("c1", "s1", key))).isEqualTo(3)
        assertThat(store.pendingNow().map { it.id }).containsExactly(other)
    }

    @Test
    fun nextAttemptIsTheEarliestQueuedTime() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null, next = 5_000))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "b", draft = null, next = 1_000))
        assertThat(store.nextAttemptMillis()).isEqualTo(1_000L)
    }

    @Test
    fun anUnreadableQueuedRowIsDroppedAndTheOthersStillRead() = runTest {
        connect("s1")
        val dao = db.calendarDao()
        dao.insertOutbox(OutboxEntity(connectionId = "c1", sourceId = "s1", remoteId = "x", kind = "BOGUS", draftJson = null, attempts = 0, nextAttemptMillis = 0, createdMillis = 0))
        dao.insertOutbox(OutboxEntity(connectionId = "c1", sourceId = "s1", remoteId = "y", kind = "UPDATE", draftJson = "{", attempts = 0, nextAttemptMillis = 0, createdMillis = 0))
        val good = store.enqueue(change(ChangeKind.DELETE, draft = null))
        assertThat(store.pending().first().map { it.id }).containsExactly(good)
        assertThat(store.pendingNow().map { it.id }).containsExactly(good)
        assertThat(dao.outboxNow().map { it.id }).containsExactly(good)
    }

    @Test
    fun rescheduleAndDropChange() = runTest {
        connect("s1")
        val id = store.enqueue(change(ChangeKind.DELETE, draft = null))
        store.reschedule(id, attempts = 3, nextAttemptMillis = 9_000)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(3 to 9_000L)
        store.dropChange(id)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.nextAttemptMillis()).isNull()
    }

    @Test
    fun fullReplaceSyncLeavesTheOutboxAlone() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9)))
        val id = store.enqueue(change(ChangeKind.DELETE, draft = null))
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9), timed("e2", "Walk", 23, 10)))
        store.applySync("c1", "s1", window, full())
        assertThat(store.pendingNow().map { it.id }).containsExactly(id)
    }

    @Test
    fun eventByRefFollowsTheMirror() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9)))
        val ref = EventRef("c1", "s1", "e1")
        assertThat(store.event(ref).first()!!.title).isEqualTo("Swim")
        assertThat(store.eventNow(ref)!!.ref).isEqualTo(ref)
        assertThat(store.eventNow(EventRef("c1", "s1", "nope"))).isNull()
    }

    @Test
    fun applyAcceptedUpsertsTheEventAndCompletesTheChange() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9)))
        val id = store.enqueue(change(ChangeKind.UPDATE))
        store.applyAccepted("c1", "s1", timed("e1", "Swim", 23, 9).copy(forPerson = "sam-id"), zone, completing = id)
        assertThat(store.eventNow(EventRef("c1", "s1", "e1"))!!.forPerson).isEqualTo("sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun applyDeletedRemovesTheEventAndCompletesTheChange() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("e1", "Swim", 23, 9), timed("e2", "Walk", 23, 10)))
        val id = store.enqueue(change(ChangeKind.DELETE, draft = null))
        store.applyDeleted(EventRef("c1", "s1", "e1"), completing = id)
        assertThat(titlesBetween(23, 24)).containsExactly("Walk")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun applyWithoutCompletingLeavesTheQueue() = runTest {
        connect("s1")
        val id = store.enqueue(change(ChangeKind.DELETE, remoteId = "other", draft = null))
        store.applyAccepted("c1", "s1", timed("e1", "Swim", 23, 9), zone)
        assertThat(store.pendingNow().map { it.id }).containsExactly(id)
    }

    @Test
    fun unknownHealthCodeIsAnError() = runTest {
        db.calendarDao().insertConnection(ConnectionEntity("c9", "calendar.test", "Odd", "{}", "BOGUS", null, null))
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Error("Unknown health code BOGUS"))
    }

    @Test
    fun okHealthCodeIsOk() = runTest {
        connect("s1")
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun listKeysDifferWhenSlashesMoveBetweenIds() {
        assertThat(EventRef("a/b", "c", "d").listKey).isNotEqualTo(EventRef("a", "b/c", "d").listKey)
        assertThat(EventRef("a", "b", "c/d").listKey).isNotEqualTo(EventRef("a", "b/c", "d").listKey)
        assertThat(EventRef("ab", "c", "d").listKey).isNotEqualTo(EventRef("a", "bc", "d").listKey)
    }
}
