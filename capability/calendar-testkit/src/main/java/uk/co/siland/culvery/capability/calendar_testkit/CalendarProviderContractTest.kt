package uk.co.siland.culvery.capability.calendar_testkit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
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
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

/**
 * Every calendar provider subclasses this in its own tests (spec §11). Invariants are checked on every
 * source; fixture-specific checks (out-of-range, recurring, cursor) use [sourceWithEvents].
 *
 * Range checks apply to cursorless (first) syncs only. With a cursor, incremental upserts MAY lie outside
 * the range (Google's syncToken can't carry timeMin/timeMax); the store keeps them and queries filter.
 *
 * Optional hooks are `open` with a null default, so a new hook never breaks an existing subclass.
 */
abstract class CalendarProviderContractTest {
    protected abstract fun provider(): CalendarProvider
    protected abstract fun connection(): Connection
    /** The window the app would ask for, e.g. yesterday to 14 days ahead. */
    protected abstract fun range(): DateRange
    /** A source with events inside [range]. */
    protected abstract fun sourceWithEvents(): CalendarSource
    /** An event in [sourceWithEvents] that lies outside [range] but within 60 days of it; null to skip. */
    protected open fun outOfRangeEventTitle(): String? = null
    /** A recurring event with at least two occurrences in [range] in [sourceWithEvents]; null to skip. */
    protected open fun recurringTitle(): String? = null
    /** Makes the next call fail as an expired sign-in would; null if the provider can't simulate it. */
    protected open fun simulateAuthFailure(): (() -> Unit)? = null
    /** Makes the next call fail as a network outage would; null if the provider can't simulate it. */
    protected open fun simulateUnreachable(): (() -> Unit)? = null
    /** The writer, for a provider that declares Feature.WRITE; the write checks fail if it is missing. */
    protected open fun writer(): CalendarWriter? = null
    /** A writable source the write checks may create, change and delete events in. */
    protected open fun writableSource(): CalendarSource? = null

    private val subject by lazy { provider() }
    private val conn by lazy { connection() }
    private val window by lazy { range() }
    private val writes by lazy { writer() }

    private suspend fun firstSync(source: CalendarSource = sourceWithEvents(), range: DateRange = window): SyncResult =
        subject.sync(conn, source, range, null)

    private suspend fun everySourceFirstSync(): List<RemoteEvent> =
        subject.sources(conn).flatMap { firstSync(it).upserts }

    private suspend fun failureOfFirstSync(): Throwable? =
        try {
            firstSync()
            null
        } catch (e: Throwable) {
            e
        }

    @Test
    fun sourceIdsAreUniqueAndStable() = runTest {
        val first = subject.sources(conn).map { it.id }
        val second = subject.sources(conn).map { it.id }
        assertThat(first).containsNoDuplicates()
        assertThat(second).containsExactlyElementsIn(first).inOrder()
        assertThat(first).contains(sourceWithEvents().id)
    }

    /** Cursorless syncs only: see the class comment. */
    @Test
    fun syncReturnsOnlyEventsOverlappingRange() = runTest {
        assertWithMessage("fixture: sourceWithEvents() has no events in range()").that(firstSync().upserts).isNotEmpty()
        everySourceFirstSync().forEach { e ->
            assertWithMessage("'${e.title}' (${e.start} to ${e.end}) is outside $window")
                .that(window.overlaps(e.start, e.end)).isTrue()
        }
        val title = outOfRangeEventTitle()
        assumeTrue("provider has no out-of-range fixture", title != null)
        val wide = DateRange(window.start.minusDays(60), window.endExclusive.plusDays(60), window.zone)
        assertWithMessage("fixture: '$title' should exist within 60 days of range()")
            .that(firstSync(range = wide).upserts.map { it.title }).contains(title)
        assertThat(firstSync().upserts.map { it.title }).doesNotContain(title)
    }

    @Test
    fun endIsNeverBeforeStart() = runTest {
        everySourceFirstSync().forEach { e ->
            assertWithMessage("'${e.title}': start and end must both be timed or both all-day")
                .that(e.start::class).isEqualTo(e.end::class)
            assertWithMessage("'${e.title}': end is before start")
                .that(e.end.instantIn(window.zone)).isAtLeast(e.start.instantIn(window.zone))
        }
    }

