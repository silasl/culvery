package uk.co.siland.culvery.core.house

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.WallClock

/** 11 §5.3, 13 §2.9: every mode ticks at start and each minute, and one stuck in a tick holds up no other. */
@RunWith(AndroidJUnit4::class)
class HouseModeSchedulerTest {
    private class Mode(override val modeId: String, private val held: CompletableDeferred<Unit>? = null) : HouseModeSwitch {
        override val modeName = modeId
        override val isOn: StateFlow<Boolean?> = MutableStateFlow(false)
        var ticks = 0

        override suspend fun tick() {
            ticks++
            held?.await()
        }
    }

    /** Fails if a mode misses its start or a minute, or waits on another mode's tick. */
    @Test
    fun eachModeTicksOnItsOwn() = runTest {
        // Not the background scope, whose work advanceUntilIdle doesn't wait for.
        val scope = CoroutineScope(coroutineContext + Job())
        val minutes = MutableSharedFlow<Long>()
        val clock = HouseholdClock(flowOf(ZoneOffset.UTC), WallClock { 0L }, scope, minutes)
        val stuck = Mode(HouseModes.HOLIDAY, CompletableDeferred())
        val party = Mode(HouseModes.PARTY)
        HouseModeScheduler(setOf(stuck, party), clock, scope).start()
        testScheduler.advanceUntilIdle()
        assertThat(stuck.ticks).isEqualTo(1)
        assertThat(party.ticks).isEqualTo(1)

        minutes.emit(60_000L)
        testScheduler.advanceUntilIdle()
        minutes.emit(120_000L)
        testScheduler.advanceUntilIdle()

        assertThat(stuck.ticks).isEqualTo(1)
        assertThat(party.ticks).isEqualTo(3)
        scope.cancel()
    }
}
