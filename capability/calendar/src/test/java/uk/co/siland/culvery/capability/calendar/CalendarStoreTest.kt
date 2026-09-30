package uk.co.siland.culvery.capability.calendar

import androidx.room.useReaderConnection
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
        store.setHealth("c1", ConnectionHealth.Error("quota exceeded"), 0L)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Error("quota exceeded"))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 0L)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.NeedsSignIn)
        store.setHealth("c1", ConnectionHealth.Unreachable, 0L)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Unreachable)
    }

    @Test
    fun markSyncedSetsOkAndTheTime() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 0L)
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
    fun removalsBeyondSqlitesVariableLimitAreAppliedWithNoStatementOverIt() = runTest {
        val counting = CountingDao(db.calendarDao())
        val store = CalendarStore(db, counting)
        store.addConnection(conn, listOf(CalendarSource("s1", "S1", writable = false)), emptyMap())
        val many = (1..1_200).map { timed("e$it", "Event $it", 23, 9) }
        store.applySync("c1", "s1", window, full(*many.toTypedArray(), timed("keep", "Keep", 23, 10)))
        store.applySync(
            "c1", "s1", window,
            SyncResult(emptyList(), many.map { it.remoteId }, SyncCursor("k2"), fullReplace = false),
        )
        assertThat(store.eventsBetween(millis(23), millis(24)).first().map { it.title }).containsExactly("Keep")
        assertThat(counting.mostBound).isAtMost(999)
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

    /** Event rows for [sourceId], counted directly: eventsBetween joins the source table, so it can't see an orphan. */
    private suspend fun eventRows(sourceId: String): Long = db.useReaderConnection { connection ->
        connection.usePrepared("SELECT COUNT(*) FROM event WHERE connectionId = 'c1' AND sourceId = ?") { statement ->
            statement.bindText(1, sourceId)
            statement.step()
            statement.getLong(0)
        }
    }

    private suspend fun rowsFor(sourceId: String): Triple<Long, SyncCursor?, Int> = Triple(
        eventRows(sourceId),
        store.cursor("c1", sourceId, window),
        store.pendingNow().count { it.sourceId == sourceId },
    )

    private fun listed(id: String, writable: Boolean = false, shown: Boolean = true, primary: Boolean = false) =
        CalendarSource(id, id.uppercase(), writable, shown, primary)

    private fun mapping(person: String) = { _: CalendarSource -> SourceMapping(PersonId(person), visible = true) }

    @Test
    fun aConnectionAndEverythingItHoldsIsRemovedTogether() = runTest {
        connect("s1", "s2")
        store.setMaster("c1", "s1")
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null))
        store.removeConnection("c1")
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.sources().first()).isEmpty()
        assertThat(eventRows("s1")).isEqualTo(0L)
        assertThat(store.cursor("c1", "s1", window)).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.master().first()).isNull()
    }

    @Test
    fun refreshingAddsNewSourcesWithTheirMappingAndKeepsExistingMappings() = runTest {
        connect("s1", mapping = mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 5_000L, mapping("mia"))
        assertThat(store.sources().first().associate { it.source.id to it.mapping.person })
            .containsExactly("s1", PersonId("alex"), "s2", PersonId("mia"))
    }

    @Test
    fun refreshingFollowsTheTicksButKeepsThePrimaryVisible() = runTest {
        connect("s1", "s2")
        store.refreshSources("c1", listOf(listed("s1", shown = false), listed("s2", shown = false, primary = true)), 5_000L, mapping("mia"))
        assertThat(store.sources().first().associate { it.source.id to it.mapping.visible }).containsExactly("s1", false, "s2", true)
        store.refreshSources("c1", listOf(listed("s1", shown = true), listed("s2", primary = true)), 6_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun refreshingUpdatesNamesAndWritability() = runTest {
        connect("s1")
        store.refreshSources("c1", listOf(CalendarSource("s1", "Swimming club", writable = true)), 5_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.source.let { it.name to it.writable }).isEqualTo("Swimming club" to true)
    }

    @Test
    fun refreshingRemovesAGoneSourceWithItsEventsCursorAndQueue() = runTest {
        connect("s1", "s2")
        store.applySync("c1", "s2", window, full(timed("a", "Walk", 23, 9)))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null).copy(sourceId = "s2"))
        val cleared = store.refreshSources("c1", listOf(listed("s1")), 5_000L, mapping("mia"))
        assertThat(cleared).isFalse()
        assertThat(store.sources().first().map { it.source.id }).containsExactly("s1")
        assertThat(rowsFor("s2")).isEqualTo(Triple(0L, null, 0))
    }

    @Test
    fun refreshingWithoutTheMasterClearsItAndRemovesItsRows() = runTest {
        connect("s1", "s2")
        store.setMaster("c1", "s1")
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null))
        val cleared = store.refreshSources("c1", listOf(listed("s2")), 5_000L, mapping("mia"))
        assertThat(cleared).isTrue()
        assertThat(store.master().first()).isNull()
        assertThat(rowsFor("s1")).isEqualTo(Triple(0L, null, 0))
    }

    @Test
    fun aMasterThatBecomesReadOnlyIsClearedButStays() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        val cleared = store.refreshSources("c1", listOf(listed("s1", writable = false)), 5_000L, mapping("mia"))
        assertThat(cleared).isTrue()
        assertThat(store.master().first()).isNull()
        assertThat(store.source("c1", "s1")!!.source.writable).isFalse()
    }

    @Test
    fun aWritableMasterStaysTheMasterThroughARefresh() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        assertThat(store.refreshSources("c1", listOf(listed("s1", writable = true)), 5_000L, mapping("mia"))).isFalse()
        assertThat(store.master().first()?.source?.id).isEqualTo("s1")
    }

    @Test
    fun refreshingRecordsWhenItChecked() = runTest {
        connect("s1")
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isNull()
        store.refreshSources("c1", listOf(listed("s1")), 5_000L, mapping("mia"))
        assertThat(store.connectionsNow().single().sourcesCheckedMillis).isEqualTo(5_000L)
    }

    @Test
    fun aSyncOrAcceptedWriteForARemovedSourceWritesNothing() = runTest {
        connect("s1")
        val queued = store.enqueue(change(ChangeKind.UPDATE, remoteId = "a"))
        store.removeConnection("c1")
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        store.applyAccepted("c1", "s1", timed("a", "Walk", 23, 9), zone, completing = queued)
        assertThat(eventRows("s1")).isEqualTo(0L)
        assertThat(store.cursor("c1", "s1", window)).isNull()
    }

    @Test
    fun aRefreshForARemovedConnectionWritesNothing() = runTest {
        connect("s1")
        store.removeConnection("c1")
        assertThat(store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 5_000L, mapping("mia"))).isFalse()
        assertThat(store.sources().first()).isEmpty()
    }

    @Test
    fun aDuplicatedSourceIdInTheProviderListIsAddedOnce() = runTest {
        connect("s1")
        store.refreshSources("c1", listOf(listed("s1"), listed("s2"), listed("s2")), 5_000L, mapping("mia"))
        assertThat(store.sources().first().map { it.source.id }).containsExactly("s1", "s2")
    }

    @Test
    fun aHiddenCalendarStaysHiddenWhileItsTickIsUnchanged() = runTest {
        connect("s1", "s2")
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 5_000L, mapping("mia"))
        store.refreshSources("c1", listOf(listed("s1"), listed("s2")), 6_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isFalse()
    }

    @Test
    fun aChangedTickInTheServiceWins() = runTest {
        connect("s1")
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        store.refreshSources("c1", listOf(listed("s1", shown = false)), 5_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isFalse()
        store.refreshSources("c1", listOf(listed("s1", shown = true)), 6_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = true)
        store.refreshSources("c1", listOf(listed("s1", shown = false)), 7_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isFalse()
    }

    @Test
    fun theMasterStaysShownWhateverTheServiceSays() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        store.refreshSources("c1", listOf(listed("s1", writable = true, shown = false)), 5_000L, mapping("mia"))
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun setMappingChangesWhoItIsForAndWhetherItShows() = runTest {
        connect("s1")
        store.setMapping("c1", "s1", PersonId("mia"), visible = false)
        assertThat(store.source("c1", "s1")!!.mapping).isEqualTo(SourceMapping(PersonId("mia"), visible = false))
    }

    @Test
    fun theMasterCannotBeHidden() = runTest {
        connect("s1")
        store.setMaster("c1", "s1")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setMapping("c1", "s1", PersonId.FAMILY, visible = false) }
        }
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun makingAHiddenCalendarTheMasterShowsIt() = runTest {
        connect("s1")
        store.setMapping("c1", "s1", PersonId.FAMILY, visible = false)
        store.setMaster("c1", "s1")
        assertThat(store.source("c1", "s1")!!.mapping.visible).isTrue()
    }

    @Test
    fun calendarsOfSomeoneGoneBecomeFamilyAndTheRestStay() = runTest {
        connect("s1", "s2", "s3", mapping = mapOf(
            "s1" to SourceMapping(PersonId("mia"), visible = true),
            "s2" to SourceMapping(PersonId("sam"), visible = false),
        ))
        store.remapMissingPeople(setOf(PersonId("sam")))
        assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
            "s1", SourceMapping(PersonId.FAMILY, visible = true),
            "s2", SourceMapping(PersonId("sam"), visible = false),
            "s3", SourceMapping(PersonId.FAMILY, visible = true),
        )
    }

    @Test
    fun queuedChangesCountsOnlyThatConnection() = runTest {
        connect("s1")
        store.addConnection(Connection("c2", "calendar.test", "Other", emptyMap()), listOf(CalendarSource("t1", "T1", writable = false)), emptyMap())
        store.enqueue(change(ChangeKind.DELETE, remoteId = "a", draft = null))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "b", draft = null))
        store.enqueue(change(ChangeKind.DELETE, remoteId = "c", draft = null).copy(connectionId = "c2", sourceId = "t1"))
        assertThat(store.queuedChanges("c1")).isEqualTo(2)
        assertThat(store.queuedChanges("c2")).isEqualTo(1)
    }

    @Test
    fun makeDueBringsAConnectionsQueueDueWithoutCountingAnAttempt() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null, next = 90_000L, attempts = 3))
        store.makeDue("c1", 1_000L)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(3 to 1_000L)
    }

    @Test
    fun addingAConnectionCanMakeOneOfItsSourcesTheMaster() = runTest {
        store.addConnection(conn, listOf(listed("s1", writable = true, primary = true)), emptyMap(), masterSourceId = "s1")
        assertThat(store.master().first()?.source?.id).isEqualTo("s1")
    }

    @Test
    fun aNeedsSignInLapseIsFoldedIntoEachRowWhenTheConnectionIsOk() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        assertThat(store.connectionsNow().single().needsSignInSinceMillis).isEqualTo(2_000L)
        store.setHealth("c1", ConnectionHealth.Ok, 10_000L)
        // The 8 s lapse no longer counts: the row reads as made 8 s later.
        assertThat(store.pendingNow().single().createdMillis).isEqualTo(9_000L)
        assertThat(store.connectionsNow().single().needsSignInSinceMillis).isNull()
    }

    @Test
    fun aRowQueuedDuringTheLapseIsPausedOnlyFromItsCreation() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 5_000L))
        store.markSynced("c1", 10_000L)
        assertThat(store.pendingNow().single().createdMillis).isEqualTo(10_000L)
        assertThat(store.connectionsNow().single().needsSignInSinceMillis).isNull()
    }

    @Test
    fun foldingAPauseKeepsTheQueueInTheOrderItWasMade() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, remoteId = "first", draft = null).copy(createdMillis = 1_000L))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        store.enqueue(change(ChangeKind.DELETE, remoteId = "second", draft = null).copy(createdMillis = 5_000L))
        store.markSynced("c1", 10_000L)
        // Both now read as made at 9 000 and 10 000; the queue is ordered by id, never by createdMillis.
        assertThat(store.pendingNow().map { it.remoteId to it.createdMillis })
            .containsExactly("first" to 9_000L, "second" to 10_000L).inOrder()
    }

    @Test
    fun aRepeatedNeedsSignInKeepsWhenThePauseBeganAndOnlyOkEndsIt() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 2_000L)
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 4_000L)
        store.setHealth("c1", ConnectionHealth.Unreachable, 6_000L)
        store.setHealth("c1", ConnectionHealth.Error("quota exceeded"), 7_000L)
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Error("quota exceeded"))
        assertThat(stored.needsSignInSinceMillis).isEqualTo(2_000L)
    }

    @Test
    fun anOkWithNoPauseRunningChangesNoRow() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L))
        store.markSynced("c1", 10_000L)
        assertThat(store.pendingNow().single().createdMillis).isEqualTo(1_000L)
    }

    @Test
    fun aPauseEndingBeforeItBeganMovesNoRow() = runTest {
        connect("s1")
        store.enqueue(change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn, 10_000L)
        // The wall clock stepped back.
        store.setHealth("c1", ConnectionHealth.Ok, 5_000L)
        assertThat(store.pendingNow().single().createdMillis).isEqualTo(1_000L)
    }

    @Test
    fun ageMillisIsNeverNegative() {
        val change = change(ChangeKind.DELETE, draft = null).copy(createdMillis = 5_000L)
        assertThat(ageMillis(change, pausedSince = null, nowMillis = 1_000L)).isEqualTo(0L)
    }

    @Test
    fun ageMillisCountsOnlyTimeTheConnectionWasNotWaitingForSignIn() {
        val change = change(ChangeKind.DELETE, draft = null).copy(createdMillis = 1_000L)
        assertThat(ageMillis(change, pausedSince = null, nowMillis = 5_000L)).isEqualTo(4_000L)
        assertThat(ageMillis(change, pausedSince = 3_000L, nowMillis = 5_000L)).isEqualTo(2_000L)
        // Made during the running pause: no age yet.
        assertThat(ageMillis(change.copy(createdMillis = 4_000L), pausedSince = 3_000L, nowMillis = 5_000L)).isEqualTo(0L)
    }

    @Test
    fun outboxKeepsItsFieldsAndTheColour() = runTest {
        connect("s1")
        val update = change(ChangeKind.UPDATE).copy(
            draft = eventDraft("Swim", 23, 9).copy(forPersonColor = 0xFF4CB387),
            fields = setOf(EventField.TITLE, EventField.TIMES),
        )
        val id = store.enqueue(update)
        assertThat(store.pendingNow().single()).isEqualTo(update.copy(id = id))
    }

    @Test
    fun eventsKeepTheirRecurrenceRule() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "Swim", 23, 9).copy(recurring = true, recurrenceRule = "RRULE:FREQ=WEEKLY")))
        assertThat(store.eventNow(EventRef("c1", "s1", "a"))!!.recurrenceRule).isEqualTo("RRULE:FREQ=WEEKLY")
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
