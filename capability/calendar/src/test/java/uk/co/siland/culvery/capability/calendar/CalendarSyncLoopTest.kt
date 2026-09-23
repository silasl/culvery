package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

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
}