    @Test
    fun allDayEventsUseExclusiveEndDates() = runTest {
        val allDay = everySourceFirstSync().filter { it.start is EventTime.AllDay && it.end is EventTime.AllDay }
        assumeTrue("provider has no all-day fixture", allDay.isNotEmpty())
        allDay.forEach { e ->
            val start = e.start as EventTime.AllDay
            val end = e.end as EventTime.AllDay
            assertWithMessage("'${e.title}': an all-day end date is exclusive, so it must be after the start date")
                .that(end.date).isGreaterThan(start.date)
        }
    }

    @Test
    fun remoteIdsAreUniqueWithinASource() = runTest {
        subject.sources(conn).forEach { source ->
            assertWithMessage("source ${source.id}").that(firstSync(source).upserts.map { it.remoteId }).containsNoDuplicates()
        }
    }

    @Test
    fun recurringOccurrencesHaveDistinctIds() = runTest {
        val title = recurringTitle()
        assumeTrue("provider has no recurring fixture", title != null)
        val occurrences = firstSync().upserts.filter { it.title == title }
        assertThat(occurrences.size).isAtLeast(2)
        assertThat(occurrences.all { it.recurring }).isTrue()
        assertThat(occurrences.map { it.remoteId }).containsNoDuplicates()
    }

    @Test
    fun firstSyncIsFullReplace() = runTest {
        subject.sources(conn).forEach { source ->
            assertWithMessage("source ${source.id}").that(firstSync(source).fullReplace).isTrue()
        }
    }

    @Test
    fun syncWithReturnedCursorDoesNotRepeatUnchangedEvents() = runTest {
        val first = firstSync()
        assumeTrue("provider returns no cursor", first.cursor != null)
        val second = subject.sync(conn, sourceWithEvents(), window, first.cursor)
        assertThat(second.upserts).isEmpty()
        assertThat(second.removedIds).isEmpty()
        assertWithMessage("an unchanged incremental result must not be a full replace: it would wipe the cache")
            .that(second.fullReplace).isFalse()
    }

    @Test
    fun authFailureThrowsNeedsSignIn() = runTest {
        val simulate = simulateAuthFailure()
        assumeTrue("provider cannot simulate an auth failure", simulate != null)
        simulate!!.invoke()
        assertThat(failureOfFirstSync()).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun networkFailureThrowsUnreachable() = runTest {
        val simulate = simulateUnreachable()
        assumeTrue("provider cannot simulate a network failure", simulate != null)
        simulate!!.invoke()
        assertThat(failureOfFirstSync()).isInstanceOf(UnreachableException::class.java)
    }

    /** Skips a provider without WRITE; fails one that declares WRITE but can't be exercised. */
    private fun requireWriting(): Pair<CalendarWriter, CalendarSource> {
        assumeTrue("provider does not declare WRITE", Feature.WRITE in subject.descriptor.features)
        val w = writes
        assertWithMessage("the provider declares WRITE, so writer() must return its writer").that(w).isNotNull()
        assertWithMessage("writer.providerId must equal the provider's descriptor.id")
            .that(w!!.providerId).isEqualTo(subject.descriptor.id)
        val source = writableSource()
        assertWithMessage("the provider declares WRITE, so writableSource() must name a writable source").that(source).isNotNull()
        return w to source!!
    }

    /** A one-hour event on day [dayOffset] after the window's second day, at 10:00 in the window's zone. */
    private fun draftIn(title: String, dayOffset: Long, forPerson: String? = "contract-for", createdBy: String? = "contract-by"): EventDraft {
        val start = window.start.plusDays(1 + dayOffset).atTime(10, 0).atZone(window.zone).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy)
    }

    private suspend fun nextSyncReturns(source: CalendarSource, cursor: SyncCursor?, remoteId: String): RemoteEvent? =
        subject.sync(conn, source, window, cursor).upserts.firstOrNull { it.remoteId == remoteId }

