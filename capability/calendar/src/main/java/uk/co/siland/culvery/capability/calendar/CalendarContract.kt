package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

sealed interface EventTime {
    data class Timed(val instant: Instant) : EventTime
    data class AllDay(val date: LocalDate) : EventTime
}

/** The instant this time starts at; an all-day date starts at midnight in [zone]. */
fun EventTime.instantIn(zone: ZoneId): Instant = when (this) {
    is EventTime.Timed -> instant
    is EventTime.AllDay -> date.atStartOfDay(zone).toInstant()
}

/** Half-open overlap of [start, end) with [windowStart, windowEnd); a zero-length span counts if it starts inside. */
fun spanOverlaps(start: Long, end: Long, windowStart: Long, windowEnd: Long): Boolean =
    start < windowEnd && (end > windowStart || start >= windowStart)

data class CalendarSource(val id: String, val name: String, val writable: Boolean)

/**
 * One concrete occurrence from a provider.
 *
 * [end] is exclusive: an all-day event on 23 September has start AllDay(23 Sep) and end AllDay(24 Sep),
 * as in Google Calendar and iCalendar. [start] and [end] are both Timed or both AllDay.
 * Recurring events arrive already expanded, one RemoteEvent per occurrence, each with its own [remoteId],
 * and [recurring] set. [forPerson] and [createdBy] are household PersonId values when the provider stores them.
 */
data class RemoteEvent(
    val remoteId: String,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val recurring: Boolean,
    val forPerson: String? = null,
    val createdBy: String? = null,
)

/** Local dates in the household's [zone]; [endExclusive] is the first day not included. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate, val zone: ZoneId) {
    init {
        require(endExclusive.isAfter(start)) { "DateRange must end after it starts: $start..$endExclusive" }
    }

    val startInstant: Instant get() = start.atStartOfDay(zone).toInstant()
    val endInstant: Instant get() = endExclusive.atStartOfDay(zone).toInstant()

    fun overlaps(start: EventTime, end: EventTime): Boolean = spanOverlaps(
        start.instantIn(zone).toEpochMilli(),
        end.instantIn(zone).toEpochMilli(),
        startInstant.toEpochMilli(),
        endInstant.toEpochMilli(),
    )
}

@JvmInline
value class SyncCursor(val value: String)

/**
 * [fullReplace] = true means [upserts] is the complete set for this source and range: drop everything else.
 * It must be true whenever the request's cursor was null, and false for an incremental result.
 */
data class SyncResult(
    val upserts: List<RemoteEvent>,
    val removedIds: List<String>,
    val cursor: SyncCursor?,
    val fullReplace: Boolean,
)

/**
 * What the tablet asks a provider to write. [end] is exclusive, as in [RemoteEvent]. [forPerson] and [createdBy]
 * are household PersonId values ("family" allowed) that the provider stores with the event (Google:
 * extendedProperties.private). Names are never written.
 */
data class EventDraft(
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val forPerson: String?,
    val createdBy: String?,
)

class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * A calendar service. Implementations live in :provider:calendar-* and bind themselves with
 * `@Binds @IntoSet`. Behaviour is pinned by CalendarProviderContractTest in :capability:calendar-testkit.
 *
 * - [sources] and [sync] throw only [NeedsSignInException] (auth) or [UnreachableException] (network).
 * - They must be main-safe and cancellable: no uninterruptible blocking I/O. The sync engine calls them on
 *   Dispatchers.IO under a 60 s timeout, and a call that times out counts as Unreachable.
 * - [sync] without a cursor returns only events that overlap the range. With a cursor, incremental upserts
 *   MAY lie outside the range (Google's syncToken can't carry timeMin/timeMax); the store keeps them and
 *   queries filter by range.
 */
interface CalendarProvider {
    val descriptor: ProviderDescriptor

    /** Non-null [existing] means "reconnect this connection" (spec §9.6): report a Connection with the same id. */
    @Composable
    fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit)

    suspend fun sources(conn: Connection): List<CalendarSource>

    suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult
}
