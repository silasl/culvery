package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

    @Test
    fun aValueGettingThroughStartsTheWaitsAgainFromOneSecond() = runTest {
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
        // One second again, not two: "a" got through, so the second failure is a fresh hiccup.
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(starts).isEqualTo(3)
        assertThat(result.await()).containsExactly("a", "b").inOrder()
    }
}
