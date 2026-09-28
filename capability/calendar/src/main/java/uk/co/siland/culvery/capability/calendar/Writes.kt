package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Waits after the nth failed attempt: 30 s, 1 min, 2 min, then 5 min. */
val OUTBOX_BACKOFF_MS: List<Long> = listOf(30_000L, 60_000L, 120_000L, 300_000L)

internal fun backoffMillis(attempts: Int): Long = OUTBOX_BACKOFF_MS[(attempts - 1).coerceIn(0, OUTBOX_BACKOFF_MS.lastIndex)]

/** A queued change still unsent this long after it was made is dropped, with a toast. */
const val OUTBOX_MAX_AGE_MS = 48 * 60 * 60_000L

/**
 * How long [change] has waited, not counting time its connection spent waiting for sign-in (3a design D16): a
 * finished pause has already moved [PendingChange.createdMillis] forward, and [pausedSince] starts the pause still
 * running (null when none runs); a change made during that pause has aged only from when it was made.
 */
internal fun ageMillis(change: PendingChange, pausedSince: Long?, nowMillis: Long): Long {
    val running = pausedSince?.let { (nowMillis - maxOf(it, change.createdMillis)).coerceAtLeast(0) } ?: 0L
    return nowMillis - change.createdMillis - running
}

/** Why a change to an event that is gone is refused; public so providers refuse with the same words. */
const val EVENT_GONE = "The event no longer exists"

/**
 * What an update of [kind] changes: an assign only who the event is for; an update its stored [fields], or every
 * field for a row queued before v4 stored them.
 */
internal fun fieldsFor(kind: ChangeKind, fields: Set<EventField>?): Set<EventField> =
    if (kind == ChangeKind.ASSIGN) setOf(EventField.FOR_PERSON) else fields ?: EventField.entries.toSet()

/** What one writer call came to. */
internal sealed interface WriteOutcome {
    /** The provider took the change. [event] is what it now holds; null for a delete. */
    data class Accepted(val event: RemoteEvent?) : WriteOutcome

    /** The provider refused the change for good. */
    data class Rejected(val message: String) : WriteOutcome

    /** Try again later. [blocksConnection]: the provider didn't answer, so its other changes should wait too. */
    data class Retry(val blocksConnection: Boolean) : WriteOutcome
}

/**
 * The one way the engine calls a writer, for the editor and the outbox drain alike: on [io], under
 * [timeoutMillis], with every failure sorted into a [WriteOutcome]. A real cancellation of the caller still
 * propagates.
 */
internal suspend fun callWriter(io: CoroutineContext, timeoutMillis: Long, call: suspend () -> RemoteEvent?): WriteOutcome =
    try {
        WriteOutcome.Accepted(withContext(io) { withTimeout(timeoutMillis) { call() } })
    } catch (e: WriteRejectedException) {
        WriteOutcome.Rejected(e.message ?: "the calendar refused the change")
    } catch (e: TimeoutCancellationException) {
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: CancellationException) {
        // Rethrows if the caller was really cancelled; otherwise the writer leaked a stray cancellation.
        currentCoroutineContext().ensureActive()
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: NeedsSignInException) {
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: UnreachableException) {
        WriteOutcome.Retry(blocksConnection = true)
    } catch (e: Exception) {
        Log.w(TAG, "A calendar write failed unexpectedly; it will be retried", e)
        WriteOutcome.Retry(blocksConnection = false)
    }

/**
 * Puts a write the provider accepted into the mirror: the event it now holds, or, for a delete, the event
 * removed. [remoteId] is the event's id before the write, needed only for a delete. [completing] is the outbox
 * row the write came from, completed in the same transaction.
 */
internal suspend fun CalendarStore.applyAcceptedWrite(
    connectionId: String,
    sourceId: String,
    remoteId: String?,
    accepted: WriteOutcome.Accepted,
    zone: ZoneId,
    completing: Long? = null,
) {
    val event = accepted.event
    if (event == null) {
        applyDeleted(EventRef(connectionId, sourceId, requireNotNull(remoteId) { "An accepted delete needs its remoteId" }), completing)
    } else {
        applyAccepted(connectionId, sourceId, event, zone, completing)
    }
}

/** "Couldn't save to {label} — {reason}", or just "Couldn't save to {label}" when there is no reason. */
fun couldNotSave(label: String, reason: String?): String =
    if (reason == null) "Couldn't save to $label" else "Couldn't save to $label — $reason"

/** One toast for everything a drain pass dropped for [label]; [reasons] has one entry per change. */
internal fun couldNotSaveAll(label: String, reasons: List<String?>): String =
    if (reasons.size == 1) couldNotSave(label, reasons.single()) else "Couldn't save ${reasons.size} changes to $label"

/** An assign as it is sent: the event's current title, times and creator, with the new person. */
internal fun assignDraft(event: StoredEvent, forPerson: String?): EventDraft =
    EventDraft(event.title, event.start, event.end, forPerson, event.createdBy)

private const val TAG = "CalendarWrites"
