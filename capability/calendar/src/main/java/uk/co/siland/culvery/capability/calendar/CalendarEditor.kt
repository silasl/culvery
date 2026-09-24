package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** How long the editor tries the provider before queueing the change instead (2b-1 design D3). */
const val WRITE_ATTEMPT_MS = 10_000L

/** The reason shown when the tablet itself fails to save a change: nothing the provider said. */
internal const val TRY_AGAIN = "try again"

/** Hand-off §7: the toast after a delete. */
const val EVENT_DELETED = "Event deleted"

sealed interface EditResult {
    /** The provider accepted the change, and the mirror has it. */
    data object Done : EditResult

    /** The provider couldn't be reached in time: the change is queued and shows as syncing. */
    data object Queued : EditResult

    /** The provider refused the change for good, or the tablet couldn't store it; the editor has toasted why. */
    data class Rejected(val message: String) : EditResult

    /** The PIN pad was cancelled, or the person was refused and told so by a toast. */
    data object Cancelled : EditResult

    /** The event is gone, queued for deletion, or can't be changed here (another calendar, or recurring). */
    data object NotEditable : EditResult
}

/**
 * Changes master-calendar events on the tablet: authorises, tries the provider for up to [attemptMillis], and
 * queues the change in the outbox when the provider can't be reached. It reports the outcome as a toast itself,
 * and every write nudges the sync loop.
 */
