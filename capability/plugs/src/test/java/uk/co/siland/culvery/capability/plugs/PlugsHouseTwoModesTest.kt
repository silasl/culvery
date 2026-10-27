package uk.co.siland.culvery.capability.plugs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.plugs.ModeChoice.OFF
import uk.co.siland.culvery.capability.plugs.ModeChoice.ON
import uk.co.siland.culvery.capability.plugs.PlugPower.UNREACHABLE
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.capability.plugs.PlugPower.OFF as OFF_NOW
import uk.co.siland.culvery.capability.plugs.PlugPower.ON as ON_NOW

/** Holiday mode is A and Party mode is B; both list Lamp, which is off before each test unless it says otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlugsHouseTwoModesTest {
    private suspend fun rig(a: ModeChoice, b: ModeChoice, lamp: PlugPower = OFF_NOW) = PlugsRig(lamp(lamp)).also { rig ->
        rig.configure { it.adding(LAMP).with(HOLIDAY, LAMP, a).with(PARTY, LAMP, b) }
    }

    private fun PlugsRig.lampIsOn() = system.states.value.getValue(LAMP).power == ON_NOW

    private suspend fun PlugsRig.note() = prefs().notes[LAMP]

    /** 12 §2.12: fails if the Lamp goes back before the last mode ends, or isn't put back after it. */
    @Test
    fun sameSettingAThenBEndingAThenB() = runTest {
        val rig = rig(ON, ON)
        val house = rig.house()
        house.start(HOLIDAY)
        house.start(PARTY)

        house.leave(HOLIDAY)
        assertThat(rig.lampIsOn()).isTrue()
        house.leave(PARTY)

        assertThat(rig.lampIsOn()).isFalse()
        assertThat(rig.system.switches).containsExactly(LAMP to true, LAMP to false).inOrder()
    }

    /** 12 §2.12: fails if ending the newer mode first changes the Lamp, or the older mode doesn't put it back. */
    @Test
    fun sameSettingEndingBThenA() = runTest {
        val rig = rig(ON, ON)
        val house = rig.house()
        house.start(HOLIDAY)
        house.start(PARTY)

        house.leave(PARTY)
        assertThat(rig.lampIsOn()).isTrue()
        house.leave(HOLIDAY)

        assertThat(rig.lampIsOn()).isFalse()
        assertThat(rig.system.switches).containsExactly(LAMP to true, LAMP to false).inOrder()
    }

    /** 12 §2.12: fails if the older mode ending switches the Lamp, or the newest doesn't decide it while both hold. */
    @Test
    fun oppositeSettingsEndingAThenB() = runTest {
        val rig = rig(ON, OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        house.start(PARTY)
        assertThat(rig.lampIsOn()).isFalse()
        assertThat(rig.system.switches).containsExactly(LAMP to true, LAMP to false).inOrder()

        house.leave(HOLIDAY)
        assertThat(rig.system.switches).hasSize(2)
        house.leave(PARTY)

        assertThat(rig.lampIsOn()).isFalse()
        assertThat(rig.system.switches).hasSize(2)
        assertThat(rig.note()).isNull()
    }

    /** 12 §2.12: fails if ending the newest mode doesn't hand the Lamp to the one still holding it. */
    @Test
    fun oppositeSettingsEndingBThenA() = runTest {
        val rig = rig(ON, OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        house.start(PARTY)

        house.leave(PARTY)
        assertThat(rig.lampIsOn()).isTrue()
        house.leave(HOLIDAY)

        assertThat(rig.lampIsOn()).isFalse()
    }

    /** 12 §2.7: fails if a hand switch is overridden by either mode ending. */
    @Test
    fun aHandChangeWinsOverBothModes() = runTest {
        val rig = rig(ON, ON)
        val house = rig.house()
        house.start(HOLIDAY)
        house.start(PARTY)
        rig.power(LAMP, OFF_NOW)

        house.leave(HOLIDAY)
        assertThat(rig.note()).isNull()
        house.leave(PARTY)

        assertThat(rig.system.switches).containsExactly(LAMP to true)
    }

    /** 12 §2.7: fails if a plug the other mode hasn't switched yet is taken for a hand change. */
    @Test
    fun aPendingPlugInTheOtherModeIsNotAHandChange() = runTest {
        val rig = rig(ON, OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        house.begin(PARTY)
        rig.system.results += PlugResult.NotSwitched
        assertThat(house.enter(PARTY)).isInstanceOf(HouseStep.Failed::class.java)

        house.leave(HOLIDAY)
        assertThat(rig.note()).isEqualTo(PlugNote(false, listOf(Holder(PARTY, false))))
        assertThat(house.enter(PARTY)).isEqualTo(HouseStep.Done)

        assertThat(rig.lampIsOn()).isFalse()
    }

    /** 12 §2.12: fails if an older mode reaching a plug late switches it over the newer mode's setting. */
    @Test
    fun aRetryByAnOlderHolderOnlyMarksReached() = runTest {
        val rig = rig(ON, OFF, lamp = ON_NOW)
        val house = rig.house()
        house.begin(HOLIDAY)
        house.start(PARTY)

        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false)
        assertThat(rig.lampIsOn()).isFalse()
    }

    /** 12 §2.12: fails if a mode reaching a plug late is placed by when it got there, not when it began. */
    @Test
    fun aLateReachingModeIsPlacedByWhenItBegan() = runTest {
        val rig = rig(ON, OFF, lamp = UNREACHABLE)
        val house = rig.house()
        assertThat(house.start(HOLIDAY)).isInstanceOf(HouseStep.Failed::class.java)
        rig.power(LAMP, ON_NOW)
        house.start(PARTY)
        assertThat(rig.system.switches).containsExactly(LAMP to false)

        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.note()).isEqualTo(PlugNote(true, listOf(Holder(HOLIDAY, true), Holder(PARTY, false))))
        assertThat(rig.system.switches).hasSize(1)
        assertThat(rig.lampIsOn()).isFalse()
    }

    /** 12 §5.4: fails if a mode that can't put a plug back drops the note of the mode still holding it. */
    @Test
    fun unreachableAtLeaveWithAnotherHolderKeepsTheNote() = runTest {
        val rig = rig(ON, OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        house.start(PARTY)
        rig.power(LAMP, UNREACHABLE)

        assertThat(house.leave(HOLIDAY)).isInstanceOf(HouseStep.Failed::class.java)

        assertThat(rig.note()).isEqualTo(PlugNote(false, listOf(Holder(PARTY, false))))
    }

    /** 12 §2.7: fails if a hand change made while one mode holds the plug is kept as that mode's note for the next. */
    @Test
    fun aHandChangeBeforeTheSecondModeStartsAfresh() = runTest {
        val rig = rig(ON, ON)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.power(LAMP, OFF_NOW)

        house.start(PARTY)
        assertThat(rig.note()).isEqualTo(PlugNote(false, listOf(Holder(PARTY, true))))
        assertThat(rig.lampIsOn()).isTrue()
        house.leave(HOLIDAY)
        assertThat(rig.lampIsOn()).isTrue()
        house.leave(PARTY)

        assertThat(rig.lampIsOn()).isFalse()
    }

    /** 12 §5.1: fails if ready answers for another mode's list. */
    @Test
    fun readyIsPerMode() = runTest {
        val rig = PlugsRig(lamp(OFF_NOW))
        rig.configure { it.adding(LAMP).with(HOLIDAY, LAMP, OFF) }
        val house = rig.house()

        assertThat(house.ready(PARTY).first()).isFalse()
        assertThat(house.ready(HOLIDAY).first()).isTrue()
    }
}
