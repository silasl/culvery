package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.UnreachableException

/** 10 §4.5: the queue is sent on start, when something is queued, and when the head comes due. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingDrainLoopTest : ShoppingTestBase() {
    private fun kotlinx.coroutines.test.TestScope.loop(rig: ShoppingRig) =
        ShoppingDrainLoop(rig.sync::drain, { rig.clock.nowMillis() }, backgroundScope)

    @Test
    fun drainsOnStart() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        loop(rig).start()
        runCurrent()
        assertThat(rig.provider.calls).contains("add")
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun drainsOnWake() = runTest {
        val rig = rig()
        rig.connect()
        val loop = loop(rig)
        loop.start()
        runCurrent()
        assertThat(rig.provider.calls).isEmpty()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        loop.wake()
        runCurrent()
        assertThat(rig.provider.calls).contains("add")
    }

    @Test
    fun drainsWhenTheHeadIsDue() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failNext = UnreachableException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        loop(rig).start()
        runCurrent()
        assertThat(rig.provider.calls).doesNotContain("add")
        advanceTimeBy(29_999)
        runCurrent()
        assertThat(rig.provider.calls).doesNotContain("add")
        advanceTimeBy(1)
        runCurrent()
        assertThat(rig.provider.calls).contains("add")
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun aDrainThatThrowsDoesNotStopTheLoop() = runTest {
        var drains = 0
        val loop = ShoppingDrainLoop({ drains++; error("database locked") }, { 0L }, backgroundScope)
        loop.start()
        runCurrent()
        advanceTimeBy(SHOPPING_BACKOFF_MS.first())
        runCurrent()
        assertThat(drains).isEqualTo(2)
    }
}
