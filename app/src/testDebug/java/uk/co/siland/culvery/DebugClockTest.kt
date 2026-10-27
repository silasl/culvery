package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.di.DebugClock

class DebugClockTest {
    /** 11 §8.1: Fails if 90 minutes doesn't put the clock 5 400 000 ms ahead, or 0 doesn't clear it. */
    @Test
    fun theOffsetMovesTheClockAndZeroClearsIt() {
        val clock = DebugClock { 1_000_000L }
        assertThat(clock.nowMillis()).isEqualTo(1_000_000L)
        clock.minutes = 90
        assertThat(clock.nowMillis()).isEqualTo(1_000_000L + 5_400_000L)
        clock.minutes = 0
        assertThat(clock.nowMillis()).isEqualTo(1_000_000L)
    }
}
