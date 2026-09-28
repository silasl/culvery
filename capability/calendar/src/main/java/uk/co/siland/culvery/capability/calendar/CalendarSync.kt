package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val writeLock: CalendarWriteLock,
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        writers: Set<@JvmSuppressWildcards CalendarWriter>,
        toaster: Toaster,
        zone: HouseholdZone,
        clock: WallClock,
        writeLock: CalendarWriteLock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS, writers, toaster, writeLock)

    // The loop's timer, a connection change and requestSync may all ask at once: one pass writes at a time.
    private val passLock = Mutex()

    // Passes in a row whose drain failed (m2); a completed drain resets it.
    @Volatile private var failedDrains = 0

    /** How long the loop waits at least after a failed drain (30 s, 1 min, 2 min, then 5 min); null after one completes. */
    internal fun drainBackoffMillis(): Long? = failedDrains.takeIf { it > 0 }?.let(::backoffMillis)

    /**
     * Delivers queued changes, then syncs each connection, and each source within it, independently. A provider
     * failure only flags that connection (with its worst source's health) and never clears its cache; a store failure
     * fails the pass, and the loop logs it and tries again.
     */
    suspend fun syncAll() = passLock.withLock {
        val window = currentWindow()
        val connections = store.connectionsNow()
        try {
            drainOutbox(connections, window.zone)
            failedDrains = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // An Error too (review M1), or its row stays due and the loop re-sends it every second. The changes stay
            // queued for a later pass; the sync must still run.
            failedDrains++
            Log.w(TAG, "The outbox drain failed ($failedDrains in a row); syncing anyway", e)
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
     * so the loop doesn't wake for them before it. Once a connection fails to answer, its other changes wait for
     * their next attempt instead of each costing a timeout. Changes nothing can deliver, changes the provider refuses,
     * and changes older than [OUTBOX_MAX_AGE_MS] are dropped, with one toast per connection, shown even if the pass
     * then fails. Age leaves out time the connection spent waiting for sign-in (3a design D16). An aged create is
     * first looked up by its key (C9): if the provider made it, it is completed and the changes behind it are sent
     * whatever their age. A dropped create takes the changes queued behind it with it (2b-2 design §5).
     */
    private suspend fun drainOutbox(connections: List<StoredConnection>, zone: ZoneId) {
        val now = clock.nowMillis()
        val byId = connections.associateBy { it.connection.id }
        // Each blocked event, with the time its earliest waiting change is next tried.
        val blockedRefs = mutableMapOf<EventRef, Long>()
        val blockedConnections = mutableSetOf<String>()
        val dropped = linkedMapOf<String, MutableList<String?>>()
        val droppedCreates = mutableSetOf<EventRef>()
        // Aged creates the provider turned out to have: the changes behind them are sent whatever their age.
        val foundCreates = mutableSetOf<EventRef>()

        suspend fun drop(change: PendingChange, label: String, reason: String?) {
            val ref = change.ref
            val count = if (change.kind == ChangeKind.CREATE && ref != null) {
                droppedCreates += ref
                // Under the editor's lock: an edit being queued behind this create goes with it, or finds it gone.
                writeLock.withLock { store.dropCreate(ref) }
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
                val aged = (ref == null || ref !in foundCreates) &&
                    ageMillis(change, stored?.needsSignInSinceMillis, now) > OUTBOX_MAX_AGE_MS
                if (aged && change.kind != ChangeKind.CREATE) {
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
                val outcome = if (aged) {
                    // C9: its reply may have been lost after the provider made it; ask before dropping it.
                    val found = callWriter(io, timeoutMillis) { writer.find(stored.connection, source.source, checkNotNull(change.clientKey)) }
                    if (found is WriteOutcome.Accepted && found.event == null) {
                        Log.w(TAG, "Dropping a queued CREATE for ${change.connectionId}: unsent for 48 hours, and never made")
                        drop(change, label, null)
                        continue
                    }
                    if (found is WriteOutcome.Accepted) complete(change, stored.connection, source.source, found, zone) else found
                } else {
                    deliver(change, stored.connection, source.source, writer, zone)
                }
                when (outcome) {
                    is WriteOutcome.Accepted -> if (aged && ref != null) foundCreates += ref
                    is WriteOutcome.Rejected -> drop(change, label, outcome.message)
                    is WriteOutcome.Retry -> {
                        val next = retryLater(change, now)
                        ref?.let { blockedRefs[it] = next }
                        if (outcome.blocksConnection) blockedConnections += change.connectionId
                        // The reconnect chip shows at once, and the queue stops ageing (3a design §3.7, D16).
                        if (outcome.needsSignIn) store.setHealth(change.connectionId, ConnectionHealth.NeedsSignIn, now)
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
                // Only the fields the sheet touched (3a design C3): a phone's change to the others is kept.
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.UPDATE, change.fields)) }
            }
            ChangeKind.ASSIGN -> {
                val remoteId = checkNotNull(change.remoteId)
                val forPerson = change.draft?.forPerson
                val color = change.draft?.forPersonColor
                val current = ref?.let { store.eventNow(it) }
                val draft = if (current != null) {
                    assignDraft(current, forPerson, color)
                } else {
                    // m3: out of the mirror's window, not necessarily gone. Never PATCH blind: it could bring a deleted event back.
                    val found = callWriter(io, timeoutMillis) { writer.find(conn, source, remoteId) }
                    if (found !is WriteOutcome.Accepted) return found
                    val event = found.event ?: return WriteOutcome.Rejected(EVENT_GONE)
                    assignDraft(event, forPerson, color)
                }
                callWriter(io, timeoutMillis) { writer.update(conn, source, remoteId, draft, fieldsFor(ChangeKind.ASSIGN, null)) }
            }
            ChangeKind.DELETE -> {
                val remoteId = checkNotNull(change.remoteId)
                callWriter(io, timeoutMillis) {
                    writer.delete(conn, source, remoteId)
                    null
                }
            }
        }
        return if (outcome is WriteOutcome.Accepted) complete(change, conn, source, outcome, zone) else outcome
    }

    /** Stores a write the provider accepted and completes its row; a store failure is retried later (C2). */
    private suspend fun complete(
        change: PendingChange,
        conn: Connection,
        source: CalendarSource,
        accepted: WriteOutcome.Accepted,
        zone: ZoneId,
    ): WriteOutcome =
        try {
            store.applyAcceptedWrite(conn.id, source.id, change.remoteId, accepted, zone, completing = change.id)
            accepted
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sent again later, which is safe: creates are idempotent by key, updates and deletes by nature.
            Log.w(TAG, "The provider accepted a queued ${change.kind} but the tablet couldn't store it; retrying later", e)
            WriteOutcome.Retry(blocksConnection = false)
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

    /**
     * The store calls sit outside the provider's call: a store failure fails the pass rather than reading as the
     * provider's health. Any Throwable from the provider (an Error included) flags only this source.
     */
    private suspend fun syncSource(
        provider: CalendarProvider,
        conn: Connection,
        source: CalendarSource,
        window: DateRange,
    ): ConnectionHealth {
        val cursor = store.cursor(conn.id, source.id, window)
        val result = callReader(io, timeoutMillis) { provider.sync(conn, source, window, cursor) }
            .getOrElse { return healthAfter(it, conn) }
        store.applySync(conn.id, source.id, window, result)
        return ConnectionHealth.Ok
    }

    /**
     * A failed read's health, logged with its cause and the connection's id: never a name or a calendar id, either of
     * which can be the account's email, and never a token.
     */
    private fun healthAfter(e: Throwable, conn: Connection): ConnectionHealth = when (e) {
        is NeedsSignInException -> {
            Log.w(TAG, "${conn.id}: a source needs signing in again", e)
            ConnectionHealth.NeedsSignIn
        }
        is UnreachableException -> {
            Log.w(TAG, "${conn.id}: a source is unreachable", e)
            ConnectionHealth.Unreachable
        }
        else -> {
            Log.e(TAG, "${conn.id}: a source failed", e)
            ConnectionHealth.Error(e.message ?: e.javaClass.simpleName)
        }
    }

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
