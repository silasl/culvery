package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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

/**
 * One calendar in a connection. [shown]: ticked and not hidden in the service (Google: `selected` and not `hidden`).
 * [primary]: the account's own calendar, at most one per connection; connecting makes it the master (3a design D4).
 */
data class CalendarSource(
    val id: String,
    val name: String,
    val writable: Boolean,
    val shown: Boolean = true,
    val primary: Boolean = false,
)

/** Whether the tablet shows this source: the primary always, any other as it is ticked in the service. */
internal val CalendarSource.visibleOnTablet: Boolean get() = shown || primary

/** The [Connection.config] key a provider stores the signed-in account under; Settings shows it ("Google Calendar · {account}"). */
const val CONFIG_ACCOUNT = "account"

/**
 * One concrete occurrence from a provider.
 *
 * [end] is exclusive: an all-day event on 23 September has start AllDay(23 Sep) and end AllDay(24 Sep),
 * as in Google Calendar and iCalendar. [start] and [end] are both Timed or both AllDay.
 * Recurring events arrive already expanded, one RemoteEvent per occurrence, each with its own [remoteId],
 * and [recurring] set. [forPerson] and [createdBy] are household PersonId values when the provider stores them.
 * [recurrenceRule] is the series' RRULE line (e.g. "RRULE:FREQ=WEEKLY") when the provider knows it.
 */
data class RemoteEvent(
    val remoteId: String,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val recurring: Boolean,
    val forPerson: String? = null,
    val createdBy: String? = null,
    val recurrenceRule: String? = null,
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
 * What the tablet already holds of one calendar's repeating events (4c design §6.3, C9): each stored instance's id with
 * its series' rule, null when unknown. A provider reads it to find a deleted series' instances.
 */
fun interface StoredSeries {
    suspend fun instances(connectionId: String, sourceId: String): Map<String, String?>
}

/**
 * What the tablet asks a provider to write. [end] is exclusive, as in [RemoteEvent]. [forPerson] and [createdBy]
 * are household PersonId values ("family" allowed) that the provider stores with the event (Google:
 * extendedProperties.private). Names are never written. [forPersonColor] is that person's colour (ARGB), so the
 * provider can colour the event (Google: the nearest colorId); null for Family or untagged.
 */
data class EventDraft(
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val forPerson: String?,
    val createdBy: String?,
    val forPersonColor: Long? = null,
)

/** What an update changes (3a design C3): the title, the start and end together, or who the event is for. */
enum class EventField { TITLE, TIMES, FOR_PERSON }

/** The connection's sign-in has expired or been revoked (Google: 401, or a refused token refresh). */
class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * The provider couldn't be reached, or asked to be tried later: network errors, and for Google 429, 403
 * rate-limit reasons (rateLimitExceeded, userRateLimitExceeded) and every 5xx. The engine retries with backoff.
 */
open class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * The source isn't there any more (Google: 404, or a 403 that isn't a rate limit, on events.list). The engine
 * records it as unreachable and refreshes the connection's sources on the next pass, which removes it if it has gone.
 */
class SourceGoneException(message: String? = null, cause: Throwable? = null) : UnreachableException(message, cause)

/**
 * A permanent refusal: retrying would not help (Google: a 4xx other than 401, 429 and the 403 rate limits).
 * A delete that finds the event already gone (404 or 410) is a success, not a refusal.
 */
class WriteRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A calendar service. Implementations live in :provider:calendar-* and bind themselves with
 * `@Binds @IntoSet`. Behaviour is pinned by CalendarProviderContractTest in :capability:calendar-testkit.
 *
 * - [sources] and [sync] throw only [NeedsSignInException] (auth) or [UnreachableException] (network).
 * - They must be main-safe and cancellable: no uninterruptible blocking I/O. The sync engine calls them on
 *   Dispatchers.IO under a 60 s timeout, and a call that times out counts as Unreachable.
 * - [sync] without a cursor returns only events that overlap the range. With a cursor, incremental upserts
 *   MAY lie outside the range (Google's syncToken can't carry timeMin/timeMax); the engine prunes what lies
 *   outside what it keeps.
 */
interface CalendarProvider {
    val descriptor: ProviderDescriptor

    /** Non-null [existing] means "reconnect this connection" (spec §9.6): report a Connection with the same id. */
    @Composable
    fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit)

    suspend fun sources(conn: Connection): List<CalendarSource>

    suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult
}

/**
 * A new client key: a random UUID as 32 lowercase hex digits, which is also a valid Google event id (base32hex,
 * 5–1024 characters). The editor chooses one on each Save tap; a queued create keeps its key for every retry.
 */
fun newClientKey(): String = UUID.randomUUID().toString().replace("-", "")

/**
 * Writes for a provider that declares Feature.WRITE. Bound `@IntoSet` beside its CalendarProvider; the engine
 * matches them by [providerId] == descriptor.id. Behaviour is pinned by the write checks in
 * CalendarProviderContractTest.
 *
 * - Throws only [WriteRejectedException] (permanent), [NeedsSignInException] or [UnreachableException].
 * - Main-safe and cancellable. Called on Dispatchers.IO under a timeout: 10 s from the editor, 60 s from the
 *   outbox drain. A timeout counts as unreachable and the change is queued or retried.
 * - Stores [EventDraft.forPerson] and [EventDraft.createdBy] so that the next sync returns them unchanged.
 * - A write to a source the connection doesn't have, or can't write, throws [WriteRejectedException].
 * - [delete] of an event that no longer exists succeeds: a retried delete must not be reported as a failure.
 * - [create] is idempotent by its client key: the writer uses the key as the event's id, so the returned remoteId
 *   equals it, and a create with a key already used on that source returns the event that key made, never a second
 *   one (Google: events.insert with id = clientKey; a 409 means it exists, so fetch and return it).
 * - [create] never recreates a deleted event: a create whose key belonged to an event since deleted throws
 *   [WriteRejectedException] (Google: the 409's event is cancelled).
 * - [find] throws only [NeedsSignInException] or [UnreachableException].
 */
interface CalendarWriter {
    val providerId: String

    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent

    /**
     * Changes only [fields] of the event to [draft]'s values; the draft's other fields are ignored. Everything else the
     * service holds (description, location, attendees, reminders, the createdBy tag) is kept (Google: PATCH, never
     * PUT). Returns the event as the service now holds it.
     */
    suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft, fields: Set<EventField>): RemoteEvent

    /** The event as the service holds it; null when it doesn't exist or was deleted. */
    suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent?

    suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String)
}
