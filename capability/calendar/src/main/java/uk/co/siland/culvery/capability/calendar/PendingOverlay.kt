package uk.co.siland.culvery.capability.calendar

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
 * values; an assign shows only its person. A create adds its draft, on a visible source, under its client key, so
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

private fun StoredEvent.withDraft(d: EventDraft, zone: ZoneId) = copy(
    title = d.title,
    start = d.start,
    end = d.end,
    forPerson = d.forPerson,
    createdBy = d.createdBy,
    startSort = d.start.instantIn(zone).toEpochMilli(),
    endSort = d.end.instantIn(zone).toEpochMilli(),
)
