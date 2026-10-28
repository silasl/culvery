package uk.co.siland.culvery.capability.plugs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.plugs.ModeChoice.IGNORE
import uk.co.siland.culvery.capability.plugs.ModeChoice.OFF
import uk.co.siland.culvery.capability.plugs.ModeChoice.ON
import uk.co.siland.culvery.capability.plugs.PlugPower.GONE
import uk.co.siland.culvery.capability.plugs.PlugPower.UNKNOWN
import uk.co.siland.culvery.capability.plugs.PlugPower.UNREACHABLE
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.capability.plugs.PlugPower.OFF as OFF_NOW
import uk.co.siland.culvery.capability.plugs.PlugPower.ON as ON_NOW
import uk.co.siland.culvery.core.plugin.test.RecordingToaster

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlugActionsTest {
    private class Rig(scope: TestScope, role: Role?, state: PlugState) {
        val plugs = PlugsRig(state)
        val access = TestAccess(role)
        val toaster = RecordingToaster()
        val job = Job()
        val actions = PlugActions(access, plugs.settings, plugs.system, toaster, CoroutineScope(StandardTestDispatcher(scope.testScheduler) + job))
    }

    /** Fan shown, set to need a PIN when [pin]. */
    private suspend fun TestScope.rig(role: Role? = Role.ADULT, pin: Boolean = false, state: PlugState = fan(ON_NOW)) =
        Rig(this, role, state).also { rig ->
            rig.plugs.configure { it.showing(FAN).copy(needsPin = if (pin) setOf(FAN) else emptySet()) }
        }

    /** 12 §4.2: fails if a plug not set to need a PIN asks for one, or switches the wrong way. */
    @Test
    fun aPlainPlugSwitchesWithNoPin() = runTest {
        val rig = rig()
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()

        assertThat(rig.access.asked).isEmpty()
        assertThat(rig.plugs.system.switches).containsExactly(FAN to false)
    }

    /** 12 §4.4: fails if a PIN plug skips the pad, or an Admin is refused. */
    @Test
    fun aPinPlugAsksForAFreshPin() = runTest {
        for (role in listOf(Role.ADULT, Role.ADMIN)) {
            val rig = rig(role, pin = true, state = fan(OFF_NOW))
            rig.actions.switch(fan(OFF_NOW))
            advanceUntilIdle()

            assertThat(rig.access.asked).containsExactly(listOf("plugs.switch"))
            assertThat(rig.plugs.system.switches).containsExactly(FAN to true)
        }
    }

    /** Review Focus 3: fails if the PIN rule is remembered from the card instead of read from the store at the tap. */
    @Test
    fun thePinRuleIsReadAtTheTap() = runTest {
        val rig = rig()
        val shown = fan(ON_NOW)
        rig.plugs.configure { it.copy(needsPin = setOf(FAN)) }
        rig.actions.switch(shown)
        advanceUntilIdle()

        assertThat(rig.access.asked).containsExactly(listOf("plugs.switch"))
    }

    /** Fails if an unreadable store lets a plug switch without the PIN. */
    @Test
    fun aStoreThatCantBeReadAsksForThePin() = runTest {
        val rig = rig()
        rig.plugs.store.failReads = true
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()

        assertThat(rig.access.asked).containsExactly(listOf("plugs.switch"))
        assertThat(rig.plugs.system.switches).containsExactly(FAN to false)
    }

    /** 12 §4.4: fails if a Child's PIN switches anything. */
    @Test
    fun aChildsPinSwitchesNothing() = runTest {
        val rig = rig(Role.CHILD, pin = true)
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()

        assertThat(rig.access.asked).hasSize(1)
        assertThat(rig.plugs.system.switches).isEmpty()
    }

    /** Fails if a cancelled pad switches anything. */
    @Test
    fun aCancelledPadSwitchesNothing() = runTest {
        val rig = rig(null, pin = true)
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()

        assertThat(rig.plugs.system.switches).isEmpty()
        assertThat(rig.toaster.shown).isEmpty()
    }

    /** 12 §4.4 "a fresh PIN every time": fails if the second switch rides on the first PIN. */
    @Test
    fun everySwitchAsksAgain() = runTest {
        val rig = rig(pin = true)
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()
        rig.actions.switch(fan(OFF_NOW))
        advanceUntilIdle()

        assertThat(rig.access.asked).containsExactly(listOf("plugs.switch"), listOf("plugs.switch"))
    }

    /** Review Focus 4: fails if Switching… shows while the pad is up, stays after Cancel, or doesn't show once the PIN is in. */
    @Test
    fun aPinPlugShowsSwitchingOnlyAfterThePin() = runTest {
        val accepted = rig(pin = true)
        val pad = CompletableDeferred<Unit>().also { accepted.access.padGate = it }
        val gate = CompletableDeferred<Unit>().also { accepted.plugs.system.switchGates[FAN] = it }
        accepted.actions.switch(fan(ON_NOW))
        runCurrent()
        assertThat(accepted.access.asked).hasSize(1)
        assertThat(accepted.actions.switching.value).isEmpty()

        pad.complete(Unit)
        runCurrent()
        assertThat(accepted.actions.switching.value).containsExactly(FAN)
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(accepted.actions.switching.value).isEmpty()

        val cancelled = rig(null, pin = true)
        val cancelPad = CompletableDeferred<Unit>().also { cancelled.access.padGate = it }
        cancelled.actions.switch(fan(ON_NOW))
        runCurrent()
        assertThat(cancelled.actions.switching.value).isEmpty()
        cancelPad.complete(Unit)
        advanceUntilIdle()
        assertThat(cancelled.actions.switching.value).isEmpty()
        assertThat(cancelled.plugs.system.switches).isEmpty()
    }

    /** Fails if the switch goes by the state at the tap instead of the state after the PIN, or still switches a plug that can no longer be switched. */
    @Test
    fun theSwitchUsesTheStateAfterThePin() = runTest {
        val rig = rig(pin = true)
        val pad = CompletableDeferred<Unit>().also { rig.access.padGate = it }
        rig.actions.switch(fan(ON_NOW))
        runCurrent()
        rig.plugs.power(FAN, OFF_NOW)
        pad.complete(Unit)
        advanceUntilIdle()
        assertThat(rig.plugs.system.switches).containsExactly(FAN to true)
        assertThat(rig.actions.switching.value).isEmpty()

        val lost = rig(pin = true)
        val lostPad = CompletableDeferred<Unit>().also { lost.access.padGate = it }
        lost.actions.switch(fan(ON_NOW))
        runCurrent()
        lost.plugs.power(FAN, UNREACHABLE)
        lostPad.complete(Unit)
        advanceUntilIdle()
        assertThat(lost.plugs.system.switches).isEmpty()
        assertThat(lost.actions.switching.value).isEmpty()
    }

    /** Review Focus 4: fails if a second tap while the pad is up asks again, and if Cancel doesn't free the button for the next tap. */
    @Test
    fun aSecondTapWhileAskingDoesNothing() = runTest {
        val rig = rig(null, pin = true)
        val pad = CompletableDeferred<Unit>().also { rig.access.padGate = it }
        rig.actions.switch(fan(ON_NOW))
        runCurrent()
        rig.actions.switch(fan(ON_NOW))
        runCurrent()
        assertThat(rig.access.asked).hasSize(1)

        pad.complete(Unit)
        advanceUntilIdle()
        rig.access.padGate = null
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()
        assertThat(rig.access.asked).hasSize(2)
    }

    /** Review Focus 3: fails if a tap while switching asks again or calls again, or Switching… doesn't clear. */
    @Test
    fun aSecondTapWhileSwitchingDoesNothing() = runTest {
        val rig = rig(pin = true)
        val gate = CompletableDeferred<Unit>()
        rig.plugs.system.switchGates[FAN] = gate
        rig.actions.switch(fan(ON_NOW))
        runCurrent()

        assertThat(rig.actions.switching.value).containsExactly(FAN)
        rig.actions.switch(fan(ON_NOW))
        runCurrent()
        assertThat(rig.access.asked).hasSize(1)
        assertThat(rig.plugs.system.switches).hasSize(1)

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(rig.actions.switching.value).isEmpty()
    }

    /** Review Focus 3: fails if a cancelled scope, a refused PIN or a throwing switch leaves Switching… on. */
    @Test
    fun switchingClearsAfterCancelRefusalOrThrow() = runTest {
        val cancelled = rig()
        cancelled.plugs.system.switchGates[FAN] = CompletableDeferred()
        cancelled.actions.switch(fan(ON_NOW))
        runCurrent()
        assertThat(cancelled.actions.switching.value).containsExactly(FAN)
        cancelled.job.cancel()
        runCurrent()
        assertThat(cancelled.actions.switching.value).isEmpty()

        val refused = rig(Role.CHILD, pin = true)
        refused.actions.switch(fan(ON_NOW))
        advanceUntilIdle()
        assertThat(refused.actions.switching.value).isEmpty()

        val threw = rig()
        threw.plugs.system.throwOnSwitch = true
        threw.actions.switch(fan(ON_NOW))
        advanceUntilIdle()
        assertThat(threw.actions.switching.value).isEmpty()
    }

    /** Review Focus 3: fails if a plug the card can't act on is asked about or called. */
    @Test
    fun anUnreachablePlugIsNotSwitched() = runTest {
        val rig = rig(pin = true)
        for (power in listOf(UNREACHABLE, GONE, UNKNOWN)) rig.actions.switch(fan(power))
        advanceUntilIdle()

        assertThat(rig.access.asked).isEmpty()
        assertThat(rig.plugs.system.switches).isEmpty()
        assertThat(rig.actions.switching.value).isEmpty()
    }

    /** Fails if an On plug is asked to switch on. */
    @Test
    fun theTargetIsTheOppositeOfTheStateShown() = runTest {
        val rig = rig()
        rig.actions.switch(fan(ON_NOW))
        advanceUntilIdle()
        rig.actions.switch(fan(OFF_NOW))
        advanceUntilIdle()

        assertThat(rig.plugs.system.switches).containsExactly(FAN to false, FAN to true).inOrder()
    }

    /** 12 §6: fails if a result's toast differs from the spec's, or Done and Busy say anything. */
    @Test
    fun eachResultHasItsToast() = runTest {
        val results = listOf<Pair<PlugResult, String?>>(
            PlugResult.Unreachable to "Can’t reach Fan. Check it’s plugged in and the Tapo app can see it.",
            PlugResult.NotSwitched to "Fan didn’t switch. Try again, or use the Tapo app.",
            PlugResult.Failed("x") to "x",
            PlugResult.Done to null,
            PlugResult.Busy to null,
        )
        for ((result, line) in results) {
            val rig = rig()
            rig.plugs.system.results += result
            rig.actions.switch(fan(ON_NOW))
            advanceUntilIdle()

            assertThat(rig.toaster.shown).isEqualTo(listOfNotNull(line))
        }
    }

    /** 12 §4.4: fails if set-up skips the pad or a Child gets in. */
    @Test
    fun manageAsksForAFreshPinAndRefusesAChild() = runTest {
        val adult = rig()
        assertThat(adult.actions.mayManage()).isTrue()
        assertThat(adult.access.asked).containsExactly(listOf("plugs.manage"))

        assertThat(rig(Role.CHILD).actions.mayManage()).isFalse()
    }

    /** 12r §4.2: fails if show doubles, an Ignore keeps its entry, or hide loses the plug's choices or PIN setting. */
    @Test
    fun showHidePinAndChoiceReachTheStore() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.actions.show(FAN, true)
        rig.actions.show(FAN, true)
        rig.actions.show(LAMP, true)
        rig.actions.setNeedsPin(FAN, true)
        rig.actions.setChoice(HOLIDAY, FAN, ON)
        rig.actions.setChoice(PARTY, FAN, OFF)
        rig.actions.setChoice(HOLIDAY, LAMP, OFF)
        assertThat(rig.plugs.prefs().plugs).containsExactly(FAN, LAMP).inOrder()
        assertThat(rig.plugs.prefs().needsPin).containsExactly(FAN)
        assertThat(rig.plugs.prefs().choice(HOLIDAY, FAN)).isEqualTo(ON)

        rig.actions.setChoice(HOLIDAY, FAN, IGNORE)
        assertThat(rig.plugs.prefs().choice(HOLIDAY, FAN)).isEqualTo(IGNORE)
        rig.actions.setNeedsPin(FAN, false)
        assertThat(rig.plugs.prefs().needsPin).isEmpty()

        rig.actions.setChoice(HOLIDAY, FAN, ON)
        rig.actions.setNeedsPin(FAN, true)
        rig.actions.show(FAN, false)
        val hidden = rig.plugs.prefs()
        assertThat(hidden.plugs).containsExactly(LAMP)
        assertThat(hidden.choice(HOLIDAY, FAN)).isEqualTo(ON)
        assertThat(hidden.choice(PARTY, FAN)).isEqualTo(OFF)
        assertThat(hidden.needsPin).containsExactly(FAN)

        rig.actions.show(FAN, true)
        assertThat(rig.plugs.prefs().choice(HOLIDAY, FAN)).isEqualTo(ON)
    }

    /** Fails if a chip tap or a PIN switch landing after a hide writes for a plug that isn't shown. */
    @Test
    fun choiceAndPinAreOnlyWrittenWhileShown() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.actions.show(FAN, true)
        rig.actions.show(FAN, false)
        rig.actions.setChoice(HOLIDAY, FAN, ON)
        rig.actions.setNeedsPin(FAN, true)

        val prefs = rig.plugs.prefs()
        assertThat(prefs.modes).isEmpty()
        assertThat(prefs.needsPin).isEmpty()
    }

    /** Fails if a write that throws escapes the tab. */
    @Test
    fun aFailedWriteIsLoggedNotThrown() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.plugs.store.failOnWrite = 1
        rig.actions.show(FAN, true)

        assertThat(rig.plugs.prefs().plugs).isEmpty()
    }
}
