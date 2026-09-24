package uk.co.siland.culvery.capability.calendar

import kotlinx.coroutines.CompletableDeferred
import uk.co.siland.culvery.core.plugin.Connection

/** An in-test writer. [calls] reads "create:<title>", "update:<remoteId>" or "delete:<remoteId>". */
internal class ScriptedWriter(override val providerId: String) : CalendarWriter {
    val calls = mutableListOf<String>()
    /** The drafts sent to create and update, in order. */
    val drafts = mutableListOf<EventDraft>()
    var failWith: Throwable? = null
    /** When set, each write waits for it: a slow network the test releases. */
    var gate: CompletableDeferred<Unit>? = null
    /** Completes when the first write starts. */
    val entered = CompletableDeferred<Unit>()
    private var next = 0

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent {
        record("create:${draft.title}", draft)
        gate?.await()
        failWith?.let { throw it }
        return RemoteEvent("new-${++next}", draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
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
