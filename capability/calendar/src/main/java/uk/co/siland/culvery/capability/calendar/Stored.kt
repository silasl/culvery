package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** Who a source's events belong to when they carry no person tag, and whether the source is shown. */
data class SourceMapping(val person: PersonId, val visible: Boolean) {
    companion object {
        val Default = SourceMapping(PersonId.FAMILY, visible = true)
    }
}

data class StoredConnection(
    val connection: Connection,
    val health: ConnectionHealth,
    val lastSyncMillis: Long?,
)

data class StoredSource(
    val connectionId: String,
    val source: CalendarSource,
    val mapping: SourceMapping,
)

data class StoredEvent(
    val connectionId: String,
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val recurring: Boolean,
    val forPerson: String?,
    val createdBy: String?,
    val sourcePerson: PersonId,
    val startSort: Long,
    val endSort: Long,
)
