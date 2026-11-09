package uk.co.siland.culvery.capability.shopping

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.plugin.UnreachableException

/** 10 §4.4, 19 §3 and §5: a check a minute while a list is connected, the screen is lit and a screen shows it, and at once on opening; failures back off. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingChecksTest {
    private val connected = MutableStateFlow(true)
    private val lit = MutableStateFlow(true)
    private val times = mutableListOf<Long>()

    private fun TestScope.checks() = ShoppingChecks(connected, lit, { times += currentTime }, backgroundScope).also { it.start() }

    private fun TestScope.failingChecks(failure: () -> Exception = { UnreachableException() }, succeedAfter: Int = Int.MAX_VALUE) =
        ShoppingChecks(connected, lit, {
            times += currentTime
            if (times.size <= succeedAfter) throw failure()
        }, backgroundScope).also { it.start() }

    private suspend fun TestScope.runTo(seconds: Long) {
        advanceTimeBy(seconds * 1_000 - currentTime)
        runCurrent()
    }

    @Test
    fun watchingChecksAtOnceThenEveryMinute() = runTest {
        checks().watch()
        runCurrent()
        assertThat(times).containsExactly(0L)
        advanceTimeBy(SHOPPING_CHECK_MS - 1)
        runCurrent()
        assertThat(times).containsExactly(0L)
        advanceTimeBy(1)
        runCurrent()
        advanceTimeBy(SHOPPING_CHECK_MS)
        runCurrent()
        assertThat(times).containsExactly(0L, 60_000L, 120_000L).inOrder()
    }

    /** Regression guard: the old out-of-front gate, now the lit signal. */
    @Test
    fun nothingChecksWhileTheScreenIsDarkAndWakingChecksAtOnce() = runTest {
        val checks = checks()
        advanceTimeBy(SHOPPING_CHECK_MS * 2)
        runCurrent()
        assertThat(times).isEmpty()
        lit.value = false
        checks.watch()
        runCurrent()
        advanceTimeBy(SHOPPING_CHECK_MS * 2)
        runCurrent()
        assertThat(times).isEmpty()
        lit.value = true
        runCurrent()
        assertThat(times).containsExactly(currentTime)
        lit.value = true
        connected.value = false
        runCurrent()
        times.clear()
        advanceTimeBy(SHOPPING_CHECK_MS * 2)
        runCurrent()
        assertThat(times).isEmpty()
    }

    @Test
    fun checksStopWhenTheLastScreenGoes() = runTest {
        val checks = checks()
        val stop = checks.watch()
        runCurrent()
        stop()
        runCurrent()
        times.clear()
        advanceTimeBy(SHOPPING_CHECK_MS * 2)
        runCurrent()
        assertThat(times).isEmpty()
    }

    @Test
    fun aSecondWatchWithCheckNowChecksAtOnce() = runTest {
        val checks = checks()
        checks.watch()
        runCurrent()
        advanceTimeBy(5_000)
        checks.watch(checkNow = false)
        runCurrent()
        assertThat(times).containsExactly(0L)
        checks.watch(checkNow = true)
        runCurrent()
        assertThat(times).containsExactly(0L, 5_000L).inOrder()
    }

    @Test
    fun askNowChecksAtOnce() = runTest {
        val checks = checks()
        checks.watch()
        runCurrent()
        advanceTimeBy(5_000)
        checks.askNow()
        runCurrent()
        assertThat(times).containsExactly(0L, 5_000L).inOrder()
    }

    @Test
    fun stoppingTwiceCountsOnce() = runTest {
        val checks = checks()
        val home = checks.watch()
        checks.watch()
        home()
        home()
        assertThat(checks.watching).isEqualTo(1)
    }

    @Test
    fun aFailingCheckDoesNotStopTheChecks() = runTest {
        var count = 0
        ShoppingChecks(connected, lit, { count++; error("database locked") }, backgroundScope).also {
            it.start()
            it.watch()
        }
        runCurrent()
        advanceTimeBy(SHOPPING_CHECK_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun failuresDoubleTheWaitUpToFiveMinutes() = runTest {
        failingChecks().watch()
        runTo(1_100)
        assertThat(times).containsExactly(0L, 60_000L, 180_000L, 420_000L, 720_000L, 1_020_000L).inOrder()
    }

    @Test
    fun anAskResetsTheWait() = runTest {
        val checks = failingChecks()
        checks.watch()
        runTo(200)
        assertThat(times).containsExactly(0L, 60_000L, 180_000L).inOrder()
        checks.askNow()
        runCurrent()
        assertThat(times.last()).isEqualTo(200_000L)
        runTo(259)
        assertThat(times.last()).isEqualTo(200_000L)
        runTo(260)
        assertThat(times.last()).isEqualTo(260_000L)
    }

    @Test
    fun wakingResetsTheBackoff() = runTest {
        failingChecks().watch()
        runTo(200)
        lit.value = false
        runCurrent()
        lit.value = true
        runCurrent()
        assertThat(times.last()).isEqualTo(200_000L)
        runTo(259)
        assertThat(times.last()).isEqualTo(200_000L)
        runTo(260)
        assertThat(times.last()).isEqualTo(260_000L)
    }

    @Test
    fun oneLineForARunAndOneForTheRecovery() = runTest {
        ShadowLog.clear()
        failingChecks({ UnreachableException(reason = "rateLimitExceeded") }, succeedAfter = 3).watch()
        runTo(1_000)
        val lines = ShadowLog.getLogsForTag("ShoppingChecks").filter { it.type >= Log.INFO }.map { it.type to it.msg }
        assertThat(lines).containsExactly(
            Log.WARN to "A shopping check failed (UnreachableException, rateLimitExceeded)",
            Log.INFO to "Shopping checks work again after 3 failures",
        ).inOrder()
    }
}
