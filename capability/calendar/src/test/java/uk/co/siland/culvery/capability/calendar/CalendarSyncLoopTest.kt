package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

// Robolectric only because the loop logs through android.util.Log when a sync throws.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarSyncLoopTest {
    @Test
    fun syncsOnStartAndEveryFiveMinutes() = runTest {
        var count = 0
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
        advanceTimeBy(SYNC_INTERVAL_MS - 1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun syncsAgainWhenAConnectionIsAdded() = runTest {
        var count = 0
        val ids = MutableStateFlow(emptyList<String>())
        CalendarSyncLoop({ count++ }, ids, backgroundScope).start()
        runCurrent()
        assertThat(count).isEqualTo(1)
        ids.value = listOf("c1")
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aFailingSyncDoesNotStopTheLoop() = runTest {
        var count = 0
        CalendarSyncLoop({ count++; if (count == 1) error("database locked") }, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aSyncThatTimesOutInternallyDoesNotStopTheLoop() = runTest {
        var count = 0
        val syncAll: suspend () -> Unit = {
            count++
            if (count == 1) withTimeout(1_000) { awaitCancellation() }
        }
        CalendarSyncLoop(syncAll, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        // The first sync times out at 1 s; the next one is due an interval after that.
        advanceTimeBy(1_000 + SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aStrayCancellationDoesNotStopTheLoop() = runTest {
        var count = 0
        val syncAll: suspend () -> Unit = {
            count++
            if (count == 1) throw CancellationException("stray")
        }
        CalendarSyncLoop(syncAll, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun requestSyncRunsAPassNow() = runTest {
        var count = 0
        val loop = CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope)
        loop.start()
        runCurrent()
        loop.requestSync()
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun requestsDuringAPassCoalesceIntoOneMorePass() = runTest {
        val gate = CompletableDeferred<Unit>()
        var count = 0
        val loop = CalendarSyncLoop({ count++; if (count == 1) gate.await() }, MutableStateFlow(listOf("c1")), backgroundScope)
        loop.start()
        runCurrent()
        repeat(3) { loop.requestSync() }
        runCurrent()
        assertThat(count).isEqualTo(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(count).isEqualTo(2)
        advanceTimeBy(SYNC_INTERVAL_MS - 1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aConnectionAddedMidPassGetsAPassOfItsOwnWithoutCancellingTheRunningOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        var finished = 0
        val ids = MutableStateFlow(listOf("c1"))
        val syncAll: suspend () -> Unit = {
            started++
            if (started == 1) gate.await()
            finished++
        }
        CalendarSyncLoop(syncAll, ids, backgroundScope).start()
        runCurrent()
        ids.value = listOf("c1", "c2")
        runCurrent()
        assertThat(started).isEqualTo(1)
        gate.complete(Unit)
        runCurrent()
        assertThat(started).isEqualTo(2)
        assertThat(finished).isEqualTo(2)
    }

    @Test
    fun aDueRetryWakesTheLoopBeforeTheFiveMinuteTick() = runTest {
        var count = 0
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope, untilNextRetry = { 30_000L }).start()
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun anOverdueRetryStillWaitsASecond() = runTest {
        var count = 0
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope, untilNextRetry = { -5_000L }).start()
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun cancellingTheScopeStopsTheLoopMidPass() = runTest {
        val loopScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val entered = CompletableDeferred<Unit>()
        var started = 0
        var retryReads = 0
        var sawCancellation = false
        val syncAll: suspend () -> Unit = {
            started++
            entered.complete(Unit)
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                sawCancellation = true
                throw e
            }
        }
        CalendarSyncLoop(syncAll, MutableStateFlow(listOf("c1")), loopScope, untilNextRetry = { retryReads++; null }).start()
        entered.await()
        loopScope.cancel()
        runCurrent()
        assertThat(sawCancellation).isTrue()
        advanceTimeBy(SYNC_INTERVAL_MS * 2)
        runCurrent()
        assertThat(started).isEqualTo(1)
        // A cancelled loop never goes on to read the outbox for its next wait.
        assertThat(retryReads).isEqualTo(0)
    }

    /** Review focus #5 at loop level: an Error (a writer that recursed) is logged, and the next pass still runs. */
    @Test
    fun anErrorInAPassIsLoggedAndTheNextPassStillRuns() = runTest {
        var count = 0
        CalendarSyncLoop(
            { count++; if (count == 1) throw StackOverflowError("a writer recursed") },
            MutableStateFlow(listOf("c1")),
            backgroundScope,
        ).start()
        runCurrent()
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
        assertThat(ShadowLog.getLogsForTag("CalendarSync").map { it.throwable?.message }).contains("a writer recursed")
    }

    @Test
    fun aFailedDrainHoldsTheNextPassBackWhateverTheQueueSays() = runTest {
        var count = 0
        CalendarSyncLoop(
            { count++ }, MutableStateFlow(listOf("c1")), backgroundScope,
            untilNextRetry = { -5_000L }, drainBackoff = { 30_000L },
        ).start()
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(29_999)
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aFailingConnectionListIsReadAgainAndStillStartsAPass() = runTest {
        var reads = 0
        var count = 0
        val ids = flow {
            reads++
            if (reads == 1) throw IllegalStateException("database locked")
            emit(listOf("c1"))
        }
        CalendarSyncLoop({ count++ }, ids, backgroundScope).start()
        runCurrent()
        assertThat(count).isEqualTo(0)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun anErrorReadingTheQueueWaitsTheFullIntervalAndTheLoopGoesOn() = runTest {
        var count = 0
        CalendarSyncLoop(
            { count++ }, MutableStateFlow(listOf("c1")), backgroundScope,
            untilNextRetry = { throw StackOverflowError("outbox recursed") },
        ).start()
        runCurrent()
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }
}
