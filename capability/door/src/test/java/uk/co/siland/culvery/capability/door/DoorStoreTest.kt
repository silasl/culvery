package uk.co.siland.culvery.capability.door

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.capability.door.db.SecureDelete
import uk.co.siland.culvery.core.plugin.RailBadge

/** 5 §4.3–4.5, §4.7–4.8, 15a §3.1: merge on key and time, snapshots, the keep-days prune and orphans, what is unseen. */
@RunWith(AndroidJUnit4::class)
class DoorStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: DoorDatabase
    private lateinit var dir: File
    private lateinit var store: DoorStore
    private val london = ZoneId.of("Europe/London")
    private val sinceYesterday = startOfYesterday(LocalDate.of(2026, 10, 14), london)

    @Before
    fun setUp() {
        db = doorDb()
        dir = File(folder.root, "door")
        store = DoorStore(db, dir)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aNewNoticeIsANewUnseenEvent() = runTest {
        val recorded = store.record(ring())
        assertThat(recorded.isNew).isTrue()
        assertThat(recorded.event).isEqualTo(DoorEvent(recorded.event.id, NOW, DoorKind.RING, "Front Door", false, null, seen = false))
        assertThat(store.events.first()).containsExactly(recorded.event)
    }

    /** Ring posts the words first, then the same notification (key and time) with the snapshot (5 §4.3). */
    @Test
    fun theSameNotificationWithinTwoMinutesUpdatesItsEventWithThePicture() = runTest {
        val first = store.record(ring())
        val again = store.record(ring(at = NOW + MERGE_MS, device = "Front Door Pro", snapshot = aPicture))
        assertThat(again.isNew).isFalse()
        assertThat(again.event.id).isEqualTo(first.event.id)
        assertThat(again.event.device).isEqualTo("Front Door Pro")
        assertThat(again.event.snapshot).isEqualTo(File(dir, "${first.event.id}.jpg"))
        assertThat(store.events.first()).hasSize(1)
    }

    @Test
    fun theSameNotificationAfterTwoMinutesIsANewEvent() = runTest {
        store.record(ring())
        assertThat(store.record(ring(at = NOW + MERGE_MS + 1)).isNew).isTrue()
        assertThat(store.events.first()).hasSize(2)
    }

    /** A second press reuses Ring's key with a new time; it is a second ring, not a re-post. */
    @Test
    fun theSameKeyWithANewTimeIsANewRing() = runTest {
        store.record(ring())
        assertThat(store.record(ring(at = NOW + 30_000, whenMillis = NOW + 30_000)).isNew).isTrue()
        assertThat(store.events.first()).hasSize(2)
    }

    @Test
    fun aRepostThatIsRecognisedClearsUnrecognised() = runTest {
        store.record(ring(unrecognised = true))
        assertThat(store.record(ring(at = NOW + 1_000)).event.unrecognised).isFalse()
    }

    /** 5 §6: the event stays without a picture, and the log names no device. */
    @Test
    fun aFailedSnapshotKeepsTheEventWithoutAPicture() = runTest {
        ShadowLog.clear()
        val recorded = store.record(ring(snapshot = Snapshot { throw IOException("disk full") }))
        assertThat(recorded.event.snapshot).isNull()
        assertThat(dir.names()).isEmpty()
        val logs = ShadowLog.getLogs().filter { it.tag == "DoorStore" }
        assertThat(logs).isNotEmpty()
        logs.forEach { assertThat(it.msg).doesNotContain("Front Door") }
    }

    /** A picture too large for memory is a failed save too, not a crash. */
    @Test
    fun aPictureThatRunsOutOfMemoryKeepsTheEventWithoutIt() = runTest {
        val recorded = store.record(ring(snapshot = Snapshot { throw OutOfMemoryError() }))
        assertThat(recorded.event.snapshot).isNull()
        assertThat(store.events.first()).hasSize(1)
    }

    private fun londonMillis(local: String) = LocalDateTime.parse(local).atZone(london).toInstant().toEpochMilli()

    private fun keeping(keep: KeepDays) = DoorStore(db, dir, keepFrom = { keepFrom(dateIn(it, london), london, keep) })

    /** Records an event at each London local time in [at], then prunes at [NOW]. */
    private suspend fun prunedAt(store: DoorStore, vararg at: String) {
        at.forEach { store.record(ring(key = it, at = londonMillis(it), whenMillis = londonMillis(it))) }
        store.prune(NOW)
    }

    /** 15a §3.1: two days are today and yesterday, from yesterday's midnight. Fails if the cut is 48 hours back or at today's midnight. */
    @Test
    fun twoDaysKeepsTodayAndYesterday() = runTest {
        val before = store.record(ring(key = "b", at = londonMillis("2026-10-12T23:59:00"), snapshot = aPicture)).event
        val after = store.record(ring(key = "a", at = londonMillis("2026-10-13T00:00:00"), snapshot = aPicture)).event
        File(dir, "999.jpg").writeBytes(byteArrayOf(1))
        File(dir, "${after.id}.jpg.part").writeBytes(byteArrayOf(1))
        store.prune(NOW)
        assertThat(store.events.first().map { it.id }).containsExactly(after.id)
        assertThat(dir.names()).containsExactly("${after.id}.jpg")
        assertThat(before.snapshot!!.exists()).isFalse()
    }

    /** The cut is the household's midnight [keep] days back, today included. */
    @Test
    fun sevenAndThirtyDaysCutAtMidnight() = runTest {
        val seven = keeping(KeepDays.SEVEN)
        prunedAt(seven, "2026-10-07T23:59:00", "2026-10-08T00:00:00")
        assertThat(seven.events.first().single().atMillis).isEqualTo(londonMillis("2026-10-08T00:00:00"))
        db.clearAllTables()
        val thirty = keeping(KeepDays.THIRTY)
        prunedAt(thirty, "2026-09-14T23:59:00", "2026-09-15T00:00:00")
        assertThat(thirty.events.first().single().atMillis).isEqualTo(londonMillis("2026-09-15T00:00:00"))
    }

    /** Europe/London, Monday 26 October 2026 after the clocks went back: the cut is Sunday's midnight BST, 23:00Z, not UTC midnight. */
    @Test
    fun theCutHoldsAcrossTheClockChange() = runTest {
        val goes = Instant.parse("2026-10-24T22:30:00Z").toEpochMilli()
        val stays = Instant.parse("2026-10-24T23:30:00Z").toEpochMilli()
        store.record(ring(key = "goes", at = goes, whenMillis = goes))
        val kept = store.record(ring(key = "stays", at = stays, whenMillis = stays)).event
        store.prune(Instant.parse("2026-10-26T12:00:00Z").toEpochMilli())
        assertThat(store.events.first().map { it.id }).containsExactly(kept.id)
    }

    /** Review Focus 5: a restart reads the family's 30 days from the file, not the two-day stand-in a fresh DoorSettings holds until it has read it. */
    @Test
    fun aColdStartKeepsThirtyDays() = runTest {
        val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        doorSettings(folder.root, writer).setKeep(KeepDays.THIRTY)
        writer.coroutineContext[Job]!!.cancelAndJoin()
        val reader = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cold = DoorStore(db, ApplicationProvider.getApplicationContext(), doorSettings(folder.root, reader), householdZone())
            val tenDays = NOW - 10 * 24 * 60 * 60_000L
            val old = cold.record(ring(key = "old", at = tenDays, whenMillis = tenDays)).event
            cold.record(ring(key = "older", at = NOW - 40 * 24 * 60 * 60_000L))
            cold.prune(NOW)
            assertThat(cold.events.first().map { it.id }).containsExactly(old.id)
        } finally {
            reader.cancel()
        }
    }

    /** A corrupt settings file reads as thirty days, so the prune that follows keeps a ten-day-old event. */
    @Test
    fun aCorruptSettingsFileDoesNotCutHistory() = runTest {
        File(folder.root, "door_settings.preferences_pb").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val reader = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val cold = DoorStore(db, ApplicationProvider.getApplicationContext(), doorSettings(folder.root, reader), householdZone())
            val tenDays = NOW - 10 * 24 * 60 * 60_000L
            val old = cold.record(ring(key = "old", at = tenDays, whenMillis = tenDays)).event
            cold.prune(NOW)
            assertThat(cold.events.first().map { it.id }).containsExactly(old.id)
        } finally {
            reader.cancel()
        }
    }

    /** An unreadable setting is not a choice of two days: no event goes, though the files with no event still do. */
    @Test
    fun anUnreadableSettingDeletesNoRows() = runTest {
        val unreadable = DoorStore(db, dir, keepFrom = { null })
        val old = unreadable.record(ring(key = "old", at = NOW - 40 * 24 * 60 * 60_000L, snapshot = aPicture)).event
        val recent = unreadable.record(ring(key = "new", snapshot = aPicture)).event
        File(dir, "999.jpg").writeBytes(byteArrayOf(1))
        unreadable.prune(NOW)
        assertThat(unreadable.events.first().map { it.id }).containsExactly(recent.id, old.id)
        assertThat(dir.names()).containsExactly("${old.id}.jpg", "${recent.id}.jpg")
    }

    @Test
    fun recordingPrunes() = runTest {
        store.record(ring(key = "old", at = NOW - 3 * 24 * 60 * 60_000L))
        store.record(ring(key = "new"))
        assertThat(store.events.first().map { it.atMillis }).containsExactly(NOW)
    }

    /** 5 §4.7–4.8: unrecognised alerts count as rings; Got it marks one; opening the tab takes them all. */
    @Test
    fun unseenCountsRingsAndMotionUntilSeen() = runTest {
        val a = store.record(ring(key = "a")).event
        store.record(ring(key = "b", unrecognised = true))
        store.record(motion())
        assertThat(store.unseen(sinceYesterday).first()).isEqualTo(Unseen(rings = 2, motion = 1))
        store.markSeen(a.id)
        assertThat(store.unseen(sinceYesterday).first()).isEqualTo(Unseen(rings = 1, motion = 1))
        store.takeUnseen()
        assertThat(store.unseen(sinceYesterday).first()).isEqualTo(Unseen(rings = 0, motion = 0))
    }

    /** The badge counts the days the tab shows: an unseen ring from the day before yesterday, still kept, doesn't count. */
    @Test
    fun unseenCountsOnlyTodayAndYesterday() = runTest {
        store.record(ring(key = "old", at = sinceYesterday - 1, whenMillis = sinceYesterday - 1))
        store.record(ring(key = "yesterday", at = sinceYesterday, whenMillis = sinceYesterday))
        assertThat(store.unseen(sinceYesterday).first()).isEqualTo(Unseen(rings = 1, motion = 0))
    }

    @Test
    fun eventsAreNewestFirst() = runTest {
        store.record(ring(key = "a", at = NOW - 5_000))
        store.record(motion(at = NOW))
        assertThat(store.events.first().map { it.kind }).containsExactly(DoorKind.MOTION, DoorKind.RING).inOrder()
    }

    /** Got it or opening the tab landing while a repost's picture is being saved must stay marked. */
    @Test
    fun aRepostSavingItsPictureDoesNotUndoSeen() = runTest {
        val first = store.record(ring())
        val again = store.record(ring(snapshot = Snapshot { it.writeBytes(byteArrayOf(1)); runBlocking { store.markSeen(first.event.id) } }))
        assertThat(again.event.seen).isTrue()
        assertThat(store.events.first().single().seen).isTrue()
    }

    @Test
    fun aNewEventSavingItsPictureDoesNotUndoTakingTheUnseen() = runTest {
        // The event isn't stored until the picture is saved, so mark the first event seen from inside the second's save.
        val a = store.record(ring(key = "a")).event
        val b = store.record(ring(key = "b", snapshot = Snapshot { it.writeBytes(byteArrayOf(1)); runBlocking { store.takeUnseen() } }))
        assertThat(store.events.first().map { it.seen }).containsExactly(true, true)
        assertThat(b.event.snapshot).isNotNull()
        assertThat(a.id).isNotEqualTo(b.event.id)
    }

    /** 9 §6.8: fails if the take marks a ring it doesn't return, or returns one it doesn't mark. */
    @Test
    fun takeUnseenMarksExactlyWhatItReturns() = runTest {
        val a = store.record(ring(key = "a")).event
        val b = store.record(motion()).event
        val c = store.record(ring(key = "c")).event
        store.markSeen(c.id)
        assertThat(store.takeUnseen()).containsExactly(a.id, b.id)
        assertThat(store.unseen(sinceYesterday).first()).isEqualTo(Unseen(rings = 0, motion = 0))
        assertThat(store.takeUnseen()).isEmpty()
    }

    /** 9 §6.8: fails if a take marks seen a ring it didn't return, as marking every unseen row would for one stored mid-take. */
    @Test
    fun aRingArrivingDuringTheMarkIsNotLost() = runBlocking {
        val taken = mutableSetOf<Long>()
        repeat(50) { round ->
            val recording = async(Dispatchers.Default) { store.record(ring(key = "r$round", whenMillis = NOW + round)) }
            val take = async(Dispatchers.Default) { store.takeUnseen() }
            recording.await()
            taken += take.await()
        }
        val events = store.events.first()
        assertThat(events).hasSize(50)
        events.forEach { assertThat(it.seen).isEqualTo(it.id in taken) }
    }

    /** Pin: a later notice for a seen ring, its picture, leaves it seen: the ring was already acknowledged. */
    @Test
    fun aPictureArrivingForASeenRingStaysSeen() = runTest {
        val first = store.record(ring()).event
        assertThat(store.takeUnseen()).containsExactly(first.id)
        val again = store.record(ring(at = NOW + 1_000, snapshot = aPicture)).event
        assertThat(again.snapshot).isNotNull()
        assertThat(again.seen).isTrue()
    }

    /** The boundary is the household's local midnight the day before, not UTC and not today's. */
    @Test
    fun yesterdayStartsAtLocalMidnightAcrossTheClockChange() {
        fun at(day: String) = startOfYesterday(LocalDate.parse(day), london)
        assertThat(at("2026-10-14")).isEqualTo(Instant.parse("2026-10-12T23:00:00Z").toEpochMilli())
        assertThat(at("2026-10-26")).isEqualTo(Instant.parse("2026-10-24T23:00:00Z").toEpochMilli())
        assertThat(at("2026-10-27")).isEqualTo(Instant.parse("2026-10-26T00:00:00Z").toEpochMilli())
    }

    /** 15a §3.3: every row goes, and every file in the folder: pictures, a stray .part, an orphan. */
    @Test
    fun clearDeletesEveryRowAndFile() = runTest {
        store.record(ring(key = "a", snapshot = aPicture))
        store.record(ring(key = "b", snapshot = aPicture))
        store.record(motion())
        File(dir, "7.jpg.part").writeBytes(byteArrayOf(1))
        File(dir, "999.jpg").writeBytes(byteArrayOf(1))
        store.clear()
        assertThat(store.events.first()).isEmpty()
        assertThat(dir.listFiles()).isEmpty()
    }

    /** Review Focus 1: a record holding the lock when Clear is asked finishes first, so its picture is cleared with the rest. */
    @Test
    fun aRecordMidClearLeavesNoPicture() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow = Snapshot { it.writeBytes(byteArrayOf(1)); started.countDown(); release.await() }
        val recording = async(Dispatchers.IO) { store.record(ring(snapshot = slow)) }
        started.await()
        val clearing = async(Dispatchers.IO) { store.clear() }
        delay(200)
        assertThat(clearing.isCompleted).isFalse()
        release.countDown()
        val recorded = recording.await()
        clearing.await()
        assertThat(recorded.isNew).isTrue()
        assertThat(store.events.first()).isEmpty()
        assertThat(dir.listFiles()).isEmpty()
    }

    /** Cancelling the caller while the clear is under way doesn't stop it: no row and no picture is left. */
    @Test
    fun aCancelledClearLeavesNoPicture() = runBlocking {
        val gated = GatedIo()
        val gatedStore = DoorStore(db, dir, io = gated)
        repeat(3) { gatedStore.record(ring(key = "k$it", snapshot = aPicture)) }
        gated.holdDispatch(1)
        val clearing = async(Dispatchers.IO) { gatedStore.clear() }
        gated.held.await()
        clearing.cancel()
        gated.release()
        val ended = runCatching { clearing.await() }.exceptionOrNull()
        assertThat(ended).isInstanceOf(CancellationException::class.java)
        assertThat(gatedStore.events.first()).isEmpty()
        assertThat(dir.listFiles()).isEmpty()
    }

    /** A picture that won't delete stops the clear with every row kept, so no row is left without its picture's say. */
    @Test
    fun aPictureThatWontDeleteStopsTheClear() = runBlocking {
        store.record(ring(snapshot = aPicture))
        File(dir, "stuck.jpg").apply { mkdirs() }.also { File(it, "inside").writeBytes(byteArrayOf(1)) }
        val failure = runCatching { store.clear() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(store.events.first()).hasSize(1)
    }

    /** A save stopped half-way leaves no half picture behind. */
    @Test
    fun aCancelledSaveLeavesNoPart() = runBlocking {
        val halfWritten = Snapshot { it.writeBytes(byteArrayOf(1)); throw CancellationException("stopped") }
        try {
            store.record(ring(snapshot = halfWritten))
        } catch (_: CancellationException) {
        }
        assertThat(dir.names()).isEmpty()
    }

    /** Ruling 14: the database opens with secure_delete on, so cleared rows are overwritten. */
    @Test
    fun theDatabaseOpensWithSecureDelete() {
        val secure = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoorDatabase::class.java)
            .allowMainThreadQueries().addCallback(SecureDelete).build()
        try {
            secure.openHelper.writableDatabase.query("PRAGMA secure_delete").use {
                it.moveToFirst()
                assertThat(it.getInt(0)).isEqualTo(1)
            }
        } finally {
            secure.close()
        }
    }

    @Test
    fun theBadgeIsACountOfRingsOrADotForMotionAlone() {
        assertThat(badgeOf(Unseen(0, 0))).isNull()
        assertThat(badgeOf(Unseen(1, 3))).isEqualTo(RailBadge.Count(1, "1 unseen ring"))
        assertThat(badgeOf(Unseen(2, 0))).isEqualTo(RailBadge.Count(2, "2 unseen rings"))
        assertThat(badgeOf(Unseen(0, 1))).isEqualTo(RailBadge.Dot("new motion"))
    }
}
