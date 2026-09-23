package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
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
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(id, id, "event", setOf(Feature.READ))
    val calls = mutableListOf<SyncCall>()
    var failWith: Throwable? = null
    /** Per-source failures, by source id; they win over [failWith]. */
    var failFor: Map<String, Throwable> = emptyMap()
    /** Never returns, like a stalled socket. */
    var hang = false
    var events: (CalendarSource) -> List<RemoteEvent> = { emptyList() }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> = sourceList

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        calls += SyncCall(conn.id, source.id, range, cursor)
        if (hang) awaitCancellation()
        (failFor[source.id] ?: failWith)?.let { throw it }
        return SyncResult(events(source), emptyList(), SyncCursor("k${calls.size}"), fullReplace = cursor == null)
    }
}

/** A real TimeoutCancellationException, as a provider's own internal withTimeout would throw. */
internal suspend fun timeoutCancellation(): TimeoutCancellationException =
    runCatching { withTimeout(1) { awaitCancellation() } }.exceptionOrNull() as TimeoutCancellationException
