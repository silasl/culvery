package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.core.household.PersonId

/** A mirrored event as the UI shows it, with any queued change laid over it. */
internal data class ShownEvent(val event: StoredEvent, val syncing: Boolean)

/**
 * A queued create as the event it will become, under its client key: the ref it keeps once it syncs (2b-2 design
 * D5). Null for any other kind, or for a create without a key or a draft.
 */
internal fun PendingChange.asCreatedEvent(sourcePerson: PersonId, zone: ZoneId): StoredEvent? {
    if (kind != ChangeKind.CREATE) return null
    val key = clientKey ?: return null
    val d = draft ?: return null
    return StoredEvent(
        connectionId = connectionId,
        sourceId = sourceId,
        remoteId = key,
        title = d.title,
        start = d.start,
        end = d.end,
        recurring = false,
        forPerson = d.forPerson,
        createdBy = d.createdBy,
        sourcePerson = sourcePerson,
        startSort = d.start.instantIn(zone).toEpochMilli(),
        endSort = d.end.instantIn(zone).toEpochMilli(),
    )
}

/**
 * Lays [pending] changes over [events] in queue order. A delete hides the event. An update shows the draft's
 * values for its fields; an assign shows only its person. A create adds its draft, on a visible source, under its client key, so
 * the changes queued behind it apply to it and it keeps its ref once it syncs. Only events overlapping
 * [windowStart, windowEnd) are returned, in start order.
 */
internal fun overlayPending(
    events: List<StoredEvent>,
    pending: List<PendingChange>,
    sources: (connectionId: String, sourceId: String) -> StoredSource?,
    zone: ZoneId,
    windowStart: Long,
    windowEnd: Long,
): List<ShownEvent> {
    val shown = LinkedHashMap<EventRef, ShownEvent>()
    events.forEach { shown[it.ref] = ShownEvent(it, syncing = false) }
    for (change in pending) {
        val draft = change.draft
        when (change.kind) {
            ChangeKind.DELETE -> change.ref?.let { shown.remove(it) }
            ChangeKind.UPDATE, ChangeKind.ASSIGN -> {
                val ref = change.ref ?: continue
                val current = shown[ref] ?: continue
                if (draft != null) {
                    shown[ref] = ShownEvent(current.event.withFields(draft, fieldsFor(change.kind, change.fields), zone), syncing = true)
                }
            }
            ChangeKind.CREATE -> {
                val source = sources(change.connectionId, change.sourceId)?.takeIf { it.mapping.visible } ?: continue
                val event = change.asCreatedEvent(source.mapping.person, zone) ?: continue
                // A sync may already have fetched it (the provider made it, but its reply was lost): keep that copy.
                shown[event.ref] = ShownEvent(shown[event.ref]?.event ?: event, syncing = true)
            }
        }
    }
    return shown.values
        .filter { spanOverlaps(it.event.startSort, it.event.endSort, windowStart, windowEnd) }
        .sortedWith(compareBy<ShownEvent> { it.event.startSort }.thenBy { it.event.title })
}

/**
 * [d]'s values for [fields] only (3a design C3): a phone's change to a field the tablet didn't touch still shows. The
 * creator never changes here.
 */
internal fun StoredEvent.withFields(d: EventDraft, fields: Set<EventField>, zone: ZoneId): StoredEvent {
    val times = EventField.TIMES in fields
    val newStart = if (times) d.start else start
    val newEnd = if (times) d.end else end
    return copy(
        title = if (EventField.TITLE in fields) d.title else title,
        start = newStart,
        end = newEnd,
        forPerson = if (EventField.FOR_PERSON in fields) d.forPerson else forPerson,
        startSort = newStart.instantIn(zone).toEpochMilli(),
        endSort = newEnd.instantIn(zone).toEpochMilli(),
    )
}

/**
 * Whether this event shows on [date] in [zone]. An all-day event goes by its own dates (end exclusive; a zero-length
 * one on its start date): its sort keys are midnight in the zone it was synced in, which a zone change leaves stale
 * (4c C4).
 */
internal fun StoredEvent.isOn(date: LocalDate, zone: ZoneId): Boolean {
    val from = start
    val to = end
    if (from is EventTime.AllDay && to is EventTime.AllDay) {
        return !date.isBefore(from.date) && (date.isBefore(to.date) || date == from.date)
    }
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return spanOverlaps(startSort, endSort, dayStart, dayEnd)
}