    /** Dates and title always; the two tags only when [checkTags] (an all-day round trip is about dates, not tags). */
    private fun assertMatches(what: String, event: RemoteEvent, draft: EventDraft, checkTags: Boolean = true) {
        assertWithMessage("$what: title").that(event.title).isEqualTo(draft.title)
        assertWithMessage("$what: start").that(event.start).isEqualTo(draft.start)
        assertWithMessage("$what: end").that(event.end).isEqualTo(draft.end)
        if (checkTags) {
            assertWithMessage("$what: the forPerson tag").that(event.forPerson).isEqualTo(draft.forPerson)
            assertWithMessage("$what: the createdBy tag").that(event.createdBy).isEqualTo(draft.createdBy)
        }
        assertWithMessage("$what: a written event is never recurring").that(event.recurring).isFalse()
    }

    @Test
    fun createdEventComesBackOnTheNextSyncWithItsTags() = runTest {
        val (w, source) = requireWriting()
        val before = subject.sync(conn, source, window, null)
        val draft = draftIn("Contract check", 1)
        val created = w.create(conn, source, draft)
        assertMatches("create's result", created, draft)
        val synced = nextSyncReturns(source, before.cursor, created.remoteId)
        assertWithMessage("the next sync must return the created event").that(synced).isNotNull()
        assertMatches("the next sync", synced!!, draft)
    }

    @Test
    fun anAllDayEventRoundTrips() = runTest {
        val (w, source) = requireWriting()
        val before = subject.sync(conn, source, window, null)
        val first = window.start.plusDays(2)
        // Two days: the end date is exclusive, as in RemoteEvent.
        val draft = EventDraft("All-day check", EventTime.AllDay(first), EventTime.AllDay(first.plusDays(2)), "contract-for", "contract-by")
        val created = w.create(conn, source, draft)
        assertMatches("create's result", created, draft, checkTags = false)
        val synced = nextSyncReturns(source, before.cursor, created.remoteId)
        assertWithMessage("the next sync must return the all-day event").that(synced).isNotNull()
        assertMatches("the next sync", synced!!, draft, checkTags = false)
    }

    @Test
    fun updatedFieldsRoundTrip() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Before", 1))
        val cursor = subject.sync(conn, source, window, null).cursor
        val changed = draftIn("After", 2, forPerson = "contract-other")
        val updated = w.update(conn, source, created.remoteId, changed)
        assertWithMessage("an update keeps the remoteId").that(updated.remoteId).isEqualTo(created.remoteId)
        assertMatches("update's result", updated, changed)
        val synced = nextSyncReturns(source, cursor, created.remoteId)
        assertWithMessage("the next sync must return the updated event").that(synced).isNotNull()
        assertMatches("the next sync", synced!!, changed)
    }

    @Test
    fun deletedEventIsRemovedOnTheNextSync() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Doomed", 1))
        val cursor = subject.sync(conn, source, window, null).cursor
        w.delete(conn, source, created.remoteId)
        val next = subject.sync(conn, source, window, cursor)
        val reported = if (next.fullReplace) {
            next.upserts.none { it.remoteId == created.remoteId }
        } else {
            created.remoteId in next.removedIds
        }
        assertWithMessage("the next sync must report the delete: a removal, or absence from a full replace")
            .that(reported).isTrue()
        assertThat(subject.sync(conn, source, window, null).upserts.map { it.remoteId }).doesNotContain(created.remoteId)
    }

    @Test
    fun deletingAnEventThatIsAlreadyGoneSucceeds() = runTest {
        val (w, source) = requireWriting()
        val created = w.create(conn, source, draftIn("Twice", 1))
        w.delete(conn, source, created.remoteId)
        val error = try {
            w.delete(conn, source, created.remoteId)
            null
        } catch (e: Exception) {
            e
        }
        assertWithMessage("a second delete of the same event must succeed: a retried delete may already have landed")
            .that(error).isNull()
    }

    @Test
    fun aWriteToAnUnknownSourceIsRejected() = runTest {
        val (w, _) = requireWriting()
        val unknown = CalendarSource("contract-no-such-source", "Nowhere", writable = true)
        val error = try {
            w.create(conn, unknown, draftIn("Lost", 1))
            null
        } catch (e: Throwable) {
            e
        }
        assertThat(error).isInstanceOf(WriteRejectedException::class.java)
    }
}
