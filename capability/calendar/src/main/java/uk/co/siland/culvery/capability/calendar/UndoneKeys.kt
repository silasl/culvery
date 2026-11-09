package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The keys of events Undo removed while a create for them may still land (9 §2.3): the outbox drain deletes any such
 * create the service accepts instead of mirroring it. Kept in memory only, so a restart loses it.
 */
@Singleton
class UndoneKeys @Inject constructor() {
    /** Where an undone create was going, so one that lands can be deleted. */
    internal class Undone(val connectionId: String, val sourceId: String, val series: Boolean)

    private val keys = ConcurrentHashMap<String, Undone>()

    // Creates that landed, or may have, and aren't yet deleted: the drain tries each pass until they go. Kept past done().
    private val strays = ConcurrentHashMap<String, Undone>()

    fun add(key: String, connectionId: String, sourceId: String, series: Boolean) {
        keys[key] = Undone(connectionId, sourceId, series)
    }

    internal operator fun get(key: String): Undone? = keys[key]

    // Creates the drain is sending: an Undo of one leaves its key for the drain to settle (20 §4.3).
    private val onTheirWay = ConcurrentHashMap.newKeySet<String>()

    fun sending(key: String) {
        onTheirWay += key
    }

    fun isSending(key: String): Boolean = key in onTheirWay

    fun sent(key: String) {
        onTheirWay -= key
    }

    /** Nothing can bring [key]'s event back any more. */
    fun done(key: String) {
        keys -= key
    }

    internal val strayCreates: Map<String, Undone> get() = strays

    internal fun stray(key: String, undone: Undone) {
        strays[key] = undone
    }

    /** [key]'s stray is deleted, or can't be: nothing more to try. */
    internal fun letGo(key: String) {
        strays -= key
        done(key)
    }

    internal val pending: Set<String> get() = keys.keys.toSet()
}
