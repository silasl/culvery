package uk.co.siland.culvery.capability.calendar_testkit.fixtures

import androidx.compose.runtime.Composable
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** A minimal provider whose flags each break one rule of the contract. */
class TinyProvider(
    private val leakOutOfRange: Boolean = false,
    private val partialFirstSync: Boolean = false,
    private val repeatOnCursor: Boolean = false,
    private val rawNetworkErrors: Boolean = false,
    private val inclusiveAllDayEnd: Boolean = false,
    private val rawAuthErrors: Boolean = false,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor("calendar.tiny", "Tiny", "event", setOf(Feature.READ))

    private var failNext: Throwable? = null

    fun failNextWith(error: Throwable) {
        failNext = error
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection) = listOf(SOURCE)

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        failNext?.let { error ->
            failNext = null
            throw when {
                rawNetworkErrors && error is UnreachableException -> IOException("socket closed")
                rawAuthErrors && error is NeedsSignInException -> IllegalStateException("HTTP 401")
                else -> error
            }
        }
        if (cursor != null && !repeatOnCursor) return SyncResult(emptyList(), emptyList(), cursor, fullReplace = false)
        val events = all(range.zone).filter { leakOutOfRange || range.overlaps(it.start, it.end) }
        return SyncResult(events, emptyList(), SyncCursor("c1"), fullReplace = !partialFirstSync)
    }

    private fun all(zone: ZoneId): List<RemoteEvent> {
        fun at(date: LocalDate, hour: Int) = EventTime.Timed(date.atTime(hour, 0).atZone(zone).toInstant())
        val d = LocalDate.of(2026, 9, 24)
        val far = LocalDate.of(2026, 11, 7)
        return listOf(
            RemoteEvent("walk", "Walk", at(d, 9), at(d, 10), recurring = false),
            RemoteEvent("yoga-1", "Yoga", at(d, 18), at(d, 19), recurring = true),
            RemoteEvent("yoga-2", "Yoga", at(d.plusDays(7), 18), at(d.plusDays(7), 19), recurring = true),
            RemoteEvent("holiday", "Holiday", EventTime.AllDay(d), EventTime.AllDay(d.plusDays(2)), recurring = false),
            // An inclusive end on a one-day event equals its start date.
            RemoteEvent("bins", "Bins", EventTime.AllDay(d), EventTime.AllDay(if (inclusiveAllDayEnd) d else d.plusDays(1)), recurring = false),
            RemoteEvent("far", "Far away", at(far, 9), at(far, 10), recurring = false),
        )
    }

    companion object {
        val SOURCE = CalendarSource("tiny", "Tiny", writable = false)
    }
}
