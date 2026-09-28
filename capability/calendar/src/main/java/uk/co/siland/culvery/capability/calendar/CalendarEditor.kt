package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
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

/** 2b-2 design D1: the toasts after a save. */
const val EVENT_ADDED = "Event added"
const val CHANGES_SAVED = "Changes saved"

sealed interface EditResult {
    /** The provider accepted the change. The mirror has it, or the next sync brings it (2b-2 design §3.4). */
    data object Done : EditResult

    /** The provider couldn't be reached in time: the change is queued and shows as syncing. */
    data object Queued : EditResult

    /**
     * The provider refused the change for good, or the tablet failed before the provider accepted it. For a delete
     * or an assign the editor has toasted why. For a save the sheet shows it; if the sheet has closed, the editor
     * toasts it.
     */
    data class Rejected(val message: String) : EditResult

    /** The PIN pad was cancelled, or the person was refused and told so by a toast. */
    data object Cancelled : EditResult

    /** The event is gone, queued for deletion, or can't be changed here; or there is no master calendar to add to. */
    data object NotEditable : EditResult
}

/**
 * Adds and changes master-calendar events on the tablet: authorises, tries the provider for up to [attemptMillis],
 * and queues the change in the outbox when the provider can't be reached. It reports outcomes as toasts itself,
 * and every write nudges the sync loop. A queued create counts as an event (2b-2 design D6): its changes queue
 * behind it, and in-order delivery with idempotent creates makes that correct.
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
    private val newKey: () -> String = ::newClientKey,
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

    /** Who is signed in now: the add/edit sheet's Who default and its disabled chips (2b-2 design D2). */
    internal val session: StateFlow<Identified?> get() = access.session

    /** Now, in the household zone: the add sheet's "today" and its default time. */
    internal suspend fun openedAt(): ZonedDateTime = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone.current())

    /** A signed-in child tapped someone else's Who chip: say why it is disabled (2b-2 design §4.2). */
    internal fun refuseOtherWho(name: String) = toaster.show(cannotAddForOthers(name))

    /** The delete guard, run before the confirmation appears: true when this person may delete [ref]. */
    suspend fun mayDelete(ref: EventRef): Boolean {
        val target = resolve(ref) ?: return false
        return authoriseChange(target, PinReason.Delete) != null
    }

    /** Deletes [ref]. Authorises again, which passes silently while the session started by [mayDelete] lasts. */
    suspend fun delete(ref: EventRef): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        val who = authoriseChange(target, PinReason.Delete) ?: return EditResult.Cancelled
        return write(target, ChangeKind.DELETE, who)
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
        return write(target, ChangeKind.ASSIGN, who = null, forPerson = person.value)
    }

    /**
     * Adds [draft] to the master calendar (2b-2 design §3.3). Adults may add for anyone; a child only for themselves.
     * [EventDraft.createdBy] is ignored: the person who authorises is recorded. Each call chooses a new client key,
     * which a queued create keeps for every retry.
     */
    suspend fun create(draft: EventDraft): EditResult {
        val to = master() ?: return EditResult.NotEditable
        val who = access.authorise(
            CalendarPermissions.CREATE,
            CalendarPermissions.CREATE_SELF,
            reason = PinReason.Save,
            allow = { person, granted -> mayCreateFor(granted, person, draft.forPerson) },
            refusal = Refusal.Toast(::cannotAddForOthers),
        ) ?: return EditResult.Cancelled
        val key = newKey()
        val toSend = draft.copy(createdBy = who.person.id.value)
        return onAppScope(ChangeKind.CREATE, to.connection.label) {
            writeLock.withLock { attempt(to, ChangeKind.CREATE, remoteId = null, toSend, clientKey = key) }
        }
    }

    /**
     * Changes [ref]'s title, times and who to [draft]'s (2b-2 design D7); everything else the provider holds is kept.
     * The event's creator is kept too: [EventDraft.createdBy] is ignored. Needs edit, or edit.own on an event this
     * person created, checked again once the PIN pad has closed. A change of who follows the add rule (§6).
     */
    suspend fun update(ref: EventRef, draft: EventDraft): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        val retags = draft.forPerson != forPersonOf(target.event, target.pending)
        val who = authoriseChange(target, PinReason.Edit, retags, draft.forPerson) ?: return EditResult.Cancelled
        return write(target, ChangeKind.UPDATE, who, edit = draft)
    }

    /** Where a change goes: a connection, one of its sources, and the writer for its provider. */
    private class Destination(val connection: Connection, val source: CalendarSource, val writer: CalendarWriter)

    private class Target(val event: StoredEvent, val to: Destination, val pending: List<PendingChange>)

    /** The writable master calendar, where new events go (2b-2 design §6); null if there is none, or no writer. */
    private suspend fun master(): Destination? {
        val master = writableMaster(store.master().first(), store.connectionsNow(), writers.map { it.providerId }.toSet())
            ?: return null
        return destination(master.connection.id, master.source.source)
    }

    private suspend fun destination(connectionId: String, source: CalendarSource): Destination? {
        val connection = store.connectionsNow().firstOrNull { it.connection.id == connectionId }?.connection ?: return null
        // A provider that declares WRITE without binding a writer fails the contract suite, and the repository logs it.
        val writer = writers.firstOrNull { it.providerId == connection.providerId } ?: return null
        return Destination(connection, source, writer)
    }

    /** [ref] if the tablet may change it: an event in the mirror, or a queued create not yet synced. */
    private suspend fun resolve(ref: EventRef): Target? {
        val source = store.source(ref.connectionId, ref.sourceId) ?: return null
        val pending = store.pendingNow().filter { it.ref == ref }
        val event = store.eventNow(ref) ?: queuedCreate(pending, source.mapping.person, zone.current()) ?: return null
        val to = destination(ref.connectionId, source.source) ?: return null
        if (readOnlyReason(event, source, hasWriter = true) != null) return null
        return Target(event, to, pending)
    }

    /**
     * Edit, or edit.own on an event this person created. When the change [retags] the event to [forPerson], the
     * person also needs assign or the add rule, and a refusal on that says so instead.
     */
    private suspend fun authoriseChange(
        target: Target,
        reason: PinReason,
        retags: Boolean = false,
        forPerson: String? = null,
    ): Authorised? {
        val createdBy = createdByOf(target.event, target.pending)
        var refusedWho = false
        return access.authorise(
            *(if (retags) CHANGE_AND_RETAG else CHANGE),
            reason = reason,
            allow = { who, granted ->
                val mayEdit = mayChange(granted, who, createdBy)
                refusedWho = mayEdit && retags && !mayRetag(granted, who, forPerson)
                mayEdit && !refusedWho
            },
            refusal = Refusal.Toast { name -> if (refusedWho) cannotAddForOthers(name) else cannotChangeOthers(name) },
        )
    }

    /**
     * Runs [block] on the application scope, so closing the sheet mid-write can't lose the change or its toast, then
     * reports the outcome and nudges the sync loop. A store failure (disk full, corruption) becomes
     * Rejected(TRY_AGAIN) instead of reaching the sheet's scope and killing the app. A save's refusal is the sheet's
     * to show; if the sheet stops waiting (it was closed), the editor toasts it instead.
     */
    private suspend fun onAppScope(kind: ChangeKind, label: String, block: suspend () -> EditResult): EditResult {
        val job = scope.async {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save a $kind", e)
                EditResult.Rejected(TRY_AGAIN)
            }
            report(kind, label, result)
            // A sync pass already in flight may briefly put back the old mirror; the pass this asks for corrects it.
            requestSync()
            result
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            if (kind == ChangeKind.CREATE || kind == ChangeKind.UPDATE) {
                scope.launch { (job.await() as? EditResult.Rejected)?.let { toaster.show(couldNotSave(label, it.message)) } }
            }
            throw e
        }
    }

    /**
     * Under [writeLock] it reads the queue and the event afresh. A change behind a pending one for the same event
     * (a queued create included) is queued after it rather than sent directly, where it could land first and be
     * undone. [who] is checked again against the event's creator as it is now: it may have changed while the PIN pad
     * was up.
     */
    private suspend fun write(
        target: Target,
        kind: ChangeKind,
        who: Authorised?,
        forPerson: String? = null,
        edit: EventDraft? = null,
    ): EditResult = onAppScope(kind, target.to.connection.label) {
        writeLock.withLock {
            val ref = target.event.ref
            val pending = store.pendingNow().filter { it.ref == ref }
            val event = store.eventNow(ref) ?: queuedCreate(pending, target.event.sourcePerson, zone.current())
            when {
                pending.any { it.kind == ChangeKind.DELETE } ->
                    if (kind == ChangeKind.DELETE) EditResult.Queued else EditResult.NotEditable
                event == null -> if (kind == ChangeKind.DELETE) EditResult.Done else EditResult.NotEditable
                who != null && !mayChange(who.granted, Identified(who.person, who.role), createdByOf(event, pending)) -> {
                    toaster.show(cannotChangeOthers(who.person.name))
                    // As a refusal on the session shortcut does (2b-1 U2): the next tap asks for a PIN.
                    access.lock()
                    EditResult.Cancelled
                }
                else -> {
                    val draft = draftFor(kind, event, pending, forPerson, edit)
                    if (pending.isNotEmpty()) {
                        queue(target.to, kind, ref.remoteId, draft, attempted = false)
                    } else {
                        attempt(target.to, kind, ref.remoteId, draft)
                    }
                }
            }
        }
    }

    /**
     * No draft for a delete. An assign sends the event as it is now, with the new person. An update sends the
     * sheet's title, times and who, with the event's own creator.
     */
    private fun draftFor(
        kind: ChangeKind,
        event: StoredEvent,
        pending: List<PendingChange>,
        forPerson: String?,
        edit: EventDraft?,
    ): EventDraft? = when (kind) {
        ChangeKind.DELETE -> null
        ChangeKind.ASSIGN -> assignDraft(event, forPerson)
        ChangeKind.UPDATE -> checkNotNull(edit) { "An update needs its draft" }.copy(createdBy = createdByOf(event, pending))
        ChangeKind.CREATE -> error("A create has no event to change")
    }

    /**
     * Tries the provider once. A create sends its [clientKey], and a queued create keeps it: if the provider did make
     * the event, the retry gets that event back, not a second. Once the provider has accepted, the change is done
     * even if the tablet then fails to store it; the sync every write asks for mirrors it (2b-2 design §3.4).
     */
    private suspend fun attempt(
        to: Destination,
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft?,
        clientKey: String? = null,
    ): EditResult {
        val outcome = callWriter(io, attemptMillis) {
            when (kind) {
                ChangeKind.CREATE -> to.writer.create(to.connection, to.source, checkNotNull(draft), checkNotNull(clientKey))
                ChangeKind.DELETE -> {
                    to.writer.delete(to.connection, to.source, checkNotNull(remoteId))
                    null
                }
                ChangeKind.UPDATE, ChangeKind.ASSIGN ->
                    to.writer.update(to.connection, to.source, checkNotNull(remoteId), checkNotNull(draft), fieldsFor(kind, null))
            }
        }
        return when (outcome) {
            is WriteOutcome.Accepted -> {
                try {
                    store.applyAcceptedWrite(to.connection.id, to.source.id, remoteId, outcome, zone.current())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "The provider accepted a $kind but the tablet couldn't store it; the next sync will", e)
                }
                EditResult.Done
            }
            is WriteOutcome.Rejected -> EditResult.Rejected(outcome.message)
            is WriteOutcome.Retry -> queue(to, kind, remoteId, draft, attempted = true, clientKey = clientKey)
        }
    }

    /** An [attempted] change waits out the first backoff; one queued behind others is due at the next pass. */
    private suspend fun queue(
        to: Destination,
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft?,
        attempted: Boolean,
        clientKey: String? = null,
    ): EditResult {
        val now = clock.nowMillis()
        store.enqueue(
            PendingChange(
                id = 0,
                connectionId = to.connection.id,
                sourceId = to.source.id,
                remoteId = remoteId,
                kind = kind,
                draft = draft,
                attempts = if (attempted) 1 else 0,
                nextAttemptMillis = if (attempted) now + backoffMillis(1) else now,
                createdMillis = now,
                clientKey = clientKey,
            ),
        )
        return EditResult.Queued
    }

    /** A queued change already shows (a delete hides the event), so it reads as done too. */
    private fun report(kind: ChangeKind, label: String, result: EditResult) {
        val saved = result == EditResult.Done || result == EditResult.Queued
        when {
            result is EditResult.Rejected && (kind == ChangeKind.DELETE || kind == ChangeKind.ASSIGN) ->
                toaster.show(couldNotSave(label, result.message))
            saved && kind == ChangeKind.DELETE -> toaster.show(EVENT_DELETED)
            saved && kind == ChangeKind.CREATE -> toaster.show(EVENT_ADDED)
            saved && kind == ChangeKind.UPDATE -> toaster.show(CHANGES_SAVED)
        }
    }

    private companion object {
        const val TAG = "CalendarEditor"

        /** What a change asks for; one that changes who also counts assign and the create permissions (§6). */
        val CHANGE = arrayOf(CalendarPermissions.EDIT, CalendarPermissions.EDIT_OWN)
        val CHANGE_AND_RETAG = CHANGE + arrayOf(CalendarPermissions.ASSIGN, CalendarPermissions.CREATE, CalendarPermissions.CREATE_SELF)
    }
}

/** A queued create stands in for the mirror's row until it syncs. */
private fun queuedCreate(pending: List<PendingChange>, sourcePerson: PersonId, zone: ZoneId): StoredEvent? =
    pending.firstNotNullOfOrNull { it.asCreatedEvent(sourcePerson, zone) }

/** Who made the event once its queued edits land; an assign never changes it. */
private fun createdByOf(event: StoredEvent, pending: List<PendingChange>): String? {
    val edit = pending.lastOrNull { it.kind == ChangeKind.UPDATE }?.draft
    return if (edit != null) edit.createdBy else event.createdBy
}

/** Who the event is for once its queued edits and assigns land: what the edit sheet showed. */
private fun forPersonOf(event: StoredEvent, pending: List<PendingChange>): String? {
    val last = pending.lastOrNull { it.kind == ChangeKind.UPDATE || it.kind == ChangeKind.ASSIGN }?.draft
    return if (last != null) last.forPerson else event.forPerson
}
