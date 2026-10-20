package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import uk.co.siland.culvery.core.plugin.RailBadge

/** 5 §4.3–4.5, §4.7–4.8: merge on key and time, snapshots, the 48-hour prune and orphans, what is unseen. */
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

    /** 5 §4.5: older than 48 hours goes with its picture; files with no event go too. */
    @Test
    fun pruneDeletesOldEventsTheirSnapshotsAndOrphans() = runTest {
        val old = store.record(ring(key = "old", at = NOW - KEEP_MS - 1, snapshot = aPicture)).event
        val edge = store.record(ring(key = "edge", at = NOW - KEEP_MS, snapshot = aPicture)).event
        File(dir, "999.jpg").writeBytes(byteArrayOf(1))
        File(dir, "${edge.id}.jpg.part").writeBytes(byteArrayOf(1))
        store.prune(NOW)
        assertThat(store.events.first().map { it.id }).containsExactly(edge.id)
        assertThat(dir.names()).containsExactly("${edge.id}.jpg")
        assertThat(old.snapshot!!.exists()).isFalse()
    }

    @Test
    fun recordingPrunes() = runTest {
        store.record(ring(key = "old", at = NOW - KEEP_MS - 1))
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

    /** 9 §6.8: a ring stored while the tab takes the unseen is either taken (so shown NEW) or left unseen, never seen unshown. */
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

    @Test
    fun theBadgeIsACountOfRingsOrADotForMotionAlone() {
        assertThat(badgeOf(Unseen(0, 0))).isNull()
        assertThat(badgeOf(Unseen(1, 3))).isEqualTo(RailBadge.Count(1, "1 unseen ring"))
        assertThat(badgeOf(Unseen(2, 0))).isEqualTo(RailBadge.Count(2, "2 unseen rings"))
        assertThat(badgeOf(Unseen(0, 1))).isEqualTo(RailBadge.Dot("new motion"))
    }
}
