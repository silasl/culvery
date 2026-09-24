package uk.co.siland.culvery.capability.calendar

enum class ReadOnlyReason { OtherCalendar, Recurring }

/**
 * Null when the tablet may change [event] (spec §6): a non-recurring event on the writable master calendar whose
 * provider has a writer. A master with no writer (a misconfiguration) reads as another calendar. A recurring
 * event on another calendar is OtherCalendar.
 */
internal fun readOnlyReason(event: StoredEvent, source: StoredSource?, hasWriter: Boolean): ReadOnlyReason? = when {
    source == null || !source.isMaster || !source.source.writable || !hasWriter -> ReadOnlyReason.OtherCalendar
    event.recurring -> ReadOnlyReason.Recurring
    else -> null
}
