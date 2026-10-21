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
    private val keys = ConcurrentHashMap.newKeySet<String>()

    fun add(key: String) {
        keys += key
    }

    fun has(key: String): Boolean = key in keys

    /** Nothing can bring [key]'s event back any more. */
    fun done(key: String) {
        keys -= key
    }

    val pending: Set<String> get() = keys.toSet()
}
