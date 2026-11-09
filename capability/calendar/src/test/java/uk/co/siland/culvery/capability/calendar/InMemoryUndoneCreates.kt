package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.ConcurrentHashMap
import uk.co.siland.culvery.capability.calendar.db.UndoneCreateDao
import uk.co.siland.culvery.capability.calendar.db.UndoneCreateEntity

/** The undone creates' table, for a test with no database of its own. */
internal class InMemoryUndoneCreates : UndoneCreateDao {
    private val rows = ConcurrentHashMap<String, UndoneCreateEntity>()

    override suspend fun all(): List<UndoneCreateEntity> = rows.values.toList()

    override suspend fun upsert(e: UndoneCreateEntity) {
        rows[e.clientKey] = e
    }

    override suspend fun delete(key: String) {
        rows -= key
    }
}
