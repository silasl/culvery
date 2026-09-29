package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.plugin.Connection

/** [OtherCalendar]: a subscription or a provider with no writer. [NotMaster]: a writable calendar changed on a phone. */
enum class ReadOnlyReason { OtherCalendar, NotMaster, Recurring }

/**
 * Null when the tablet may change [event] (spec §6): a non-recurring event on the writable master calendar whose
 * provider has a writer. A master with no writer (a misconfiguration) reads as another calendar. A recurring
 * event on another calendar takes that calendar's reason.
 */
internal fun readOnlyReason(event: StoredEvent, source: StoredSource?, hasWriter: Boolean): ReadOnlyReason? = when {
    source == null || !source.source.writable || !hasWriter -> ReadOnlyReason.OtherCalendar
    !source.isMaster -> ReadOnlyReason.NotMaster
    event.recurring -> ReadOnlyReason.Recurring
    else -> null
}

/** Where new events go: the master calendar and its connection. */
internal class WritableMaster(val source: StoredSource, val connection: Connection)

/**
 * The master calendar if the tablet can add to it (2b-2 design §6): writable, on a connection whose provider binds a
 * writer. Null means there is nowhere to add, which hides the add entry points and makes a create NotEditable.
 */
internal fun writableMaster(master: StoredSource?, connections: List<StoredConnection>, writerIds: Set<String>): WritableMaster? {
    val m = master?.takeIf { it.source.writable } ?: return null
    val connection = connections.firstOrNull { it.connection.id == m.connectionId }?.connection ?: return null
    return if (connection.providerId in writerIds) WritableMaster(m, connection) else null
}
