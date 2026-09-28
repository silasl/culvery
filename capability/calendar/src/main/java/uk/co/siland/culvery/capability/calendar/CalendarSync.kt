package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

const val SYNC_PAST_DAYS = 1L
const val SYNC_FUTURE_DAYS = 14L
const val PROVIDER_TIMEOUT_MS = 60_000L

@Singleton
class CalendarSync internal constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
    private val writers: Set<@JvmSuppressWildcards CalendarWriter>,
    private val toaster: Toaster,
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        writers: Set<@JvmSuppressWildcards CalendarWriter>,
        toaster: Toaster,
        zone: HouseholdZone,
        clock: WallClock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS, writers, toaster)

    // The loop's timer, a connection change and requestSync may all ask at once: one pass writes at a time.
    private val passLock = Mutex()

    /**
     * Delivers queued changes, then syncs each connection, and each source within it, independently. A
     * failure only flags that connection (with its worst source's health) and never clears its cache.
     */
    suspend fun syncAll() = passLock.withLock {
        val window = currentWindow()
        val connections = store.connectionsNow()
        try {
            drainOutbox(connections, window.zone)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The changes stay queued for the next pass; the sync must still run.
            Log.w(TAG, "The outbox drain failed; syncing anyway", e)
        }
        connections.forEach { sync(it.connection, window) }
    }

    private suspend fun currentWindow(): DateRange {
        val z = zone.current()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(z).toLocalDate()
        return DateRange(today.minusDays(SYNC_PAST_DAYS), today.plusDays(SYNC_FUTURE_DAYS + 1), z)
    }

    /**
     * Delivers queued changes in the order they were made. An event's later changes wait while an earlier one is
     * waiting or has just failed, so they can't land first and be undone; they are rescheduled to its next attempt,
     * so the loop doesn't wake for them before it. Once a connection fails to answer, its
     * other changes wait for their next attempt instead of each costing a timeout. Changes nothing can deliver,
     * changes the provider refuses, and changes older than [OUTBOX_MAX_AGE_MS] are dropped, with one toast per
     * connection, shown even if the pass then fails. A dropped create takes the changes queued behind it with it:
     * there is no event for them to change (2b-2 design §5).
     */
    private suspend fun drainOutbox(connections: List<StoredConnection>, zone: ZoneId) {
        val now = clock.nowMillis()
        val byId = connections.associateBy { it.connection.id }
        // Each blocked event, with the time its earliest waiting change is next tried.
        val blockedRefs = mutableMapOf<EventRef, Long>()
        val blockedConnections = mutableSetOf<String>()
        val dropped = linkedMapOf<String, MutableList<String?>>()
        val droppedCreates = mutableSetOf<EventRef>()

        suspend fun drop(change: PendingChange, label: String, reason: String?) {
            val ref = change.ref
            val count = if (change.kind == ChangeKind.CREATE && ref != null) {
                droppedCreates += ref
                store.dropCreate(ref)
            } else {
                store.dropChange(change.id)
                1
            }
            val reasons = dropped.getOrPut(label) { mutableListOf() }
            reasons += reason
            // The changes that went with a create have no reason of their own.
            repeat(count - 1) { reasons += null }
        }

        try {
            for (change in store.pendingNow()) {
                val ref = change.ref
                val stored = byId[change.connectionId]
                val label = stored?.connection?.label ?: REMOVED_CALENDAR
                // Already deleted, and counted, with its create.
                if (ref != null && ref in droppedCreates) continue
                val blockedUntil = ref?.let(blockedRefs::get)
                if (blockedUntil != null) {
                    // Not an attempt, so it costs no backoff step.
                    if (change.nextAttemptMillis < blockedUntil) store.reschedule(change.id, change.attempts, blockedUntil)
                    continue
                }
                if (now - change.createdMillis > OUTBOX_MAX_AGE_MS) {
                    Log.w(TAG, "Dropping a queued ${change.kind} for ${change.connectionId}: unsent for 48 hours")
                    drop(change, label, null)
                    continue
                }
                if (change.nextAttemptMillis > now) {
                    ref?.let { blockedRefs[it] = change.nextAttemptMillis }
                    continue
                }
                if (change.connectionId in blockedConnections) {
                    val next = retryLater(change, now)
                    ref?.let { blockedRefs[it] = next }
                    continue
                }
                val source = store.source(change.connectionId, change.sourceId)
                val writer = stored?.let { s -> writers.firstOrNull { it.providerId == s.connection.providerId } }
                if (stored == null || source == null || writer == null || !change.isComplete()) {
                    Log.w(TAG, "Dropping a queued ${change.kind} for ${change.connectionId}/${change.sourceId}: nothing can deliver it")
                    drop(change, label, null)
                    continue
                }
                when (val outcome = deliver(change, stored.connection, source.source, writer, zone)) {
                    is WriteOutcome.Accepted -> Unit
                    is WriteOutcome.Rejected -> drop(change, label, outcome.message)
                    is WriteOutcome.Retry -> {
                        val next = retryLater(change, now)
                        ref?.let { blockedRefs[it] = next }
                        if (outcome.blocksConnection) blockedConnections += change.connectionId
                    }
                }
            }
        } finally {
            // Those changes are already gone, so they are told even when a later change fails the pass.
            dropped.forEach { (label, reasons) -> toaster.show(couldNotSaveAll(label, reasons)) }
        }
    }

    /** Whether the change carries what its kind needs; one that doesn't can never be sent. */
    private fun PendingChange.isComplete(): Boolean = when (kind) {
        ChangeKind.CREATE -> draft != null && clientKey != null
        ChangeKind.UPDATE -> remoteId != null && draft != null
        ChangeKind.ASSIGN, ChangeKind.DELETE -> remoteId != null
    }

    /** Sends one change and, when the provider accepts it, applies the result to the mirror and completes it. */
    private suspend fun deliver(
        change: PendingChange,
        conn: Connection,
        source: CalendarSource,
        writer: CalendarWriter,
        zone: ZoneId,
    ): WriteOutcome {
        // Checked outside callWriter, so a malformed change fails the drain instead of being retried as a write.
        val ref = change.ref
        val outcome = when (change.kind) {
            ChangeKind.CREATE -> {
                val draft = checkNotNull(change.draft)
                val key = checkNotNull(change.clientKey)
                // The key makes a retry safe: if an earlier attempt did create the event, the provider returns it.
                callWriter(io, timeoutMillis) { writer.create(conn, source, draft, key) }
            }
            ChangeKind.UPDATE -> {
                val remoteId = checkNotNull(change.remoteId)
                val draft = checkNotNull(change.draft)
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.UPDATE, null)) }
            }
            ChangeKind.ASSIGN -> {
                // Sent as the event is now, so a title or time changed elsewhere since it was queued is kept.
                val current = ref?.let { store.eventNow(it) } ?: return WriteOutcome.Rejected(EVENT_GONE)
                val draft = assignDraft(current, change.draft?.forPerson)
                callWriter(io, timeoutMillis) { writer.update(conn, source, current.remoteId, draft, fieldsFor(ChangeKind.ASSIGN, null)) }
            }
            ChangeKind.DELETE -> {
                val remoteId = checkNotNull(change.remoteId)
                callWriter(io, timeoutMillis) {
                    writer.delete(conn, source, remoteId)
                    null
                }
            }
        }
        if (outcome is WriteOutcome.Accepted) {
            store.applyAcceptedWrite(conn.id, source.id, change.remoteId, outcome, zone, completing = change.id)
        }
        return outcome
    }

    /** Counts a failed attempt and returns when the change will next be tried. */
    private suspend fun retryLater(change: PendingChange, now: Long): Long {
        val attempts = change.attempts + 1
        val next = now + backoffMillis(attempts)
        store.reschedule(change.id, attempts, next)
        return next
    }

    private suspend fun sync(conn: Connection, window: DateRange) {
        val provider = providers.firstOrNull { it.descriptor.id == conn.providerId }
        if (provider == null) {
            store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"), clock.nowMillis())
            return
        }
        var worst: ConnectionHealth = ConnectionHealth.Ok
        for (stored in store.visibleSourcesFor(conn.id)) {
            val health = syncSource(provider, conn, stored.source, window)
            if (health.severity() > worst.severity()) worst = health
        }
        if (worst == ConnectionHealth.Ok) {
            store.markSynced(conn.id, clock.nowMillis())
        } else {
            store.setHealth(conn.id, worst, clock.nowMillis())
        }
    }

    private suspend fun syncSource(
        provider: CalendarProvider,
        conn: Connection,
        source: CalendarSource,
        window: DateRange,
    ): ConnectionHealth =
        try {
            val cursor = store.cursor(conn.id, source.id, window)
            val result = callProvider { provider.sync(conn, source, window, cursor) }
            store.applySync(conn.id, source.id, window, result)
            ConnectionHealth.Ok
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${conn.label} / ${source.name}: timed out", e)
            ConnectionHealth.Unreachable
        } catch (e: CancellationException) {
            // Rethrows if this sync was really cancelled; otherwise the provider leaked a stray cancellation.
            currentCoroutineContext().ensureActive()
            ConnectionHealth.Unreachable
        } catch (e: NeedsSignInException) {
            ConnectionHealth.NeedsSignIn
        } catch (e: UnreachableException) {
            ConnectionHealth.Unreachable
        } catch (e: Exception) {
            ConnectionHealth.Error(e.message ?: e.javaClass.simpleName)
        }

    /** Every provider read goes through here; writes go through callWriter. */
    private suspend fun <T> callProvider(block: suspend () -> T): T =
        withContext(io) { withTimeout(timeoutMillis) { block() } }

    private fun ConnectionHealth.severity(): Int = when (this) {
        ConnectionHealth.NeedsSignIn -> 3
        ConnectionHealth.Unreachable -> 2
        is ConnectionHealth.Error -> 1
        ConnectionHealth.Ok -> 0
    }

    private companion object {
        const val TAG = "CalendarSync"

        /** The toast's label for a change whose connection has been removed. */
        const val REMOVED_CALENDAR = "a removed calendar"
    }
}
