package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowRetryTest {
    @Test
    fun retriesWaitOneSecondDoublingToAMinute() {
        assertThat((0L..7L).map(::retryDelayMillis))
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L).inOrder()
    }

    @Test
    fun doublingIsClampedAndCapped() {
        assertThat(listOf(0, 1, 5, 100).map { doublingMillis(10_000, 300_000, it) })
            .containsExactly(10_000L, 20_000L, 300_000L, 300_000L).inOrder()
        assertThat(doublingMillis(10_000, 300_000, -1)).isEqualTo(10_000L)
    }

    /** Regression guard: `retryDelayMillis` keeps its callers' waits. */
    @Test
    fun retryDelaysAreUnchanged() {
        assertThat((0L..8L).map(::retryDelayMillis))
            .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L).inOrder()
    }

    @Test
    fun theWaitDoublesFromTheSecondFailureToTheCap() {
        val run = FailureRun()
        val waits = (0..7).map {
            run.waitMillis(2_000, 60_000).also { run.failed() }
        }
        assertThat(waits)
            .containsExactly(2_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L).inOrder()
    }

    @Test
    fun onlyTheFirstFailureOfARunIsFirst() {
        val run = FailureRun()
        assertThat(listOf(run.failed(), run.failed(), run.failed())).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun succeededSaysHowManyFailed() {
        val run = FailureRun()
        repeat(3) { run.failed() }
        assertThat(run.succeeded()).isEqualTo(3)
        assertThat(run.succeeded()).isEqualTo(0)
    }

    @Test
    fun aResetKeepsTheRunButNotTheWait() {
        val run = FailureRun()
        repeat(3) { run.failed() }
        run.resetWaits()
        assertThat(run.waitMillis(2_000, 60_000)).isEqualTo(2_000L)
        assertThat(run.failed()).isFalse()
        assertThat(run.waitMillis(2_000, 60_000)).isEqualTo(2_000L)
        assertThat(run.succeeded()).isEqualTo(4)
    }

    @Test
    fun aFailingFlowStartsAgainAfterItsWaitAndEachFailureIsReported() = runTest {
        var starts = 0
        val failures = mutableListOf<String>()
        val flaky = flow {
            starts++
            if (starts < 3) throw IllegalStateException("store hiccup $starts")
            emit("ok")
        }
        val result = async { flaky.retryWithBackoff { failures += it.message.orEmpty() }.first() }
        runCurrent()
        assertThat(starts).isEqualTo(1)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(2_000)
        runCurrent()
        assertThat(result.await()).isEqualTo("ok")
        assertThat(failures).containsExactly("store hiccup 1", "store hiccup 2").inOrder()
    }

    /** §7.2: a value that stood for one wait starts the waits again from 1 s. */
    @Test
    fun aValueThatStandsStartsTheWaitsAgainFromOneSecond() = runTest {
        var starts = 0
        val flaky = flow {
            starts++
            if (starts == 1) throw IllegalStateException("store hiccup 1")
            if (starts == 2) {
                emit("a")
                // Just past the wait it would have had next.
                delay(retryDelayMillis(1) + 1)
                throw IllegalStateException("store hiccup 2")
            }
            emit("b")
        }
        val result = async { flaky.retryWithBackoff {}.take(2).toList() }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(retryDelayMillis(1) + 1)
        runCurrent()
        // "a" stood for a whole wait: the next wait is 1 s again.
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(3)
        assertThat(result.await()).containsExactly("a", "b").inOrder()
    }

    /** A flow that emits faster than the capped wait still resets it: the timer isn't restarted by every value. */
    @Test
    fun aFlowEmittingFasterThanTheWaitStillStartsTheWaitsAgain() = runTest {
        var starts = 0
        val flaky = flow {
            starts++
            if (starts <= 7) throw IllegalStateException("store hiccup $starts")
            if (starts == 8) {
                // Values at 0, 30 and 60 s, then a failure at 90 s: the first value has stood for the 60 s wait.
                repeat(3) { emit("v$it"); delay(30_000) }
                throw IllegalStateException("store hiccup 8")
            }
            emit("after")
        }
        val result = async { flaky.retryWithBackoff {}.take(4).toList() }
        runCurrent()
        // Seven failures: waits of 1, 2, 4, 8, 16, 32 and 60 s.
        advanceTimeBy(1_000 + 2_000 + 4_000 + 8_000 + 16_000 + 32_000 + 60_000)
        runCurrent()
        assertThat(starts).isEqualTo(8)
        advanceTimeBy(90_000)
        runCurrent()
        // The eighth failure waits 1 s, not 60.
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(9)
        assertThat(result.await()).containsExactly("v0", "v1", "v2", "after").inOrder()
    }

    /** §7.2: a read that emits and fails at once doesn't retry every second. */
    @Test
    fun aValueThatFailsAtOnceKeepsTheWaitsGrowing() = runTest {
        var starts = 0
        val flaky = flow {
            starts++
            if (starts == 1) throw IllegalStateException("store hiccup 1")
            if (starts == 2) {
                emit("a")
                throw IllegalStateException("store hiccup 2")
            }
            emit("b")
        }
        val result = async { flaky.retryWithBackoff {}.take(2).toList() }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(2)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(3)
        assertThat(result.await()).containsExactly("a", "b").inOrder()
    }

    /** Fails if the recovery line says "after 1 failures". */
    @Test
    fun oneFailureIsSingular() {
        assertThat(failuresPhrase(1)).isEqualTo("1 failure")
        assertThat(failuresPhrase(3)).isEqualTo("3 failures")
    }
}
