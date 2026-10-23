package uk.co.siland.culvery.capability.shopping

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

/** 10 §4.4: a check a minute while a list is connected, Culvery is in front and a screen shows it, and at once on opening. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingChecksTest {
    private val connected = MutableStateFlow(true)
    private val inFront = MutableStateFlow(true)
    private val times = mutableListOf<Long>()

    private fun TestScope.checks() = ShoppingChecks(connected, inFront, { times += currentTime }, backgroundScope).also { it.start() }

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

    @Test
    fun nothingChecksOutOfFrontOrUnwatchedOrUnconnected() = runTest {
        val checks = checks()
        advanceTimeBy(SHOPPING_CHECK_MS * 2)
        runCurrent()
        assertThat(times).isEmpty()
        inFront.value = false
        checks.watch()
        runCurrent()
        advanceTimeBy(SHOPPING_CHECK_MS * 2)
        runCurrent()
        assertThat(times).isEmpty()
        inFront.value = true
        connected.value = false
        runCurrent()
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
        ShoppingChecks(connected, inFront, { count++; error("database locked") }, backgroundScope).also {
            it.start()
            it.watch()
        }
        runCurrent()
        advanceTimeBy(SHOPPING_CHECK_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }
}
