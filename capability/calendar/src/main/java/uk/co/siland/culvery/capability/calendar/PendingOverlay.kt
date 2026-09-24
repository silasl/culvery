package uk.co.siland.culvery.capability.calendar

import java.time.ZoneId

/** A mirrored event as the UI shows it, with any queued change laid over it. */
internal data class ShownEvent(val event: StoredEvent, val syncing: Boolean)

/**
 * Lays [pending] changes over [events] in queue order. A delete hides the event. An update shows the draft's
 * values; an assign shows only its person. A create adds the draft as a new event on a visible source; it has no
 * remoteId yet, so its ref uses "pending-{id}". Only events overlapping [windowStart, windowEnd) are returned, in
 * start order.
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
            ChangeKind.UPDATE -> {
                val ref = change.ref ?: continue
                val current = shown[ref] ?: continue
                if (draft != null) shown[ref] = ShownEvent(current.event.withDraft(draft, zone), syncing = true)
            }
            ChangeKind.ASSIGN -> {
                val ref = change.ref ?: continue
                val current = shown[ref] ?: continue
                if (draft != null) shown[ref] = ShownEvent(current.event.copy(forPerson = draft.forPerson), syncing = true)
            }
            ChangeKind.CREATE -> {
                val source = sources(change.connectionId, change.sourceId)?.takeIf { it.mapping.visible } ?: continue
                if (draft == null) continue
                val event = StoredEvent(
                    connectionId = change.connectionId,
                    sourceId = change.sourceId,
                    remoteId = "pending-${change.id}",
                    title = draft.title,
                    start = draft.start,
                    end = draft.end,
                    recurring = false,
                    forPerson = draft.forPerson,
                    createdBy = draft.createdBy,
                    sourcePerson = source.mapping.person,
                    startSort = draft.start.instantIn(zone).toEpochMilli(),
                    endSort = draft.end.instantIn(zone).toEpochMilli(),
                )
                shown[event.ref] = ShownEvent(event, syncing = true)
            }
        }
    }
    return shown.values
        .filter { spanOverlaps(it.event.startSort, it.event.endSort, windowStart, windowEnd) }
        .sortedWith(compareBy<ShownEvent> { it.event.startSort }.thenBy { it.event.title })
}

private fun StoredEvent.withDraft(d: EventDraft, zone: ZoneId) = copy(
    title = d.title,
    start = d.start,
    end = d.end,
    forPerson = d.forPerson,
    createdBy = d.createdBy,
    startSort = d.start.instantIn(zone).toEpochMilli(),
    endSort = d.end.instantIn(zone).toEpochMilli(),
)
