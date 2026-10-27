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
import uk.co.siland.culvery.capability.plugs.ModeChoice.LEAVE
import uk.co.siland.culvery.capability.plugs.ModeChoice.OFF
import uk.co.siland.culvery.capability.plugs.ModeChoice.ON
import uk.co.siland.culvery.capability.plugs.PlugPower.GONE
import uk.co.siland.culvery.capability.plugs.PlugPower.UNKNOWN
import uk.co.siland.culvery.capability.plugs.PlugPower.UNREACHABLE
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.capability.plugs.PlugPower.OFF as OFF_NOW
import uk.co.siland.culvery.capability.plugs.PlugPower.ON as ON_NOW

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

    /** Fan added, set to need a PIN when [pin]. */
    private suspend fun TestScope.rig(role: Role? = Role.ADULT, pin: Boolean = false, state: PlugState = fan(ON_NOW)) =
        Rig(this, role, state).also { rig ->
            rig.plugs.configure { it.adding(FAN).copy(needsPin = if (pin) setOf(FAN) else emptySet()) }
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
            val rig = rig(role, pin = true)
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

    /** 12 §5.2: fails if add doubles, a Leave keeps its entry, or remove leaves the plug in a list. */
    @Test
    fun addRemovePinAndChoiceReachTheStore() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.actions.add(FAN)
        rig.actions.add(FAN)
        rig.actions.add(LAMP)
        rig.actions.setNeedsPin(FAN, true)
        rig.actions.setChoice(HOLIDAY, FAN, ON)
        rig.actions.setChoice(PARTY, FAN, OFF)
        rig.actions.setChoice(HOLIDAY, LAMP, OFF)
        assertThat(rig.plugs.prefs().plugs).containsExactly(FAN, LAMP).inOrder()
        assertThat(rig.plugs.prefs().needsPin).containsExactly(FAN)
        assertThat(rig.plugs.prefs().choice(HOLIDAY, FAN)).isEqualTo(ON)

        rig.actions.setChoice(HOLIDAY, FAN, LEAVE)
        assertThat(rig.plugs.prefs().choice(HOLIDAY, FAN)).isEqualTo(LEAVE)
        rig.actions.setNeedsPin(FAN, false)
        assertThat(rig.plugs.prefs().needsPin).isEmpty()

        rig.actions.setChoice(HOLIDAY, FAN, ON)
        rig.actions.remove(FAN)
        val after = rig.plugs.prefs()
        assertThat(after.plugs).containsExactly(LAMP)
        assertThat(after.modes.values.flatMap { it.keys }).doesNotContain(FAN)
    }

    /** 12 §2.4: fails if a chip tap landing after a Remove writes an entry for a plug no longer added. */
    @Test
    fun aChoiceForARemovedPlugIsNotWritten() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.actions.add(FAN)
        rig.actions.remove(FAN)
        rig.actions.setChoice(HOLIDAY, FAN, ON)

        assertThat(rig.plugs.prefs().modes).isEmpty()
    }

    /** 12 §2.4: fails if adding a plug brings back an entry an earlier life of it left in a mode list. */
    @Test
    fun addingAPlugStartsOnLeaveEverywhere() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.plugs.configure { it.copy(modes = mapOf(HOLIDAY to mapOf(FAN to true, LAMP to false), PARTY to mapOf(FAN to false))) }
        rig.actions.add(FAN)

        val prefs = rig.plugs.prefs()
        assertThat(prefs.choice(HOLIDAY, FAN)).isEqualTo(LEAVE)
        assertThat(prefs.choice(PARTY, FAN)).isEqualTo(LEAVE)
        assertThat(prefs.modes).containsExactly(HOLIDAY, mapOf(LAMP to false))
    }

    /** Fails if a write that throws escapes the tab. */
    @Test
    fun aFailedWriteIsLoggedNotThrown() = runTest {
        val rig = Rig(this, Role.ADULT, fan(ON_NOW))
        rig.plugs.store.failOnWrite = 1
        rig.actions.add(FAN)

        assertThat(rig.plugs.prefs().plugs).isEmpty()
    }
}
