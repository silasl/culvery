package uk.co.siland.culvery.capability.calendar_testkit.fixtures

import androidx.compose.runtime.Composable
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
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
    private val canWrite: Boolean = true,
    private val dropTagsOnCreate: Boolean = false,
    private val rejectMissingDelete: Boolean = false,
) : CalendarProvider, CalendarWriter {
    override val descriptor = ProviderDescriptor(
        "calendar.tiny",
        "Tiny",
        "event",
        if (canWrite) setOf(Feature.READ, Feature.WRITE) else setOf(Feature.READ),
    )
    override val providerId = "calendar.tiny"

    private var failNext: Throwable? = null
    private val written = linkedMapOf<String, RemoteEvent>()
    private var version = 0
    private var nextId = 0

    fun failNextWith(error: Throwable) {
        failNext = error
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection) = if (canWrite) listOf(SOURCE, WRITABLE) else listOf(SOURCE)

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        failNext?.let { error ->
            failNext = null
            throw when {
                rawNetworkErrors && error is UnreachableException -> IOException("socket closed")
                rawAuthErrors && error is NeedsSignInException -> IllegalStateException("HTTP 401")
                else -> error
            }
        }
        if (source.id == WRITABLE.id) {
            // Every write bumps the version, so a cursor from before it gets a full replace.
            val current = SyncCursor("w$version")
            if (cursor == current) return SyncResult(emptyList(), emptyList(), current, fullReplace = false)
            return SyncResult(written.values.filter { range.overlaps(it.start, it.end) }, emptyList(), current, fullReplace = true)
        }
        if (cursor != null && !repeatOnCursor) return SyncResult(emptyList(), emptyList(), cursor, fullReplace = false)
        val events = all(range.zone).filter { leakOutOfRange || range.overlaps(it.start, it.end) }
        return SyncResult(events, emptyList(), SyncCursor("c1"), fullReplace = !partialFirstSync)
    }

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent {
        checkWritable(source)
        val event = RemoteEvent(
            "w${++nextId}",
            draft.title,
            draft.start,
            draft.end,
            recurring = false,
            forPerson = if (dropTagsOnCreate) null else draft.forPerson,
            createdBy = if (dropTagsOnCreate) null else draft.createdBy,
        )
        written[event.remoteId] = event
        version++
        return event
    }

    override suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent {
        checkWritable(source)
        if (remoteId !in written) throw WriteRejectedException("No event $remoteId")
        val event = RemoteEvent(remoteId, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
        written[remoteId] = event
        version++
        return event
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) {
        checkWritable(source)
        when {
            written.remove(remoteId) != null -> version++
            rejectMissingDelete -> throw WriteRejectedException("No event $remoteId")
        }
    }

    private fun checkWritable(source: CalendarSource) {
        if (!canWrite || source.id != WRITABLE.id) throw WriteRejectedException("Tiny can't write to ${source.id}")
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
        val WRITABLE = CalendarSource("tiny-w", "Tiny writable", writable = true)
    }
}
