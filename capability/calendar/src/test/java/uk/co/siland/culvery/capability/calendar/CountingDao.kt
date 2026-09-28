package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.capability.calendar.db.CalendarDao

/** The real DAO, remembering the most variables a list query bound: API 30's SQLite allows 999 per statement. */
internal class CountingDao(private val real: CalendarDao) : CalendarDao by real {
    var mostBound = 0
        private set

    override suspend fun deleteEvents(connectionId: String, sourceId: String, ids: List<String>) {
        // The ids, plus the connection and the source.
        mostBound = maxOf(mostBound, ids.size + 2)
        real.deleteEvents(connectionId, sourceId, ids)
    }
}
