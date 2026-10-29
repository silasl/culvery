package uk.co.siland.culvery.capability.party

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role

/** 13 §1, §2.6, §2.7: a fresh Adult or Admin PIN each time, and never on while Holiday mode is on. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PartyActionsTest {
    private val opened = mutableListOf<Pair<Authorised, Boolean>>()
    private val holiday = MutableStateFlow<Boolean?>(false)

    private fun TestScope.rig() = HouseRig(backgroundScope)

    private fun actions(access: TestAccess, rig: HouseRig) =
        PartyActions(access, rig.house, setOf(TestSwitch(HouseModes.HOLIDAY, "Holiday mode", holiday)))

    private suspend fun turnOn(rig: HouseRig) = rig.house.turnOn(TestAccess(Role.ADULT).authorise(HousePermissions.PARTY_MODE)!!)

    /** Fails if the pad asks for anything but `party.mode`, or the sheet opens for the wrong direction. */
    @Test
    fun aTapAsksForAFreshPartyPin() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        val actions = actions(access, rig)

        actions.toggle { by, on -> opened += by to on }

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(opened.map { it.second }).containsExactly(true)
        assertThat(opened.single().first.granted).contains(HousePermissions.PARTY_MODE)
        assertThat(rig.trace.calls).isEmpty()

        turnOn(rig)
        access.asked.clear()
        actions.toggle { by, on -> opened += by to on }

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(opened.map { it.second }).containsExactly(true, false).inOrder()
        assertThat(rig.house.isOn.value).isTrue()
    }

    /** Fails if a Child's PIN opens the sheet either way. */
    @Test
    fun aChildsPinOpensNothing() = runTest {
        val rig = rig()
        val access = TestAccess(Role.CHILD)
        rig.loaded()
        val actions = actions(access, rig)

        actions.toggle { by, on -> opened += by to on }
        turnOn(rig)
        actions.toggle { by, on -> opened += by to on }

        assertThat(opened).isEmpty()
        assertThat(access.asked).hasSize(2)
        assertThat(rig.house.isOn.value).isTrue()
    }

    @Test
    fun aCancelledPadOpensNothing() = runTest {
        val rig = rig()
        val access = TestAccess(null)
        rig.loaded()

        actions(access, rig).toggle { by, on -> opened += by to on }

        assertThat(opened).isEmpty()
        assertThat(access.asked).hasSize(1)
    }

    /** Review Focus 5. Fails if Holiday mode being on still asks for a PIN, or the line is missing, stale or kept after a tap. */
    @Test
    fun holidayOnRefusesBeforeThePin() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        val actions = actions(access, rig)
        holiday.value = true
        assertThat(actions.refusal.value).isNull()

        actions.toggle { by, on -> opened += by to on }

        assertThat(access.asked).isEmpty()
        assertThat(opened).isEmpty()
        assertThat(actions.refusal.value).isEqualTo("Turn Holiday mode off first.")

        holiday.value = false
        assertThat(actions.refusal.value).isNull()
        actions.toggle { by, on -> opened += by to on }
        assertThat(access.asked).hasSize(1)
        holiday.value = true
        assertThat(actions.refusal.value).isNull()
    }

    /** The line is a state, not an event: it is on while Holiday mode is and off once it isn't, without another tap. */
    @Test
    fun theRefusalFollowsHolidayModeBothWays() = runTest {
        val rig = rig()
        rig.loaded()
        val actions = actions(TestAccess(Role.ADULT), rig)
        val seen = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { actions.refusal.toList(seen) }
        holiday.value = true

        actions.toggle { by, on -> opened += by to on }
        holiday.value = false
        runCurrent()

        assertThat(seen).containsExactly(null, "Turn Holiday mode off first.", null).inOrder()
    }

    /** Fails if ending a party is refused because Holiday mode is on too. */
    @Test
    fun turningOffIsNeverRefused() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        turnOn(rig)
        holiday.value = true
        val actions = actions(access, rig)

        actions.toggle { by, on -> opened += by to on }

        assertThat(access.asked).containsExactly(listOf("party.mode"))
        assertThat(opened.map { it.second }).containsExactly(false)
        assertThat(actions.refusal.value).isNull()
    }

    /** Review Focus 5. Fails if Holiday mode turned on while the sheet was open lets the confirm turn Party on. */
    @Test
    fun theConfirmChecksAgain() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        rig.choose()
        val actions = actions(access, rig)
        var by: Authorised? = null
        actions.toggle { granted, _ -> by = granted }
        holiday.value = true

        val turned = actions.turnOn(by!!)

        assertThat(turned).isFalse()
        assertThat(rig.trace.calls).isEmpty()
        assertThat(rig.house.isOn.value).isFalse()
        assertThat(actions.refusal.value).isEqualTo("Turn Holiday mode off first.")

        holiday.value = false
        assertThat(actions.turnOn(by!!)).isTrue()
        assertThat(rig.house.isOn.value).isTrue()
        assertThat(actions.refusal.value).isNull()
    }

    /** Review Focus 5. A tap while a change runs asks no PIN and opens nothing. */
    @Test
    fun aTapWhileRunningDoesNothing() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        rig.choose()
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["plugs.begin"] = gate
        val turning = launch { turnOn(rig) }
        runCurrent()
        assertThat(rig.house.running.value).isEqualTo(PartyRun.TURNING_ON)

        actions(access, rig).toggle { by, on -> opened += by to on }

        assertThat(access.asked).isEmpty()
        assertThat(opened).isEmpty()
        gate.complete(Unit)
        turning.join()
    }

    @Test
    fun aTapBeforeTheRecordIsReadDoesNothing() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)

        actions(access, rig).toggle { by, on -> opened += by to on }

        assertThat(access.asked).isEmpty()
        assertThat(opened).isEmpty()
    }

    /** Fails if the caller going away mid turn-off (the sheet closing) leaves a party on with some steps undone. */
    @Test
    fun aTurnOffSurvivesTheCallerBeingCancelled() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        rig.choose()
        turnOn(rig)
        val gate = CompletableDeferred<Unit>()
        rig.trace.gates["music.stop"] = gate
        val by = access.authorise(HousePermissions.PARTY_MODE)!!
        val off = launch { actions(access, rig).turnOff(by) }
        runCurrent()
        assertThat(rig.house.running.value).isEqualTo(PartyRun.TURNING_OFF)

        off.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat(rig.house.isOn.value).isFalse()
        assertThat(rig.trace.calls).contains("lights.partyBack")
    }
}
