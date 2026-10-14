package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.plugin.WallClock

/** 5 §4.5: at start, then daily, and each time Culvery comes back to the front. */
@OptIn(ExperimentalCoroutinesApi::class)
class DoorPruneLoopTest {
    @Test
    fun itPrunesAtStartEachDayAndOnEachReturnToTheFront() = runTest {
        val prunedAt = mutableListOf<Long>()
        val inFront = MutableStateFlow(false)
        DoorPruneLoop({ prunedAt += it }, WallClock { NOW + testScheduler.currentTime }, inFront, backgroundScope).start()
        runCurrent()
        assertThat(prunedAt).containsExactly(NOW)
        advanceTimeBy(DAY_MS - 1)
        runCurrent()
        assertThat(prunedAt).hasSize(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(prunedAt).containsExactly(NOW, NOW + DAY_MS).inOrder()
        inFront.value = true
        runCurrent()
        assertThat(prunedAt).hasSize(3)
    }
}
