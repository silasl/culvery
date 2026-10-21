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
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** How long the editor tries the provider before queueing the change instead (2b-1 design D3). */
const val WRITE_ATTEMPT_MS = 10_000L

/** Hand-off §7: the toast after a delete. */
const val EVENT_DELETED = "Event deleted."

/** 2b-2 design D1: the toast after a change. */
const val CHANGES_SAVED = "Changes saved."

/** The added toast's button (9 §3.1), and what Undo says afterwards. */
const val UNDO = "Undo"

fun removed(title: String): String = "Removed $title."

fun couldNotUndo(service: String): String = "Couldn’t undo — remove it in $service on your phone."

/**
 * What the added toast's Undo removes (9 §2.3): the event made as [key], a whole series when [series], which only
 * [createdBy], the person who added it, may undo. [service] names where it went, for when Undo can't.
 */
data class AddedEvent(
    val key: String,
    val connectionId: String,
    val sourceId: String,
    val title: String,
    val series: Boolean,
    val createdBy: String,
    val service: String,
)

/** 3a design §3.12: the add/edit sheet couldn't load what it needs, so it closed. */
const val COULD_NOT_OPEN = "Couldn’t open the event — try again."

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
    data class Rejected(val problem: WriteProblem) : EditResult

    /** The PIN pad was cancelled, or the person was refused and told so by a toast. */
    data object Cancelled : EditResult

    /**
     * The event is gone, queued for deletion, or can't be changed here; or there is no master calendar to add to; or
     * Undo couldn't remove what was added, and has said so.
     */
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
    private val writeLock: CalendarWriteLock,
    private val personOf: suspend (PersonId) -> Person?,
    private val serviceOf: (providerId: String) -> String?,
    private val order: suspend () -> List<PersonId>,
    private val undone: UndoneKeys,
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
        writeLock: CalendarWriteLock,
        household: HouseholdRepository,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        undone: UndoneKeys,
    ) : this(
        store, writers, access, toaster, zone, clock, scope, loop::requestSync, Dispatchers.IO, WRITE_ATTEMPT_MS, writeLock,
        household::person, providers::displayNameOf, { household.people.first().map { it.id } }, undone,
    )

    /** The week's Update now: a sync pass at once. It changes nothing, so it asks for no PIN. */
    fun updateNow() = requestSync()

    /** Who is signed in now: the add/edit sheet's Who default and its disabled chips (2b-2 design D2). */
    internal val session: StateFlow<Identified?> get() = access.session

    /** Now, in the household zone: the add sheet's "today" and its default time. */
    internal suspend fun openedAt(): ZonedDateTime = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone.current())

    /** The added toast for [draft]: a series says how it repeats, and a first day the week can't show is named (9 §3.1). */
    private suspend fun addedLineFor(draft: EventDraft): String {
        val z = zone.current()
        val day = draft.start.instantIn(z).atZone(z).toLocalDate()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(z).toLocalDate()
        val wording = draft.recurrence?.let { choiceOf(it.rule, day) }?.wording(day)
        return addedLine(draft.title, wording, day.takeIf { weeksTo(today, day) == null })
    }

    /** A signed-in child tapped someone else's Who chip: say why it is locked (2b-2 design §4.2). */
    internal fun refuseOtherWho(name: String) = toaster.show(cannotAddForOthers(name), urgent = true)

    /** The add/edit sheet couldn't load (a store failure): it closes and says so. */
    internal fun couldNotOpen() = toaster.show(COULD_NOT_OPEN, urgent = true)

    /** The editor's Delete failed before it could ask to confirm (a store failure): the detail sheet's delete wording. */
    internal fun couldNotDelete(label: String) = toaster.show(couldNotDelete(label, WriteProblem.Tablet), urgent = true)

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

    /**
     * Tags [ref] as being for [people], written in the household's order (hand-off: "Choose who it’s for"). Adults
     * only. On an occurrence of a repeating event it tags the whole series (4e).
     */
    suspend fun assign(ref: EventRef, people: List<PersonId>): EditResult {
        val target = resolve(ref, assigning = true) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        access.authorise(
            CalendarPermissions.ASSIGN,
            reason = PinReason.Assign,
            refusal = Refusal.Toast { ASK_AN_ADULT },
        ) ?: return EditResult.Cancelled
        val kind = if (target.event.recurring) ChangeKind.ASSIGN_SERIES else ChangeKind.ASSIGN
        return write(target, kind, who = null, forPerson = tagFor(people, order()))
    }

    /**
     * Adds [draft] to the master calendar (2b-2 design §3.3) as [clientKey], the id the event keeps. The sheet chooses
     * the key, and keeps it for a Try again after the tablet failed (3a design §3.12): if the provider did make the event,
     * the retry gets it back rather than making a second. Adults may add for anyone, and repeating; a child only for themselves, once.
     * [EventDraft.createdBy] is ignored: the person who authorises is recorded. A queued create keeps its key for every
     * retry.
     */
    suspend fun create(draft: EventDraft, clientKey: String): EditResult {
        val to = master() ?: return EditResult.NotEditable
        var refusedRepeat = false
        val who = access.authorise(
            CalendarPermissions.CREATE,
            CalendarPermissions.CREATE_SELF,
            reason = PinReason.Save,
            allow = { person, granted ->
                refusedRepeat = draft.recurrence != null && !mayRepeat(granted)
                !refusedRepeat && mayCreateFor(granted, person, draft.forPerson)
            },
            refusal = Refusal.Toast { name -> if (refusedRepeat) CANT_REPEAT_FOR_CHILD else cannotAddForOthers(name) },
        ) ?: return EditResult.Cancelled
        val toSend = draft.copy(createdBy = who.person.id.value, forPersonColor = colorOf(draft.forPerson))
        val label = serviceNameOf(to.connection, serviceOf)
        val added = AddedEvent(clientKey, to.connection.id, to.source.id, draft.title, draft.recurrence != null, who.person.id.value, label)
        val line = addedLineFor(draft)
        return onAppScope(ChangeKind.CREATE, label, say = { reportAdded(added, line, it) }) {
            writeLock.withLock { attempt(to, ChangeKind.CREATE, remoteId = null, toSend, clientKey = clientKey) }
        }
    }

    /**
     * The added toast's Undo (9 §2.3): removes [added], a whole series included, even while its create waits in the
     * outbox or is on its way. Only the person who added it may, whatever their role; nobody signed in asks for a PIN.
     * The one way the tablet deletes a series.
     */
    suspend fun undoAdd(added: AddedEvent): EditResult {
        // Whoever added it could create; the test that matters is that it was them.
        access.authorise(
            CalendarPermissions.CREATE,
            CalendarPermissions.CREATE_SELF,
            reason = PinReason.Delete,
            allow = { who, _ -> who.person.id.value == added.createdBy },
            refusal = Refusal.Toast(::cannotChangeOthers),
        ) ?: return EditResult.Cancelled
        return onAppScope(ChangeKind.DELETE, added.service, say = { reportUndo(added.title, added.service, it) }) {
            val source = store.source(added.connectionId, added.sourceId) ?: return@onAppScope EditResult.NotEditable
            val to = destination(added.connectionId, source.source) ?: return@onAppScope EditResult.NotEditable
            writeLock.withLock { undo(added, to) }
        }
    }

    /**
     * Under [writeLock]: drops what is queued for the event, its create included, then deletes it. The key goes in
     * [undone] first, so a create the drain already has on its way is deleted as it lands; it stays there while a
     * create may yet land, until restart for one that never does. The mirror changes only once the service has deleted
     * it. NotEditable: it couldn't be undone.
     */
    private suspend fun undo(added: AddedEvent, to: Destination): EditResult {
        val ref = EventRef(added.connectionId, added.sourceId, added.key)
        undone.add(added.key, added.connectionId, added.sourceId, added.series)
        val create = store.pendingNow().firstOrNull { it.kind == ChangeKind.CREATE && it.ref == ref }
        store.dropCreate(ref)
        val outcome = callWriter(io, attemptMillis) {
            if (added.series) to.writer.deleteSeries(to.connection, to.source, added.key) else to.writer.delete(to.connection, to.source, added.key)
            null
        }
        // Never sent, so dropping it was enough; the key stays, as a drain may be sending it now.
        val unsent = create != null && create.attempts == 0
        if (create == null) undone.done(added.key)
        return when {
            outcome is WriteOutcome.Accepted -> {
                try {
                    if (added.series) store.applySeriesDeleted(added.connectionId, added.sourceId, added.key) else store.applyDeleted(ref)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "The provider deleted an undone event but the tablet couldn't store it (${e::class.simpleName}); the next sync will")
                }
                EditResult.Done
            }
            unsent -> EditResult.Done
            // As a delete does: queued, and hidden until it sends.
            outcome is WriteOutcome.Retry && !added.series -> {
                if (outcome.needsSignIn) store.setHealth(to.connection.id, ConnectionHealth.NeedsSignIn, clock.nowMillis())
                queue(to, ChangeKind.DELETE, added.key, draft = null, attempted = true)
            }
            else -> EditResult.NotEditable
        }
    }

    /** A saved create says [line] with an Undo of [added]; a refusal is the editor's to show. */
    private fun reportAdded(added: AddedEvent, line: String, result: EditResult) {
        if (result == EditResult.Done || result == EditResult.Queued) toaster.show(line, action = ToastAction(UNDO) { scope.launch { undoAdd(added) } })
    }

    private fun reportUndo(title: String, label: String, result: EditResult) = when (result) {
        EditResult.Done, EditResult.Queued -> toaster.show(removed(title))
        EditResult.Cancelled -> Unit
        else -> toaster.show(couldNotUndo(label), urgent = true)
    }

    /**
     * Changes [fields] of [ref] to [draft]'s values (3a design C3): the sheet's touched fields, laid over the event as
     * re-read under the write lock, so a phone's change to the others is kept, in the mirror and the queue alike.
     * Everything else the provider holds, and the event's creator, is kept: [EventDraft.createdBy] is ignored. Needs
     * edit, or edit.own on an event this person created, checked again once the PIN pad has closed. A touched Who
     * that differs follows the add rule (2b-2 design §6).
     */
    suspend fun update(ref: EventRef, draft: EventDraft, fields: Set<EventField>): EditResult {
        val target = resolve(ref) ?: return EditResult.NotEditable
        if (target.pending.any { it.kind == ChangeKind.DELETE }) return EditResult.NotEditable
        val shownPerson = asShown(target.event, target.pending, zone.current()).forPerson
        val retags = EventField.FOR_PERSON in fields && draft.forPerson != shownPerson
        val who = authoriseChange(target, PinReason.Edit, retags, draft.forPerson) ?: return EditResult.Cancelled
        return write(target, ChangeKind.UPDATE, who, edit = draft, fields = fields)
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

    /**
     * [ref] if the tablet may change it, or when [assigning] only choose who it is for: an event in the mirror, or a
     * queued create not yet synced.
     */
    private suspend fun resolve(ref: EventRef, assigning: Boolean = false): Target? {
        val source = store.source(ref.connectionId, ref.sourceId) ?: return null
        val pending = store.pendingNow().filter { it.ref == ref }
        val event = store.eventNow(ref) ?: queuedCreate(pending, source.mapping.person, zone.current()) ?: return null
        val to = destination(ref.connectionId, source.source) ?: return null
        val reason = readOnlyReason(event, source, hasWriter = true)
        val allowed = if (assigning) mayAssign(event, reason) else reason == null
        if (!allowed) return null
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
     * tells the outcome through [say] and nudges the sync loop. A store failure (disk full, corruption) becomes
     * Rejected(Tablet) instead of reaching the sheet's scope and killing the app. A save's refusal is the sheet's
     * to show; if the sheet stops waiting (it was closed), the editor toasts it instead.
     */
    private suspend fun onAppScope(
        kind: ChangeKind,
        label: String,
        say: (EditResult) -> Unit = { report(kind, label, it) },
        block: suspend () -> EditResult,
    ): EditResult {
        val job = scope.async {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save a ${kind.name} (${e::class.simpleName})")
                EditResult.Rejected(WriteProblem.Tablet)
            }
            say(result)
            // A sync pass already in flight may briefly put back the old mirror; the pass this asks for corrects it.
            requestSync()
            result
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            if (kind == ChangeKind.CREATE || kind == ChangeKind.UPDATE) {
                scope.launch { (job.await() as? EditResult.Rejected)?.let { toaster.show(couldNotSave(label, it.problem), urgent = true) } }
            }
            throw e
        }
    }

    /**
     * Under [writeLock] it reads the queue and the event afresh. A change behind a pending one for the same event
     * (a queued create included), or for a series assign the same series, is queued after it rather than sent
     * directly, where it could land first and be undone. [who] is checked again against the event's creator as it is now: it may have changed while the PIN pad
     * was up.
     */
    private suspend fun write(
        target: Target,
        kind: ChangeKind,
        who: Authorised?,
        forPerson: String? = null,
        edit: EventDraft? = null,
        fields: Set<EventField>? = null,
    ): EditResult = onAppScope(kind, serviceNameOf(target.to.connection, serviceOf)) {
        writeLock.withLock {
            val ref = target.event.ref
            val queued = store.pendingNow()
            val pending = queued.filter { it.ref == ref }
            val event = store.eventNow(ref) ?: queuedCreate(pending, target.event.sourcePerson, zone.current())
            when {
                pending.any { it.kind == ChangeKind.DELETE } ->
                    if (kind == ChangeKind.DELETE) EditResult.Queued else EditResult.NotEditable
                event == null -> if (kind == ChangeKind.DELETE) EditResult.Done else EditResult.NotEditable
                who != null && !mayChange(who.granted, Identified(who.person, who.role), createdByOf(event, pending)) -> {
                    toaster.show(cannotChangeOthers(who.person.name), urgent = true)
                    // As a refusal on the session shortcut does (2b-1 U2): the next tap asks for a PIN.
                    access.lock()
                    EditResult.Cancelled
                }
                else -> {
                    val draft = draftFor(kind, event, pending, forPerson, edit, fields)
                    val sendTo = if (kind == ChangeKind.ASSIGN_SERIES) checkNotNull(event.seriesId) else ref.remoteId
                    val ahead = queued.filter { it.ref == ref.copy(remoteId = sendTo) }
                    if (ahead.isNotEmpty()) {
                        queue(target.to, kind, sendTo, draft, attempted = false, fields = fields)
                    } else {
                        attempt(target.to, kind, sendTo, draft, fields = fields)
                    }
                }
            }
        }
    }

    /**
     * No draft for a delete. An assign, of one event or its series, sends the event as it is now, with the new person
     * and their colour. An update
     * lays the sheet's [fields] over the event as shown now (its queued changes included) and keeps its creator.
     */
    private suspend fun draftFor(
        kind: ChangeKind,
        event: StoredEvent,
        pending: List<PendingChange>,
        forPerson: String?,
        edit: EventDraft?,
        fields: Set<EventField>?,
    ): EventDraft? = when (kind) {
        ChangeKind.DELETE -> null
        ChangeKind.ASSIGN, ChangeKind.ASSIGN_SERIES -> assignDraft(event, forPerson, colorOf(forPerson))
        ChangeKind.UPDATE -> {
            val sheet = checkNotNull(edit) { "An update needs its draft" }
            val touched = checkNotNull(fields) { "An update needs its fields" }
            val shown = asShown(event, pending, zone.current()).withFields(sheet, touched, zone.current())
            EventDraft(shown.title, shown.start, shown.end, shown.forPerson, createdByOf(event, pending), colorOf(shown.forPerson))
        }
        ChangeKind.CREATE -> error("A create has no event to change")
    }

    /** The provider's event colour: only a tag of exactly one real person has one, so Family, a set or someone gone has none (9 §3.3). */
    private suspend fun colorOf(forPerson: String?): Long? =
        peopleIn(forPerson).singleOrNull()?.takeIf { it != PersonId.FAMILY }?.let { personOf(it)?.color }

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
        fields: Set<EventField>? = null,
    ): EditResult {
        val outcome = callWriter(io, attemptMillis) {
            when (kind) {
                ChangeKind.CREATE -> to.writer.create(to.connection, to.source, checkNotNull(draft), checkNotNull(clientKey))
                ChangeKind.DELETE -> {
                    to.writer.delete(to.connection, to.source, checkNotNull(remoteId))
                    null
                }
                ChangeKind.ASSIGN_SERIES -> {
                    to.writer.assignSeries(to.connection, to.source, checkNotNull(remoteId), checkNotNull(draft))
                    null
                }
                ChangeKind.UPDATE, ChangeKind.ASSIGN ->
                    to.writer.update(to.connection, to.source, checkNotNull(remoteId), checkNotNull(draft), fieldsFor(kind, fields))
            }
        }
        return when (outcome) {
            is WriteOutcome.Accepted -> {
                try {
                    store.applyAcceptedWrite(kind, to.connection.id, to.source.id, remoteId, draft?.forPerson, outcome, zone.current(), series = draft?.recurrence != null)
                    store.writeAccepted(to.connection.id, clock.nowMillis())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "The provider accepted a ${kind.name} but the tablet couldn't store it (${e::class.simpleName}); the next sync will")
                }
                EditResult.Done
            }
            is WriteOutcome.Rejected -> EditResult.Rejected(WriteProblem.Refused(outcome.reason))
            is WriteOutcome.Retry -> {
                // The reconnect chip shows at once rather than at the next sync (3a design §3.7).
                if (outcome.needsSignIn) store.setHealth(to.connection.id, ConnectionHealth.NeedsSignIn, clock.nowMillis())
                queue(to, kind, remoteId, draft, attempted = true, sent = outcome.sent, clientKey = clientKey, fields = fields)
            }
        }
    }

    /**
     * An [attempted] change waits out the first backoff; one queued behind others is due at the next pass. One that
     * never left the tablet ([sent] false) counts no attempt, so Undo knows the service can't hold it (9 §2.3).
     */
    private suspend fun queue(
        to: Destination,
        kind: ChangeKind,
        remoteId: String?,
        draft: EventDraft?,
        attempted: Boolean,
        sent: Boolean = true,
        clientKey: String? = null,
        fields: Set<EventField>? = null,
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
                attempts = if (attempted && sent) 1 else 0,
                nextAttemptMillis = if (attempted) now + backoffMillis(1) else now,
                createdMillis = now,
                clientKey = clientKey,
                // An UPDATE's touched fields, which the drain sends (3a design C3); nothing else stores fields.
                fields = fields.takeIf { kind == ChangeKind.UPDATE },
            ),
        )
        return EditResult.Queued
    }

    /** A queued change already shows (a delete hides the event), so it reads as done too. A failed delete says delete. */
    private fun report(kind: ChangeKind, label: String, result: EditResult) {
        val saved = result == EditResult.Done || result == EditResult.Queued
        when {
            result is EditResult.Rejected && kind == ChangeKind.DELETE -> toaster.show(couldNotDelete(label, result.problem), urgent = true)
            result is EditResult.Rejected && kind.isAssign -> toaster.show(couldNotSave(label, result.problem), urgent = true)
            saved && kind == ChangeKind.DELETE -> toaster.show(EVENT_DELETED)
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

/** The event as the sheets show it: its queued updates and assigns laid over it in order, each with only its fields. */
private fun asShown(event: StoredEvent, pending: List<PendingChange>, zone: ZoneId): StoredEvent =
    pending.fold(event) { shown, change ->
        val d = change.draft
        if (d != null && (change.kind == ChangeKind.UPDATE || change.kind == ChangeKind.ASSIGN)) {
            shown.withFields(d, fieldsFor(change.kind, change.fields), zone)
        } else {
            shown
        }
    }
