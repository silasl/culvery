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

/** [isMaster]: the household's master calendar, the only source the tablet writes to (spec §6). */
data class StoredSource(
    val connectionId: String,
    val source: CalendarSource,
    val mapping: SourceMapping,
    val isMaster: Boolean = false,
)

/** One mirrored event. Ids from providers may contain any character, including "/". */
data class EventRef(val connectionId: String, val sourceId: String, val remoteId: String) {
    /** A Bundle-safe lazy-list key. NUL never appears in a provider id, so it can't be mistaken for part of one. */
    val listKey: String get() = "$connectionId\u0000$sourceId\u0000$remoteId"
}

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
) {
    val ref: EventRef get() = EventRef(connectionId, sourceId, remoteId)
}

/** ASSIGN changes only who an event is for: it is sent as the event is when it is sent, with the new person. */
enum class ChangeKind { CREATE, UPDATE, DELETE, ASSIGN }

/**
 * One queued write, kept until the provider accepts or refuses it. [remoteId] is null only for CREATE, which
 * carries its [clientKey] instead: the id the provider gives the event (CalendarWriter.create), so a retried create
 * can't make a second one. [draft] is null only for DELETE, and for ASSIGN only its forPerson counts. [id] is 0
 * until the store assigns one.
 */
data class PendingChange(
    val id: Long,
    val connectionId: String,
    val sourceId: String,
    val remoteId: String?,
    val kind: ChangeKind,
    val draft: EventDraft?,
    val attempts: Int,
    val nextAttemptMillis: Long,
    val createdMillis: Long,
    val clientKey: String? = null,
) {
    /**
     * The event this change is for. A create's ref is built from its client key, the id the event keeps once it
     * syncs, so the changes queued behind it and the sheets showing it use one ref throughout.
     */
    val ref: EventRef?
        get() = (if (kind == ChangeKind.CREATE) clientKey else remoteId)?.let { EventRef(connectionId, sourceId, it) }
}
