package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection

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
    return (nowMillis - change.createdMillis - running).coerceAtLeast(0)
}

/** Why a change to an event that is gone is refused; lower case, to follow a dash. Providers refuse with the same words. */
const val EVENT_GONE = "the event has been deleted"

/** Why a change didn't reach the calendar. The editor never meets an unreachable provider: it queues instead. */
sealed interface WriteProblem {
    /** The tablet failed before the provider answered (a store failure): trying again may work. */
    data object Tablet : WriteProblem

    /** The provider refused for good. [reason] is lower case, to follow a dash; null when it gave none. */
    data class Refused(val reason: String?) : WriteProblem
}

/**
 * What an update of [kind] changes: an assign only who the event is for; an update its stored [fields], or every
 * field for a row queued before v4 stored them.
 */
internal fun fieldsFor(kind: ChangeKind, fields: Set<EventField>?): Set<EventField> =
    if (kind.isAssign) setOf(EventField.FOR_PERSON) else fields ?: EventField.entries.toSet()

/** What one writer call came to. */
internal sealed interface WriteOutcome {
    /** The provider took the change. [event] is what it now holds; null for a delete. */
    data class Accepted(val event: RemoteEvent?) : WriteOutcome

    /** The provider refused the change for good; [reason] is null when it gave none of its own. */
    data class Rejected(val reason: String?) : WriteOutcome

    /**
     * Try again later. [blocksConnection]: the provider didn't answer, so its other changes should wait too.
     * [needsSignIn]: the connection needs signing in again, so its health says so at once (3a design §3.7).
     * [sent]: false only when it failed before leaving the tablet, so the provider can't hold it.
     */
    data class Retry(val blocksConnection: Boolean, val needsSignIn: Boolean = false, val sent: Boolean) : WriteOutcome
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
        WriteOutcome.Rejected(e.message)
    } catch (e: TimeoutCancellationException) {
        // Rethrows if it was the caller's own timeout, not this call's.
        currentCoroutineContext().ensureActive()
        WriteOutcome.Retry(blocksConnection = true, sent = true)
    } catch (e: CancellationException) {
        // Rethrows if the caller was really cancelled; otherwise the writer leaked a stray cancellation.
        currentCoroutineContext().ensureActive()
        WriteOutcome.Retry(blocksConnection = true, sent = true)
    } catch (e: NeedsSignInException) {
        WriteOutcome.Retry(blocksConnection = true, needsSignIn = true, sent = true)
    } catch (e: UnreachableException) {
        WriteOutcome.Retry(blocksConnection = true, sent = !e.neverSent())
    } catch (e: Throwable) {
        // An Error too (review M1): logged and retried, never out of the drain.
        Log.w(TAG, "A calendar write failed unexpectedly (${e::class.simpleName}); it will be retried")
        WriteOutcome.Retry(blocksConnection = false, sent = true)
    }

/** No address looked up or reached: the tablet's own connection (5 §A.3). OkHttp keeps the cause the provider wraps. */
internal fun Throwable.isOffline(): Boolean = causes()
    .any { it is OfflineException || it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }

/** Offline with no earlier failure kept: OkHttp suppresses a route that failed mid-send before it tries the next. */
private fun Throwable.neverSent(): Boolean = isOffline() && causes().all { it.suppressed.isEmpty() }

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

/**
 * The one way the engine reads a provider (a sync, or its sources for the refresh and for connecting): on [io], under
 * [timeoutMillis]. A failure comes back as the Throwable to report, an Error included; a timeout, or a cancellation
 * the provider leaked, as [UnreachableException]. A real cancellation of the caller still propagates.
 */
internal suspend fun <T> callReader(io: CoroutineContext, timeoutMillis: Long, read: suspend () -> T): Result<T> =
    try {
        Result.success(withContext(io) { withTimeout(timeoutMillis) { read() } })
    } catch (e: TimeoutCancellationException) {
        // Rethrows if it was the caller's own timeout, not this call's.
        currentCoroutineContext().ensureActive()
        Result.failure(UnreachableException("Timed out after $timeoutMillis ms", e))
    } catch (e: CancellationException) {
        // Rethrows if the caller was really cancelled; otherwise the provider leaked a stray cancellation.
        currentCoroutineContext().ensureActive()
        Result.failure(UnreachableException("Cancelled inside the provider", e))
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Puts a write the provider accepted into the mirror: the event it now holds; for a delete, the event removed; for a
 * series assign, [forPerson] on every occurrence held. [remoteId] is the event's (or series') id before the write,
 * needed for a delete and a series assign. [completing] is the outbox row the write came from, completed in the same
 * transaction. A new [series] isn't mirrored: the service returns the series itself, which a read never lists; the sync
 * every write asks for brings its occurrences, and the outbox row it came from is completed.
 */
