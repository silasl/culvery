package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.db.UndoneCreateDao
import uk.co.siland.culvery.capability.calendar.db.UndoneCreateEntity

/**
 * The keys of events Undo removed while a create for them may still land (9 §2.3): the outbox drain deletes any such
 * create the service accepts instead of mirroring it. Written through to the database, so a restart keeps them (20 §4.3).
 */
@Singleton
class UndoneKeys @Inject constructor(private val dao: UndoneCreateDao) {
    /** Where an undone create was going, so one that lands can be deleted. */
    internal class Undone(val connectionId: String, val sourceId: String, val series: Boolean)

    private val keys = ConcurrentHashMap<String, Undone>()

    // Creates that landed, or may have, and aren't yet deleted: the drain tries each pass until they go. Kept past done().
    private val strays = ConcurrentHashMap<String, Undone>()

    // Creates the drain is sending: an Undo of one leaves its key for the drain to settle (20 §4.3).
    private val onTheirWay = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var loaded = false

    /**
     * Reads what was stored before a restart, once, into what is already held. Nothing is on its way after a restart, so
     * each stored key is a stray: the next drain deletes it, where gone counts as done.
     */
    suspend fun load() {
        if (loaded) return
        for (row in dao.all()) {
            val undone = Undone(row.connectionId, row.sourceId, row.series)
            keys.putIfAbsent(row.clientKey, undone)
            strays.putIfAbsent(row.clientKey, undone)
        }
        loaded = true
    }

    suspend fun add(key: String, connectionId: String, sourceId: String, series: Boolean) {
        val undone = Undone(connectionId, sourceId, series)
        keys[key] = undone
        dao.upsert(undone.row(key, stray = strays.containsKey(key)))
    }

    internal operator fun get(key: String): Undone? = keys[key]

    fun sending(key: String) {
        onTheirWay += key
    }

    fun isSending(key: String): Boolean = key in onTheirWay

    fun sent(key: String) {
        onTheirWay -= key
    }

    /** Nothing can bring [key]'s event back any more. */
    suspend fun done(key: String) {
        keys -= key
        if (!strays.containsKey(key)) dao.delete(key)
    }

    internal val strayCreates: Map<String, Undone> get() = strays

    internal suspend fun stray(key: String, undone: Undone) {
        strays[key] = undone
        dao.upsert(undone.row(key, stray = true))
    }

    /** [key]'s stray is deleted, or can't be: nothing more to try. */
    internal suspend fun letGo(key: String) {
        strays -= key
        done(key)
    }

    internal val pending: Set<String> get() = keys.keys.toSet()

    private fun Undone.row(key: String, stray: Boolean) = UndoneCreateEntity(key, connectionId, sourceId, series, stray)
}