@Singleton
class CalendarEditor internal constructor(
    private val store: CalendarStore,
    private val writers: Set<@JvmSuppressWildcards CalendarWriter>,
    private val access: AccessControl,
    private val toaster: Toaster,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val scope: CoroutineScope,
    private val requestSync: () -> Unit,
    private val io: CoroutineContext,
    private val attemptMillis: Long,
) {
    @Inject
    constructor(
        store: CalendarStore,
        writers: Set<@JvmSuppressWildcards CalendarWriter>,
        access: AccessControl,
        toaster: Toaster,
        zone: HouseholdZone,
        clock: WallClock,
        @ApplicationScope scope: CoroutineScope,
        loop: CalendarSyncLoop,
    ) : this(store, writers, access, toaster, zone, clock, scope, loop::requestSync, Dispatchers.IO, WRITE_ATTEMPT_MS)

    // One write at a time, so two sheets on one event can't send their changes out of order. Held only around the
    // write itself, never while the PIN pad is up.
    private val writeLock = Mutex()

    /** The delete guard, run before the confirmation appears: true when this person may delete [ref]. */
    suspend fun mayDelete(ref: EventRef): Boolean {
        val target = resolve(ref) ?: return false
        return authoriseChange(target) != null
    }

    /** Deletes [ref]. Authorises again, which passes silently while the session started by [mayDelete] lasts. */
    suspend fun delete(ref: EventRef): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        authoriseChange(target) ?: return EditResult.Cancelled
        return write(target, ChangeKind.DELETE, forPerson = null)
    }

    /** Tags [ref] as being for [person] (hand-off: "Assign to…"). Adults only. */
    suspend fun assign(ref: EventRef, person: PersonId): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        access.authorise(
            CalendarPermissions.ASSIGN,
            reason = PinReason.Assign,
            refusal = Refusal.Toast { ASK_AN_ADULT },
        ) ?: return EditResult.Cancelled
        return write(target, ChangeKind.ASSIGN, forPerson = person.value)
    }

    private class Target(
        val event: StoredEvent,
        val connection: Connection,
        val source: CalendarSource,
        val writer: CalendarWriter,
        val pending: List<PendingChange>,
    ) {
        /** Who made the event once its queued edits land; an assign never changes it. */
        val createdBy: String?
            get() {
                val edit = pending.lastOrNull { it.kind == ChangeKind.UPDATE }?.draft
                return if (edit != null) edit.createdBy else event.createdBy
            }
    }

    private suspend fun resolve(ref: EventRef): Target? {
        val event = store.eventNow(ref) ?: return null
        val source = store.source(ref.connectionId, ref.sourceId) ?: return null
        val connection = store.connectionsNow().firstOrNull { it.connection.id == ref.connectionId }?.connection ?: return null
        // A provider that declares WRITE without binding a writer fails the contract suite, and the repository logs it.
        val writer = writers.firstOrNull { it.providerId == connection.providerId } ?: return null
        if (readOnlyReason(event, source, hasWriter = true) != null) return null
        return Target(event, connection, source.source, writer, store.pendingNow().filter { it.ref == ref })
    }

    private suspend fun authoriseChange(target: Target): Authorised? {
        val createdBy = target.createdBy
        return access.authorise(
            CalendarPermissions.EDIT,
            CalendarPermissions.EDIT_OWN,
            reason = PinReason.Delete,
            allow = { who, granted -> mayChange(granted, who, createdBy) },
            refusal = Refusal.Toast(::cannotChangeOthers),
        )
    }

    /**
     * Runs on the application scope, so closing the sheet mid-write can't lose the change or its toast. Under
     * [writeLock] it reads the queue and the mirror afresh: a change behind a pending one for the same event is
     * queued after it rather than sent directly, where it could land first and be undone.
     */
    private suspend fun write(target: Target, kind: ChangeKind, forPerson: String?): EditResult {
        require(kind == ChangeKind.DELETE || kind == ChangeKind.ASSIGN) { "Creating and editing arrive with the quick-add sheet (2b-2)" }
        return scope.async {
            val result = try {
                writeLock.withLock {
                    val ref = target.event.ref
                    val pending = store.pendingNow().filter { it.ref == ref }
                    val event = store.eventNow(ref)
                    when {
                        pending.any { it.kind == ChangeKind.DELETE } ->
                            if (kind == ChangeKind.DELETE) EditResult.Queued else EditResult.NotEditable
                        event == null -> if (kind == ChangeKind.DELETE) EditResult.Done else EditResult.NotEditable
                        pending.isNotEmpty() -> queue(target, kind, draftFor(kind, event, forPerson), attempted = false)
                        else -> attempt(target, kind, event, forPerson)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A store failure (disk full, corruption) would otherwise reach the sheet's scope and kill the app.
                Log.w(TAG, "Couldn't save a $kind for ${target.event.ref}", e)
                EditResult.Rejected(TRY_AGAIN)
            }
            report(target, kind, result)
            // A sync pass already in flight may briefly put back the old mirror; the pass this asks for corrects it.
            requestSync()
            result
        }.await()
    }

    /** No draft for a delete; for an assign, the event as the mirror has it now, with the new person. */
    private fun draftFor(kind: ChangeKind, event: StoredEvent, forPerson: String?): EventDraft? =
        if (kind == ChangeKind.DELETE) null else assignDraft(event, forPerson)

    private suspend fun attempt(target: Target, kind: ChangeKind, event: StoredEvent, forPerson: String?): EditResult {
        val ref = event.ref
        val draft = draftFor(kind, event, forPerson)
        val outcome = callWriter(io, attemptMillis) {
            if (draft == null) {
                target.writer.delete(target.connection, target.source, ref.remoteId)
                null
            } else {
                target.writer.update(target.connection, target.source, ref.remoteId, draft)
            }
        }
        return when (outcome) {
            is WriteOutcome.Accepted -> {
                store.applyAcceptedWrite(ref.connectionId, ref.sourceId, ref.remoteId, outcome, zone.current())
                EditResult.Done
            }
            is WriteOutcome.Rejected -> EditResult.Rejected(outcome.message)
            is WriteOutcome.Retry -> queue(target, kind, draft, attempted = true)
        }
    }

    /** An [attempted] change waits out the first backoff; one queued behind others is due at the next pass. */
    private suspend fun queue(target: Target, kind: ChangeKind, draft: EventDraft?, attempted: Boolean): EditResult {
        val now = clock.nowMillis()
        store.enqueue(
            PendingChange(
                id = 0,
                connectionId = target.connection.id,
                sourceId = target.source.id,
                remoteId = target.event.remoteId,
                kind = kind,
                draft = draft,
                attempts = if (attempted) 1 else 0,
                nextAttemptMillis = if (attempted) now + backoffMillis(1) else now,
                createdMillis = now,
            ),
        )
        return EditResult.Queued
    }

    /** A queued delete already hides the event, so it reads as deleted too. */
    private fun report(target: Target, kind: ChangeKind, result: EditResult) {
        when {
            result is EditResult.Rejected -> toaster.show(couldNotSave(target.connection.label, result.message))
            kind == ChangeKind.DELETE && (result == EditResult.Done || result == EditResult.Queued) -> toaster.show(EVENT_DELETED)
        }
    }

    private companion object {
        const val TAG = "CalendarEditor"
    }
}