internal suspend fun CalendarStore.applyAcceptedWrite(
    kind: ChangeKind,
    connectionId: String,
    sourceId: String,
    remoteId: String?,
    forPerson: String?,
    accepted: WriteOutcome.Accepted,
    today: LocalDate,
    zone: ZoneId,
    completing: Long? = null,
    series: Boolean = false,
) {
    val event = accepted.event
    when {
        kind == ChangeKind.CREATE && series -> completing?.let { dropChange(it) }
        kind == ChangeKind.ASSIGN_SERIES ->
            applySeriesAssigned(connectionId, sourceId, requireNotNull(remoteId) { "A series assign needs its series" }, forPerson, completing)
        event == null ->
            applyDeleted(EventRef(connectionId, sourceId, requireNotNull(remoteId) { "An accepted delete needs its remoteId" }), today, zone, completing)
        else -> applyAccepted(connectionId, sourceId, event, today, zone, completing)
    }
}

/** A save that didn't happen, by its cause. */
fun couldNotSave(service: String, problem: WriteProblem): String = when (problem) {
    WriteProblem.Tablet -> "Couldn’t save — try again."
    is WriteProblem.Refused -> problem.reason?.let { "Couldn’t save to $service — $it." } ?: wouldNotAccept(service)
}

/** A delete that didn't happen: never "save". */
fun couldNotDelete(service: String, problem: WriteProblem): String = when (problem) {
    WriteProblem.Tablet -> "Couldn’t delete — try again."
    is WriteProblem.Refused -> problem.reason?.let { "Couldn’t delete — $it." } ?: wouldNotAccept(service)
}

private fun wouldNotAccept(service: String): String = "$service wouldn’t accept this change."

/** The editor's failure card's body: trying again helps only when the tablet failed. */
fun saveFailedBody(service: String, problem: WriteProblem): String = when (problem) {
    WriteProblem.Tablet -> "Nothing was saved. Your details are still here — try again."
    is WriteProblem.Refused -> "Nothing was saved. Your details are still here to add in $service on your phone."
}

/** One toast for everything a drain pass lost for [service]; [reasons] has one entry per change. */
internal fun changesLost(service: String, reasons: List<String?>): String = when {
    reasons.size > 1 -> "${reasons.size} changes couldn’t be saved to $service and were lost."
    reasons.single() == null -> "A change couldn’t be saved to $service and was lost."
    else -> "A change couldn’t be saved to $service and was lost — ${reasons.single()}."
}

/**
 * The name failure, repeating-event and delete copy use for [connection] (3a design D11): its provider's display name
 * ("Google Calendar"), or its label when the provider isn't installed.
 */
internal fun serviceNameOf(connection: Connection, displayNameOf: (providerId: String) -> String?): String =
    displayNameOf(connection.providerId) ?: connection.label

/** [providerId]'s display name among these providers; null when it isn't installed. */
internal fun Set<CalendarProvider>.displayNameOf(providerId: String): String? =
    firstOrNull { it.descriptor.id == providerId }?.descriptor?.displayName

/** An assign as it is sent: the event's current title, times and creator, with the new person and their colour. */
internal fun assignDraft(event: StoredEvent, forPerson: String?, forPersonColor: Long?): EventDraft =
    assignDraft(event.title, event.start, event.end, event.createdBy, forPerson, forPersonColor)

/** As [assignDraft] for the mirror's event, for one only the provider holds (3a design m3). */
internal fun assignDraft(event: RemoteEvent, forPerson: String?, forPersonColor: Long?): EventDraft =
    assignDraft(event.title, event.start, event.end, event.createdBy, forPerson, forPersonColor)

private fun assignDraft(
    title: String,
    start: EventTime,
    end: EventTime,
    createdBy: String?,
    forPerson: String?,
    forPersonColor: Long?,
): EventDraft = EventDraft(title, start, end, forPerson, createdBy, forPersonColor)

private const val TAG = "CalendarWrites"
