package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

internal data class SyncCall(val connectionId: String, val sourceId: String, val range: DateRange, val cursor: SyncCursor?)

/** An in-test provider (the capability may not depend on :provider:calendar-fake, even in tests). */
internal class ScriptedProvider(
    id: String,
    var sourceList: List<CalendarSource> = emptyList(),
    features: Set<Feature> = setOf(Feature.READ),
    displayName: String = id,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(id, displayName, "event", features)
    val calls = mutableListOf<SyncCall>()
    var failWith: Throwable? = null
    /** Per-source failures, by source id; they win over [failWith]. */
    var failFor: Map<String, Throwable> = emptyMap()
    /** When set, sources() throws it. */
    var sourcesFailWith: Throwable? = null
    /** sources() never returns, like a stalled socket. */
    var sourcesHang = false
    /** When set, sources() waits for it. */
    var sourcesGate: CompletableDeferred<Unit>? = null

    /** How many times sources() was called. */
    @Volatile var sourcesCalls = 0
        private set
    /** Never returns, like a stalled socket. */
    var hang = false
    /** When set, sync waits for it: a slow network the test releases. */
    var gate: CompletableDeferred<Unit>? = null
    /** Completes when the first sync call starts. */
    val entered = CompletableDeferred<Unit>()
    var events: (CalendarSource) -> List<RemoteEvent> = { emptyList() }

    /** When set, the connect screen reports this at once; otherwise it waits, like a person still choosing. */
    var connectsAs: Connection? = null

    /** How many times the connect screen entered the composition. */
    @Volatile var connectScreenShown = 0
        private set

    private var inFlight = 0

    /** The most sync calls that were running at once. */
    var maxInFlight = 0
        private set

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        LaunchedEffect(Unit) { connectScreenShown++ }
        val answer = connectsAs
        LaunchedEffect(answer) { answer?.let(onConnected) }
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> {
        synchronized(this) { sourcesCalls++ }
        if (sourcesHang) awaitCancellation()
        sourcesGate?.await()
        sourcesFailWith?.let { throw it }
        return sourceList
    }

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        // Some tests run the engine on Dispatchers.Default, so the bookkeeping is locked.
        synchronized(this) {
            calls += SyncCall(conn.id, source.id, range, cursor)
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
        }
        entered.complete(Unit)
        try {
            if (hang) awaitCancellation()
            gate?.await()
            (failFor[source.id] ?: failWith)?.let { throw it }
            return SyncResult(events(source), emptyList(), SyncCursor("k${calls.size}"), fullReplace = cursor == null)
        } finally {
            synchronized(this) { inFlight-- }
        }
    }
}

/** A real TimeoutCancellationException, as a provider's own internal withTimeout would throw. */
internal suspend fun timeoutCancellation(): TimeoutCancellationException =
    runCatching { withTimeout(1) { awaitCancellation() } }.exceptionOrNull() as TimeoutCancellationException
