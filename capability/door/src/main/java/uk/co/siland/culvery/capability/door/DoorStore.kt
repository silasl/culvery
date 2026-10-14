package uk.co.siland.culvery.capability.door

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.capability.door.db.DoorEventEntity

/** A notification posted again within this long of its event updates it (5 §4.3). */
const val MERGE_MS = 120_000L

/** Events and their pictures are kept this long (5 §2.8). */
const val KEEP_MS = 48 * 60 * 60 * 1_000L

private const val SNAPSHOT_DIR = "door"
private const val TAG = "DoorStore"

data class Recorded(val event: DoorEvent, val isNew: Boolean)

data class Unseen(val rings: Int, val motion: Int)

/** Where the Door tab's days begin: the household's yesterday, at midnight (5 §5.2). */
fun startOfYesterday(today: LocalDate, zone: ZoneId): Long = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

/** `door.db` and `files/door/` (5 §3.1): rings and motion for 48 hours, with their pictures. */
@Singleton
class DoorStore internal constructor(
    db: DoorDatabase,
    private val dir: File,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    @Inject
    constructor(db: DoorDatabase, @ApplicationContext context: Context) : this(db, File(context.filesDir, SNAPSHOT_DIR))

    private val dao = db.doorDao()

    // A record and a prune never meet: a prune would take a picture still being written for an event not yet stored.
    private val lock = Mutex()

    /** Newest first. */
    val events: Flow<List<DoorEvent>> = dao.events().map { rows -> rows.map { it.toEvent() } }

    /** Unseen rings (unrecognised ones included) and unseen motion since [sinceMillis] (5 §4.8): the days the tab shows. */
    fun unseen(sinceMillis: Long): Flow<Unseen> = dao.unseen(sinceMillis).distinctUntilChanged()

    /**
     * Records [notice] (5 §4.3–4.5): the same notification (key and time) within [MERGE_MS] of its event updates that
     * event (its device, whether it was recognised, a new picture) and isn't new; otherwise a new event. Then prunes.
     */
    suspend fun record(notice: DoorNotice): Recorded = lock.withLock {
        val earlier = dao.sameNotice(notice.key, notice.whenMillis, notice.atMillis - MERGE_MS)
        val recorded = if (earlier != null) {
            val saved = notice.snapshot?.let { save(earlier.id, it) } == true
            dao.updateNotice(earlier.id, notice.device, notice.unrecognised)
            if (saved) dao.markHasSnapshot(earlier.id)
            Recorded(dao.byId(earlier.id).toEvent(), isNew = false)
        } else {
            val row = DoorEventEntity(
                noticeKey = notice.key, noticeWhen = notice.whenMillis, atMillis = notice.atMillis, kind = notice.kind.name,
                device = notice.device, unrecognised = notice.unrecognised, hasSnapshot = false, seen = false,
            )
            val id = dao.insert(row)
            val saved = notice.snapshot?.let { save(id, it) } == true
            if (saved) dao.markHasSnapshot(id)
            Recorded(dao.byId(id).toEvent(), isNew = true)
        }
        pruneLocked(notice.atMillis)
        recorded
    }

    /** Deletes events older than [KEEP_MS] before [nowMillis], their pictures, and pictures with no event (5 §4.5). */
    suspend fun prune(nowMillis: Long) = lock.withLock { pruneLocked(nowMillis) }

    suspend fun markAllSeen() = dao.markAllSeen()

    suspend fun markSeen(id: Long) = dao.markSeen(id)

    private suspend fun pruneLocked(nowMillis: Long) {
        dao.deleteBefore(nowMillis - KEEP_MS)
        val kept = dao.withSnapshots().map { fileOf(it).name }.toSet()
        withContext(io) { dir.listFiles()?.filter { it.name !in kept }?.forEach { it.delete() } }
    }

    /** Written beside, then renamed, so a failed or interrupted save never leaves half a picture under the event's name. */
    private suspend fun save(id: Long, snapshot: Snapshot): Boolean = withContext(io) {
        val part = File(dir, "$id.jpg.part")
        try {
            dir.mkdirs()
            snapshot.saveTo(part)
            check(part.renameTo(fileOf(id))) { "The picture couldn't be renamed" }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(part, e)
        } catch (e: OutOfMemoryError) {
            failed(part, e)
        }
    }

    private fun failed(part: File, e: Throwable): Boolean {
        Log.w(TAG, "Couldn't save a door snapshot (${e::class.simpleName})")
        part.delete()
        return false
    }

    private fun fileOf(id: Long) = File(dir, "$id.jpg")

    private fun DoorEventEntity.toEvent() =
        DoorEvent(id, atMillis, DoorKind.valueOf(kind), device, unrecognised, fileOf(id).takeIf { hasSnapshot }, seen)
}
