package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstDrawTest {
    @Test
    fun aWaiterGoesOnOnceHomeHasDrawn() = runTest {
        val draw = FirstDraw()
        var through = false
        launch { draw.await(); through = true }
        runCurrent()
        assertThat(through).isFalse()
        draw.markDrawn()
        runCurrent()
        assertThat(through).isTrue()
    }

    @Test
    fun withoutAFrameAWaiterGoesOnAfterThreeSeconds() = runTest {
        var through = false
        launch { FirstDraw().await(); through = true }
        advanceTimeBy(FIRST_DRAW_WAIT_MS - 1)
        runCurrent()
        assertThat(through).isFalse()
        advanceTimeBy(1)
        runCurrent()
        assertThat(through).isTrue()
    }

    @Test
    fun aLaterWaiterDoesNotWaitAtAll() = runTest {
        val draw = FirstDraw()
        draw.markDrawn()
        var through = false
        launch { draw.await(); through = true }
        runCurrent()
        assertThat(through).isTrue()
    }
}
