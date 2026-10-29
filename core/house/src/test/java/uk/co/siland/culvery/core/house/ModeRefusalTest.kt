package uk.co.siland.culvery.core.house

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 13 §2.6: one mode's tile refuses while the other is on, and says so until that mode goes off. */
@RunWith(AndroidJUnit4::class)
class ModeRefusalTest {
    private class Switch(override val modeId: String, override val modeName: String, val on: MutableStateFlow<Boolean?>) : HouseModeSwitch {
        override val isOn: StateFlow<Boolean?> = on

        override suspend fun tick() = Unit
    }

    private val party = Switch(HouseModes.PARTY, "Party mode", MutableStateFlow(false))
    private val holiday = Switch(HouseModes.HOLIDAY, "Holiday mode", MutableStateFlow(false))
    private val refusal = ModeRefusal(party, setOf(party, holiday))

    /** Fails if the guard runs the turn-on while Holiday mode is on, or doesn't run it once it is off. */
    @Test
    fun theGuardRunsTheTurnOnOnlyWhileNoOtherModeIsOn() = runTest {
        var ran = 0
        holiday.on.value = true

        assertThat(refusal.turnOnUnlessRefused { ran++ }).isFalse()
        assertThat(ran).isEqualTo(0)
        assertThat(refusal.current()).isEqualTo("Turn Holiday mode off first.")

        holiday.on.value = false
        assertThat(refusal.turnOnUnlessRefused { ran++ }).isTrue()
        assertThat(ran).isEqualTo(1)
        assertThat(refusal.current()).isNull()
    }

    /** Fails if a turn-off tap is refused or keeps the line, or a turn-on tap isn't refused while Holiday mode is on. */
    @Test
    fun aTurnOffTapIsNeverRefusedAndClearsTheLine() {
        holiday.on.value = true

        assertThat(refusal.refusesTap(turningOn = true)).isTrue()
        assertThat(refusal.current()).isEqualTo("Turn Holiday mode off first.")
        assertThat(refusal.refusesTap(turningOn = false)).isFalse()
        assertThat(refusal.current()).isNull()
    }

    /** Fails if the line doesn't start with the current value, or doesn't go once Holiday mode is off. */
    @Test
    fun theLineFollowsTheOtherMode() = runTest {
        refusal.line.test {
            assertThat(awaitItem()).isNull()
            holiday.on.value = true
            refusal.refusesTap(turningOn = true)
            assertThat(awaitItem()).isEqualTo("Turn Holiday mode off first.")
            holiday.on.value = false
            assertThat(awaitItem()).isNull()
        }
    }

    /** Fails if the line comes back when Holiday mode goes on again with no new tap (the Plan 13 walk-through). */
    @Test
    fun theLineStaysGoneWhenTheOtherModeGoesOnAgain() = runTest {
        refusal.line.test {
            assertThat(awaitItem()).isNull()
            holiday.on.value = true
            refusal.refusesTap(turningOn = true)
            assertThat(awaitItem()).isEqualTo("Turn Holiday mode off first.")
            holiday.on.value = false
            assertThat(awaitItem()).isNull()
            holiday.on.value = true
            expectNoEvents()
        }
        assertThat(refusal.current()).isNull()
    }
}
