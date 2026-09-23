package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
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
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        zone: HouseholdZone,
        clock: WallClock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS)

    /**
     * Syncs each connection, and each source within it, independently. A failure only flags that connection
     * (with its worst source's health) and never clears its cache.
     */
    suspend fun syncAll() {
        val window = currentWindow()
        store.connectionsNow().forEach { sync(it.connection, window) }
    }

    private suspend fun currentWindow(): DateRange {
        val z = zone.current()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(z).toLocalDate()
        return DateRange(today.minusDays(SYNC_PAST_DAYS), today.plusDays(SYNC_FUTURE_DAYS + 1), z)
    }

    private suspend fun sync(conn: Connection, window: DateRange) {
        val provider = providers.firstOrNull { it.descriptor.id == conn.providerId }
        if (provider == null) {
            store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"))
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
            store.setHealth(conn.id, worst)
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

    /** Every provider call (sync now, sources() when Plan 4 refreshes them) goes through here. */
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
    }
}
