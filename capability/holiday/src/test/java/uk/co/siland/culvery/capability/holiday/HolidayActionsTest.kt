package uk.co.siland.culvery.capability.holiday

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role

/** 11 §5.2: the tile asks for a fresh Adult or Admin PIN both ways, and a refused or cancelled pad changes nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HolidayActionsTest {
    private val back = HouseRig.TODAY.plusDays(3)
    private val opened = mutableListOf<Authorised>()
    private val party = MutableStateFlow<Boolean?>(false)

    private fun kotlinx.coroutines.test.TestScope.rig() = HouseRig(backgroundScope)

    private fun actions(access: TestAccess, rig: HouseRig, events: FakeEvents? = null) =
        HolidayActions(access, rig.house, events, setOf(TestSwitch(HouseModes.PARTY, "Party mode", party)), { HouseRig.TODAY })

    /** Fails if the pad asks for anything but `holiday.mode`, or the sheet doesn't open once. */
    @Test
    fun anAdultsPinOpensTheSheet() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()

        actions(access, rig).toggle { opened += it }

        assertThat(access.asked).containsExactly(listOf("holiday.mode"))
        assertThat(opened).hasSize(1)
        assertThat(opened.single().granted).contains(HousePermissions.HOLIDAY_MODE)
        assertThat(rig.lights.calls).isEmpty()
    }

    /** Review Focus 4. Fails if a Child's PIN opens the sheet, or turns Holiday mode off. */
    @Test
    fun aChildsPinChangesNothing() = runTest {
        val rig = rig()
        val access = TestAccess(Role.CHILD)
        rig.loaded()
        val actions = actions(access, rig)

        actions.toggle { opened += it }
        assertThat(opened).isEmpty()

        rig.house.turnOn(back, TestAccess(Role.ADULT).authorise(HousePermissions.HOLIDAY_MODE)!!)
        rig.lights.calls.clear()
        actions.toggle { opened += it }

        assertThat(opened).isEmpty()
        assertThat(rig.lights.calls).isEmpty()
        assertThat(rig.house.holiday.value).isTrue()
        assertThat(access.asked).hasSize(2)
    }

    @Test
    fun aCancelledPadChangesNothing() = runTest {
        val rig = rig()
        val access = TestAccess(null)
        rig.loaded()
        val actions = actions(access, rig)

        actions.toggle { opened += it }
        rig.house.turnOn(back, TestAccess(Role.ADULT).authorise(HousePermissions.HOLIDAY_MODE)!!)
        actions.toggle { opened += it }

        assertThat(opened).isEmpty()
        assertThat(rig.house.holiday.value).isTrue()
        assertThat(rig.lights.calls).containsExactly(true)
    }

    /** Fails if a tap on the on tile opens the sheet instead of turning off, or turns off without the PIN. */
    @Test
    fun whileOnAPinTurnsItOff() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADMIN)
        rig.loaded()
        rig.house.turnOn(back, access.authorise(HousePermissions.HOLIDAY_MODE)!!)
        access.asked.clear()

        actions(access, rig).toggle { opened += it }

        assertThat(access.asked).containsExactly(listOf("holiday.mode"))
        assertThat(opened).isEmpty()
        assertThat(rig.house.holiday.value).isFalse()
        assertThat(rig.lights.calls).containsExactly(true, false).inOrder()
    }

    /** 11 §5.2 "fresh PIN every time": fails if a second tap rides on the first one's PIN. */
    @Test
    fun everyTapAsksAgain() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        val actions = actions(access, rig)

        actions.toggle { opened += it }
        actions.toggle { opened += it }

        assertThat(access.asked).hasSize(2)
        assertThat(opened).hasSize(2)
    }

    /** A turn-off while a turn-on runs is dropped by the house, so the tile asks no PIN for it either. */
    @Test
    fun aTapWhileTurningAsksNoPin() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        val gate = CompletableDeferred<Unit>()
        rig.lights.gate = gate
        val actions = actions(access, rig)
        val turning = launch { rig.house.turnOn(back, TestAccess(Role.ADULT).authorise(HousePermissions.HOLIDAY_MODE)!!) }
        runCurrent()
        assertThat(rig.house.running.value).isEqualTo(HolidayRun.TURNING_ON)

        actions.toggle { opened += it }

        assertThat(access.asked).isEmpty()
        assertThat(opened).isEmpty()
        gate.complete(Unit)
        turning.join()
    }

    /** Before the record is read the tile can't tell on from off, so a tap asks nothing. */
    @Test
    fun aTapBeforeTheRecordIsReadAsksNothing() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)

        actions(access, rig).toggle { opened += it }

        assertThat(access.asked).isEmpty()
    }

    @Test
    fun theCalendarsPickIsThePicksBackDate() = runTest {
        val rig = rig()

        val pick = actions(TestAccess(Role.ADULT), rig, FakeEvents(listOf(HouseRig.CORNWALL))).pick()

        assertThat(pick).isEqualTo(BackDatePick("Cornwall", HouseRig.TODAY.plusDays(4)))
        assertThat(actions(TestAccess(Role.ADULT), rig, null).pick()).isNull()
        assertThat(actions(TestAccess(Role.ADULT), rig, FakeEvents()).pick()).isNull()
    }

    /** Fails if the caller going away mid turn-off (the tile leaving Home) leaves Holiday mode on with some steps undone. */
    @Test
    fun turningOffCarriesOnWhenTheCallerGoesAway() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        rig.house.turnOn(back, access.authorise(HousePermissions.HOLIDAY_MODE)!!)
        val gate = CompletableDeferred<Unit>()
        rig.lights.gate = gate
        val tap = launch { actions(access, rig).toggle { opened += it } }
        runCurrent()
        assertThat(rig.house.running.value).isEqualTo(HolidayRun.TURNING_OFF)

        tap.cancel()
        gate.complete(Unit)
        runCurrent()

        assertThat(rig.house.holiday.value).isFalse()
        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
    }

    /** Review Focus 5. Fails if Party mode being on still asks for a PIN, or the line is missing, stale or kept after a tap. */
    @Test
    fun partyOnRefusesBeforeThePin() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        val actions = actions(access, rig)
        party.value = true
        assertThat(actions.refusal.current()).isNull()

        actions.toggle { opened += it }

        assertThat(access.asked).isEmpty()
        assertThat(opened).isEmpty()
        assertThat(actions.refusal.current()).isEqualTo("Turn Party mode off first.")

        party.value = false
        assertThat(actions.refusal.current()).isNull()
        actions.toggle { opened += it }
        assertThat(access.asked).hasSize(1)
        party.value = true
        assertThat(actions.refusal.current()).isNull()
    }

    /** The line is a state, not an event: it is on while Party mode is and off once it isn't, without another tap. */
    @Test
    fun theRefusalFollowsPartyModeBothWays() = runTest {
        val rig = rig()
        rig.loaded()
        val actions = actions(TestAccess(Role.ADULT), rig)
        val seen = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { actions.refusal.line.toList(seen) }
        party.value = true

        actions.toggle { opened += it }
        party.value = false
        runCurrent()

        assertThat(seen).containsExactly(null, "Turn Party mode off first.", null).inOrder()
    }

    /** Fails if ending Holiday mode is refused because Party mode is on too. */
    @Test
    fun turningOffIsNeverRefused() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        rig.house.turnOn(back, access.authorise(HousePermissions.HOLIDAY_MODE)!!)
        party.value = true
        access.asked.clear()

        actions(access, rig).toggle { opened += it }

        assertThat(access.asked).containsExactly(listOf("holiday.mode"))
        assertThat(rig.house.holiday.value).isFalse()
    }

    /** Review Focus 5. Fails if Party mode turned on while the sheet was open lets the confirm turn Holiday mode on. */
    @Test
    fun theConfirmChecksAgain() = runTest {
        val rig = rig()
        val access = TestAccess(Role.ADULT)
        rig.loaded()
        val actions = actions(access, rig)
        var by: Authorised? = null
        actions.toggle { by = it }
        party.value = true

        val turned = actions.turnOn(back, by!!)

        assertThat(turned).isFalse()
        assertThat(rig.lights.calls).isEmpty()
        assertThat(rig.house.holiday.value).isFalse()
        assertThat(actions.refusal.current()).isEqualTo("Turn Party mode off first.")

        party.value = false
        assertThat(actions.turnOn(back, by!!)).isTrue()
        assertThat(rig.house.holiday.value).isTrue()
        assertThat(actions.refusal.current()).isNull()
    }
}
