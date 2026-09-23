package uk.co.siland.culvery.capability.calendar_testkit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.core.plugin.Connection

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

    private val subject by lazy { provider() }
    private val conn by lazy { connection() }
    private val window by lazy { range() }

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
}
