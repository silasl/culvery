package uk.co.siland.culvery.capability.calendar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import uk.co.siland.culvery.core.plugin.Connection

/**
 * An in-test writer. [calls] reads "create:<title>", "update:<remoteId>" or "delete:<remoteId>". Creates keep the
 * contract: the client key is the event's id, and a repeated key returns the event it made.
 */
internal class ScriptedWriter(override val providerId: String) : CalendarWriter {
    val calls = mutableListOf<String>()
    /** The drafts sent to create and update, in order. */
    val drafts = mutableListOf<EventDraft>()
    /** The events create made, by client key. */
    val created = linkedMapOf<String, RemoteEvent>()
    var failWith: Throwable? = null
    /** When set, each write waits for it: a slow network the test releases. */
    var gate: CompletableDeferred<Unit>? = null
    /** The next create makes its event, then never replies: a reply lost after the provider acted. */
    var loseNextReply = false
    /** Completes when the first write starts. */
    val entered = CompletableDeferred<Unit>()

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent {
        record("create:${draft.title}", draft)
        gate?.await()
        failWith?.let { throw it }
        val event = synchronized(this) {
            created.getOrPut(clientKey) {
                RemoteEvent(clientKey, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
            }
        }
        if (loseNextReply) {
            loseNextReply = false
            awaitCancellation()
        }
        return event
    }

    override suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent {
        record("update:$remoteId", draft)
        gate?.await()
        failWith?.let { throw it }
        return RemoteEvent(remoteId, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        record("delete:$remoteId", null)
        gate?.await()
        failWith?.let { throw it }
    }

    // Some tests run writes on Dispatchers.Default, so the lists are locked.
    private fun record(call: String, draft: EventDraft?) {
        synchronized(this) {
            calls += call
            if (draft != null) drafts += draft
        }
        entered.complete(Unit)
    }
}
