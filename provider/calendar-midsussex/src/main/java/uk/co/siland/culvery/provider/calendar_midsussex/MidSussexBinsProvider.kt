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
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
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
class MidSussexBinsProvider @Inject constructor(
    private val council: CouncilClient,
    private val reads: BinsReads,
    private val clock: WallClock,
    private val updates: UpdateRequests,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(
        MIDSUSSEX_PROVIDER_ID,
        "Mid Sussex bins",
        Icons.DELETE,
        setOf(Feature.READ),
        connectHint = "Enter the house name or number and the postcode, as on the council’s site.",
    )

    private val lock = Mutex()
    private var loaded = false
    private var held: BinsRead? = null

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) = Unit

    override suspend fun sources(conn: Connection): List<CalendarSource> = listOf(SOURCE_BINS)

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        val read = lock.withLock { latest(conn, range.zone) }
        read.failure?.let { throw UnreadableException(BINS_UNREADABLE, it.name) }
        if (read.lastGoodDay == null) throw UnreachableException("The council’s site hasn’t been read yet")
        val upserts = read.collections.map(::eventOf).filter { range.overlaps(it.start, it.end) }
        return SyncResult(upserts, emptyList(), cursor = null, fullReplace = true)
    }

    private suspend fun latest(conn: Connection, zone: ZoneId): BinsRead {
        if (!loaded) {
            held = reads.load()
            loaded = true
        }
        val now = clock.nowMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val key = addressKey(conn)
        val stored = held?.takeIf { it.addressKey == key }
        if (stored != null && !due(stored, today)) return stored
        val read = try {
            val config = conn.config
            val collections = council.read(config[CONFIG_HOUSE].orEmpty(), config[CONFIG_POSTCODE].orEmpty(), config[CONFIG_ADDRESS].orEmpty())
            Log.i(TAG, "Bins read: ${collections.size} collections")
            BinsRead(key, now, lastGoodDay = today, collections = collections)
        } catch (e: BinsReadFailed) {
            val kind = e.kind
            Log.w(TAG, "Bins read failed: ${kind.name}")
            attempted(stored, key, now, today).copy(failure = kind)
        } catch (e: UnreachableException) {
            countAttempt(attempted(stored, key, now, today))
            throw e
        } catch (e: CancellationException) {
            // The engine's timeout: an attempt like any unreachable one.
            countAttempt(attempted(stored, key, now, today))
            throw e
        }
        keep(read)
        return read
    }

    private fun due(read: BinsRead, today: LocalDate): Boolean {
        if ((updates.last.value ?: Long.MIN_VALUE) > read.lastAttemptMillis) return true
        if (read.lastGoodDay?.isBefore(today) == false) return false
        if (read.failureDay != today) return true
        return read.failuresToday < ATTEMPTS_A_DAY && !clock.within(read.lastAttemptMillis, RETRY_GAP_MS)
    }

    private fun attempted(stored: BinsRead?, key: String, now: Long, today: LocalDate): BinsRead {
        val base = stored ?: BinsRead(key, now)
        val failures = if (base.failureDay == today) base.failuresToday + 1 else 1
        return base.copy(lastAttemptMillis = now, failuresToday = failures, failureDay = today)
    }

    private suspend fun keep(read: BinsRead) {
        held = read
        reads.save(read)
    }

    private suspend fun countAttempt(read: BinsRead) = withContext(NonCancellable) { keep(read) }
}

// A hash, not the address: it only has to change when the connection's address does.
private fun addressKey(conn: Connection): String =
    "${conn.id}:" + listOf(CONFIG_HOUSE, CONFIG_POSTCODE, CONFIG_ADDRESS).joinToString("\n") { conn.config[it].orEmpty() }.hashCode()

private fun eventOf(day: Collection): RemoteEvent = RemoteEvent(
    remoteId = "bins-${day.date}",
    title = binsTitle(day.kinds),
    start = EventTime.AllDay(day.date),
    end = EventTime.AllDay(day.date.plusDays(1)),
    recurring = false,
    forPerson = PersonId.FAMILY.value,
    bins = orderedBins(day.kinds),
)
