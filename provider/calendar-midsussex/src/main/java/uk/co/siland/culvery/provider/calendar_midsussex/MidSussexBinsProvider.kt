package uk.co.siland.culvery.provider.calendar_midsussex

import android.util.Log
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.UnreadableException
import uk.co.siland.culvery.capability.calendar.UpdateRequests
import uk.co.siland.culvery.capability.calendar.binsTitle
import uk.co.siland.culvery.capability.calendar.orderedBins
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.today
import uk.co.siland.culvery.core.plugin.within
import uk.co.siland.culvery.core.ui.Icons

internal const val TAG = "MidSussexBins"

const val MIDSUSSEX_PROVIDER_ID = "calendar.midsussex"
const val CONFIG_HOUSE = "house"
const val CONFIG_POSTCODE = "postcode"
const val CONFIG_ADDRESS = "address"

const val BINS_UNREADABLE = "Can’t read the bin dates."

val SOURCE_BINS = CalendarSource("bins", "Mid Sussex District Council", writable = false, shown = true, primary = true)

private const val RETRY_GAP_MS = 60 * 60_000L

/** One daily attempt plus three retries. */
private const val ATTEMPTS_A_DAY = 4

/**
 * Mid Sussex District Council's bin collections, read from its site (spec 3.5, 4.2). The engine asks every few minutes;
 * the council is read only on a new day, an hour after a failed attempt (at most four attempts a day), or after
 * Update now. Every other pass answers from the stored read, and a stored failure is thrown until a read works.
 */
@Singleton
class MidSussexBinsProvider internal constructor(
    private val council: CouncilClient,
    private val reads: BinsReads,
    private val clock: WallClock,
    private val updates: UpdateRequests,
    private val zone: suspend () -> ZoneId,
) : CalendarProvider {
    @Inject
    constructor(council: CouncilClient, reads: BinsReads, clock: WallClock, updates: UpdateRequests, zone: HouseholdZone) :
        this(council, reads, clock, updates, zone::current)

    override val descriptor = ProviderDescriptor(
        MIDSUSSEX_PROVIDER_ID,
        BINS_LABEL,
        Icons.DELETE,
        setOf(Feature.READ),
        connectHint = "Enter the house name or number and the postcode, as on the council’s site.",
        carriesBins = true,
    )

    private val lock = Mutex()

    /** Each connection's read, by connection id; a null value means nothing is stored for it. */
    private val held = mutableMapOf<String, BinsRead?>()

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) =
        MidSussexConnectScreen(council, clock, zone, existing, onConnected)

    override suspend fun sources(conn: Connection): List<CalendarSource> = listOf(SOURCE_BINS)

    /** The cursor stamps the stored read and [range]: an unchanged read for the same range writes nothing. */
    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        val read = lock.withLock { latest(conn, range.zone) }
        read.failure?.let { throw UnreadableException(BINS_UNREADABLE, it.name) }
        val goodMillis = read.lastGoodMillis ?: throw UnreachableException("The council’s site hasn’t been read yet")
        val stamp = SyncCursor("$goodMillis:${range.start}:${range.endExclusive}")
        if (cursor == stamp) return SyncResult(emptyList(), emptyList(), stamp, fullReplace = false)
        val upserts = read.collections.filter { !it.date.isBefore(range.start) && it.date.isBefore(range.endExclusive) }.map(::eventOf)
        return SyncResult(upserts, emptyList(), stamp, fullReplace = true)
    }

    private suspend fun latest(conn: Connection, householdZone: ZoneId): BinsRead {
        if (conn.id !in held) held[conn.id] = reads.load(conn.id)
        val now = clock.nowMillis()
        val today = clock.today(householdZone)
        val key = addressKey(conn)
        val stored = held[conn.id]?.takeIf { it.addressKey == key }
        if (stored != null && !due(stored, today, householdZone)) return stored
        return try {
            val config = conn.config
            val collections = council.read(config[CONFIG_HOUSE].orEmpty(), config[CONFIG_POSTCODE].orEmpty(), config[CONFIG_ADDRESS].orEmpty())
            Log.i(TAG, "Bins read: ${collections.size} collections")
            keep(conn.id, BinsRead(key, now, lastGoodMillis = now, collections = collections))
        } catch (failed: BinsReadFailed) {
            Log.w(TAG, "Bins read failed: ${failed.kind.name}")
            keep(conn.id, attempted(stored, key, now, today, householdZone).copy(failure = failed.kind))
        } catch (e: Exception) {
            // A CancellationException is the engine's timeout: an attempt like any unreachable one.
            if (e !is UnreachableException && e !is CancellationException) throw e
            keep(conn.id, attempted(stored, key, now, today, householdZone))
            throw e
        }
    }

    private fun due(read: BinsRead, today: LocalDate, householdZone: ZoneId): Boolean {
        if ((updates.last.value ?: Long.MIN_VALUE) > read.lastAttemptMillis) return true
        if (read.lastGoodMillis?.let { dayOf(it, householdZone) } == today) return false
        if (dayOf(read.lastAttemptMillis, householdZone) != today) return true
        return read.failuresToday < ATTEMPTS_A_DAY && !clock.within(read.lastAttemptMillis, RETRY_GAP_MS)
    }

    private fun attempted(stored: BinsRead?, key: String, now: Long, today: LocalDate, householdZone: ZoneId): BinsRead {
        val base = stored ?: BinsRead(key, now)
        val failures = if (dayOf(base.lastAttemptMillis, householdZone) == today) base.failuresToday + 1 else 1
        return base.copy(lastAttemptMillis = now, failuresToday = failures)
    }

    /** Saved even while the read is being cancelled, so a timed-out attempt still counts. */
    private suspend fun keep(connectionId: String, read: BinsRead): BinsRead = withContext(NonCancellable) {
        held[connectionId] = read
        reads.save(connectionId, read)
        read
    }
}

private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

// A hash, not the address: it only has to change when the connection's address does.
private fun addressKey(conn: Connection): String =
    listOf(CONFIG_HOUSE, CONFIG_POSTCODE, CONFIG_ADDRESS).joinToString("\n") { conn.config[it].orEmpty() }.hashCode().toString()

private fun eventOf(day: BinDay): RemoteEvent {
    val bins = orderedBins(day.kinds)
    return RemoteEvent(
        remoteId = "bins-${day.date}",
        title = binsTitle(bins),
        start = EventTime.AllDay(day.date),
        end = EventTime.AllDay(day.date.plusDays(1)),
        recurring = false,
        forPerson = PersonId.FAMILY.value,
        bins = bins,
    )
}
