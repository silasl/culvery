package uk.co.siland.culvery.capability.plugs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.capability.plugs.PlugPower.OFF as OFF_NOW
import uk.co.siland.culvery.capability.plugs.PlugPower.ON as ON_NOW

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlugsHouseTest {
    private val offLine = "Lamp didn’t switch off. It’ll keep trying."

    private fun keys(rig: PlugsRig) = rig.store.state.value!!.asMap().keys.map { it.name }

    private suspend fun refused(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: IllegalArgumentException) {
        e
    }

    /** 12 §2.13: fails if another permission, or an unknown mode, can begin a mode, or a refused begin leaves a run. */
    @Test
    fun beginNeedsTheModesOwnGrant() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()

        assertThat(refused { house.begin(HOLIDAY, grant("plugs.manage")) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(refused { house.begin("sleep", grant("sleep.mode")) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(refused { house.begin(HOLIDAY, grant("party.mode")) }).isInstanceOf(IllegalArgumentException::class.java)

        assertThat(rig.prefs().runs).isEmpty()
    }

    /** 12 §5.3: fails if enter without a begun mode reads or switches anything. */
    @Test
    fun noRunNoSwitch() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)

        assertThat(rig.house().enter(HOLIDAY)).isNull()

        assertThat(rig.system.reads).isEmpty()
        assertThat(rig.system.switches).isEmpty()
    }

    /** 12 §2.11: fails if a retry after a restart needs a grant, or skips a plug that needs a PIN. */
    @Test
    fun aRetryAfterARestartWithARunSwitchesEvenPinPlugs() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(UNREACHABLE))
        rig.holiday(fan = OFF)
        rig.configure { it.copy(needsPin = setOf(FAN)) }

        assertThat(rig.house().start(HOLIDAY)).isEqualTo(HouseStep.Failed("Fan didn’t switch off. It’ll keep trying."))
        rig.power(FAN, ON_NOW)
        val again = rig.house().enter(HOLIDAY)

        assertThat(again).isEqualTo(HouseStep.Done)
        assertThat(rig.system.switches).containsExactly(FAN to false)
    }

    /** 12 §5.2: fails if a second begin takes a new snapshot. */
    @Test
    fun beginTwiceKeepsTheFirstSnapshot() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()

        house.begin(HOLIDAY)
        rig.configure { it.with(HOLIDAY, LAMP, ON) }
        house.begin(HOLIDAY)

        val prefs = rig.prefs()
        assertThat(prefs.runs.getValue(HOLIDAY)).isEqualTo(ModeRun(0, mapOf(LAMP to false)))
        assertThat(prefs.next).isEqualTo(1)
    }

    /** 12 §5.2: fails if the note isn't stored before the switch is sent, or records anything but the state before. */
    @Test
    fun enterNotesThenSwitches() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.begin(HOLIDAY)
        val gate = CompletableDeferred<Unit>().also { rig.system.switchGates[LAMP] = it }

        val enter = async { house.enter(HOLIDAY) }
        runCurrent()

        assertThat(rig.system.switches).containsExactly(LAMP to false)
        assertThat(rig.prefs().notes.getValue(LAMP)).isEqualTo(PlugNote(true, listOf(Holder(HOLIDAY, false))))
        gate.complete(Unit)
        assertThat(enter.await()).isEqualTo(HouseStep.Done)
    }

    /** 12 §5.3: fails if a plug already as wanted is switched, or isn't noted. */
    @Test
    fun aPlugAlreadyAsWantedIsNotedNotSwitched() = runTest {
        val rig = PlugsRig(lamp(OFF_NOW))
        rig.holiday(lamp = OFF)

        assertThat(rig.house().start(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).isEmpty()
        assertThat(rig.prefs().notes.getValue(LAMP)).isEqualTo(PlugNote(false, listOf(Holder(HOLIDAY, false))))
        assertThat(rig.prefs().runs.getValue(HOLIDAY).pending).isEmpty()
    }

    /** 12 §2.4: fails if a plug set to Leave is read or switched. */
    @Test
    fun leavePlugsAreUntouched() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW))
        rig.holiday(lamp = OFF, fan = LEAVE)
        val house = rig.house()

        house.start(HOLIDAY)
        house.leave(HOLIDAY)

        assertThat(rig.system.reads).doesNotContain(FAN)
        assertThat(rig.system.switches.map { it.first }).doesNotContain(FAN)
    }

    /** 12 §5.3: fails if the plugs switch one after the other, which would make a turn-on wait for each. */
    @Test
    fun plugsSwitchInParallel() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW))
        rig.holiday(lamp = OFF, fan = OFF)
        val house = rig.house()
        house.begin(HOLIDAY)
        val gates = listOf(LAMP, FAN).map { id -> CompletableDeferred<Unit>().also { rig.system.switchGates[id] = it } }

        val enter = async { house.enter(HOLIDAY) }
        runCurrent()

        assertThat(rig.system.switches).containsExactly(LAMP to false, FAN to false)
        gates.forEach { it.complete(Unit) }
        assertThat(enter.await()).isEqualTo(HouseStep.Done)
    }

    /** 12 §2.6: fails if leave switches to anything but the state before, or leaves the note, started or pending behind. */
    @Test
    fun leavePutsBackToBefore() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)

        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false, LAMP to true).inOrder()
        assertThat(keys(rig)).containsNoneOf("note.$LAMP", "started.$HOLIDAY", "pending.$HOLIDAY")
    }

    /** 12 §2.7: fails if a plug someone switched by hand is switched back, or keeps its note. */
    @Test
    fun aHandSwitchedPlugIsLeftAndItsNoteDropped() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.power(LAMP, ON_NOW)

        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false)
        assertThat(rig.prefs().notes).isEmpty()
    }

    /** 12 §5.3: fails if a plug never reached gets a note, or is switched at leave. */
    @Test
    fun aNeverReachedPlugIsLeft() = runTest {
        val rig = PlugsRig(lamp(UNREACHABLE))
        rig.holiday(lamp = OFF)
        val house = rig.house()

        assertThat(house.start(HOLIDAY)).isEqualTo(HouseStep.Failed(offLine))
        assertThat(rig.prefs().notes).isEmpty()
        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).isEmpty()
    }

    /** 12 §5.4: fails if a plug that can't be reached at leave isn't named, or keeps its note. */
    @Test
    fun unreachableAtLeaveIsFailedAndDropped() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.power(LAMP, UNREACHABLE)

        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Failed("Lamp didn’t switch back on. Switch it on the Plugs tab."))

        assertThat(rig.prefs().notes).isEmpty()
        assertThat(rig.system.switches).containsExactly(LAMP to false)
    }

    /** 12 §5.4: fails if a plug whose state isn't known at leave is taken for a hand change, or switched. */
    @Test
    fun anUnknownPlugAtLeaveIsFailedNotAHandChange() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.power(LAMP, UNKNOWN)

        assertThat(house.leave(HOLIDAY)).isInstanceOf(HouseStep.Failed::class.java)

        assertThat(rig.system.switches).containsExactly(LAMP to false)
        assertThat(rig.prefs().notes).isEmpty()
    }

    /** 12 §5.3: fails if a retry reads or switches a plug already reached. */
    @Test
    fun aRetrySwitchesOnlyUnreachedPlugs() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(UNREACHABLE))
        rig.holiday(lamp = OFF, fan = OFF)
        val house = rig.house()
        assertThat(house.start(HOLIDAY)).isEqualTo(HouseStep.Failed("Fan didn’t switch off. It’ll keep trying."))
        rig.forgetCalls()
        rig.power(FAN, ON_NOW)

        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.reads).containsExactly(FAN)
        assertThat(rig.system.switches).containsExactly(FAN to false)
    }

    /** 12 §5.3: fails if a switch Home Assistant didn't show is counted reached, or the retry re-reads before. */
    @Test
    fun aSwitchNotSeenIsTriedAgain() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        rig.system.results += PlugResult.NotSwitched

        assertThat(house.start(HOLIDAY)).isEqualTo(HouseStep.Failed(offLine))
        assertThat(rig.prefs().runs.getValue(HOLIDAY).pending).containsExactly(LAMP, false)
        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false, LAMP to false)
        assertThat(rig.prefs().notes.getValue(LAMP).before).isTrue()
    }

    /** Review Focus 4: fails if a switch that landed while the app was cut off is taken for the state before. */
    @Test
    fun aCancelledEnterThenANewHouseKeepsTheFirstBefore() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        rig.house().begin(HOLIDAY)
        rig.system.switchGates[LAMP] = CompletableDeferred()
        val enter = launch { rig.house().enter(HOLIDAY) }
        runCurrent()
        enter.cancelAndJoin()
        rig.system.switchGates.clear()
        rig.power(LAMP, OFF_NOW)

        assertThat(rig.house().enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false)
        assertThat(rig.prefs().notes.getValue(LAMP).before).isTrue()
    }

    /** Review Focus 4: fails if a leave cut short, run again, skips the plug it hadn't reached or switches the first twice. */
    @Test
    fun aRestartMidLeaveStillPutsBack() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW))
        rig.holiday(lamp = OFF, fan = OFF)
        rig.house().start(HOLIDAY)
        rig.system.switchGates[FAN] = CompletableDeferred()
        val leave = launch { rig.house().leave(HOLIDAY) }
        runCurrent()
        assertThat(rig.system.switches).contains(LAMP to true)
        leave.cancelAndJoin()
        rig.system.switchGates.clear()

        assertThat(rig.house().leave(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches.count { it == LAMP to true }).isEqualTo(1)
        assertThat(rig.system.switches.count { it == FAN to true }).isEqualTo(2)
        assertThat(rig.system.states.value.getValue(FAN).power).isEqualTo(ON_NOW)
        assertThat(rig.prefs().runs).isEmpty()
        assertThat(rig.prefs().notes).isEmpty()
    }

    /** Review Focus 4: fails if a plug whose pending entry couldn't be cleared is lost or switched again. */
    @Test
    fun aStoreWriteFailingMidEnterIsRetried() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.begin(HOLIDAY)
        // The note, the check before the switch, then the pending entry.
        rig.store.failOnWrite = rig.store.writeCount + 3

        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Failed(offLine))
        assertThat(rig.prefs().runs.getValue(HOLIDAY).pending).containsExactly(LAMP, false)
        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false)
    }

    /** 12 §5.2: fails if the notes don't outlive the process. */
    @Test
    fun notesSurviveARestart() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        rig.house().start(HOLIDAY)

        assertThat(rig.house().leave(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false, LAMP to true).inOrder()
    }

    /** Fails if a switch that throws escapes enter, or the plug is dropped from pending. */
    @Test
    fun aThrowingSwitchIsNotReached() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        rig.system.throwOnSwitch = true

        assertThat(house.start(HOLIDAY)).isEqualTo(HouseStep.Failed(offLine))
        assertThat(rig.prefs().runs.getValue(HOLIDAY).pending).containsExactly(LAMP, false)
        rig.system.throwOnSwitch = false

        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)
    }

    /** 12 §5.2: fails if a list edited mid-mode changes what is retried or put back. */
    @Test
    fun editingTheListMidModeDoesntChangeWhatIsPutBack() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.configure { it.with(HOLIDAY, LAMP, LEAVE).with(HOLIDAY, FAN, OFF) }
        rig.forgetCalls()

        house.enter(HOLIDAY)
        house.leave(HOLIDAY)

        assertThat(rig.system.reads).doesNotContain(FAN)
        assertThat(rig.system.switches).containsExactly(LAMP to true)
    }

    /** Review Focus 2: fails if a removed plug is put back at leave. */
    @Test
    fun removeDropsTheNote() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.configure { it.without(LAMP) }

        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.switches).containsExactly(LAMP to false)
    }

    /** Review Focus 2: fails if a retry reads a plug removed since. */
    @Test
    fun removingAPlugMidModeStopsItsRetries() = runTest {
        val rig = PlugsRig(lamp(UNREACHABLE))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        assertThat(house.start(HOLIDAY)).isInstanceOf(HouseStep.Failed::class.java)
        rig.configure { it.without(LAMP) }
        rig.forgetCalls()

        assertThat(house.enter(HOLIDAY)).isEqualTo(HouseStep.Done)

        assertThat(rig.system.reads).isEmpty()
    }

    /** Review Focus 2: fails if a remove landing after the note's write but before the switch still sends it, or leaves entries. */
    @Test
    fun aRemoveBetweenTheNoteAndTheSwitchSendsNothing() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.begin(HOLIDAY)
        val gate = CompletableDeferred<Unit>().also { rig.store.writeGate = it }

        val enter = async { house.enter(HOLIDAY) }
        runCurrent()
        val remove = launch { rig.configure { it.without(LAMP) } }
        runCurrent()
        gate.complete(Unit)
        joinAll(remove)

        assertThat(enter.await()).isEqualTo(HouseStep.Done)
        assertThat(rig.system.switches).isEmpty()
        assertThat(rig.prefs().notes).isEmpty()
        assertThat(rig.prefs().runs.getValue(HOLIDAY).pending).isEmpty()
    }

    /** Review Focus 1: fails if a plug Home Assistant no longer has is noted, or dropped from retries. */
    @Test
    fun aGonePlugAtEnterIsRetriedNotNoted() = runTest {
        val rig = PlugsRig(lamp(GONE))
        rig.holiday(lamp = OFF)

        assertThat(rig.house().start(HOLIDAY)).isEqualTo(HouseStep.Failed(offLine))

        assertThat(rig.prefs().notes).isEmpty()
        assertThat(rig.prefs().runs.getValue(HOLIDAY).pending).containsExactly(LAMP, false)
        assertThat(rig.system.switches).isEmpty()
    }

    /** Review Focus 1: fails if a plug gone at leave isn't named, or sticks in its note. */
    @Test
    fun aGonePlugAtLeaveIsFailed() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.power(LAMP, GONE)

        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Failed("Lamp didn’t switch back on. Switch it on the Plugs tab."))

        assertThat(rig.prefs().notes).isEmpty()
        assertThat(rig.system.switches).containsExactly(LAMP to false)
    }

    /** Review Focus 5: fails if Home Assistant being down at leave doesn't say how many, or leaves a note or a run. */
    @Test
    fun leaveWithHomeAssistantDownSaysHowMany() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW))
        rig.holiday(lamp = OFF, fan = OFF)
        val house = rig.house()
        house.start(HOLIDAY)
        rig.power(LAMP, UNREACHABLE)
        rig.power(FAN, UNREACHABLE)

        assertThat(house.leave(HOLIDAY)).isEqualTo(HouseStep.Failed("2 plugs didn’t switch back. Switch them on the Plugs tab."))

        assertThat(rig.prefs().notes).isEmpty()
        assertThat(rig.prefs().runs).isEmpty()
    }

    /** Fails if leave runs while an enter is still switching. */
    @Test
    fun leaveWaitsForARunningEnter() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday(lamp = OFF)
        val house = rig.house()
        house.begin(HOLIDAY)
        val gate = CompletableDeferred<Unit>().also { rig.system.switchGates[LAMP] = it }
        val enter = async { house.enter(HOLIDAY) }
        runCurrent()

        val leave = async { house.leave(HOLIDAY) }
        runCurrent()
        assertThat(leave.isCompleted).isFalse()
        assertThat(rig.system.switches).containsExactly(LAMP to false)
        gate.complete(Unit)

        assertThat(enter.await()).isEqualTo(HouseStep.Done)
        assertThat(leave.await()).isEqualTo(HouseStep.Done)
        assertThat(rig.system.switches).containsExactly(LAMP to false, LAMP to true).inOrder()
    }

    /** 12 §5.2: fails if a hold left by a run that never ended survives the next begin. */
    @Test
    fun aStaleHoldIsClearedAtBegin() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW))
        rig.holiday(lamp = OFF)
        rig.configure {
            it.copy(
                notes = mapOf(
                    LAMP to PlugNote(true, listOf(Holder(HOLIDAY, false))),
                    FAN to PlugNote(true, listOf(Holder(PARTY, false), Holder(HOLIDAY, false))),
                ),
            )
        }

        rig.house().begin(HOLIDAY)

        val prefs = rig.prefs()
        assertThat(prefs.notes).containsExactly(FAN, PlugNote(true, listOf(Holder(PARTY, false))))
        assertThat(prefs.runs).containsKey(HOLIDAY)
    }

    /** 12 §5.1: fails if ready ignores the list, the mode asked about, or the system. */
    @Test
    fun readyFollowsTheModesList() = runTest {
        val rig = PlugsRig(lamp(ON_NOW))
        rig.holiday()
        val house = rig.house()
        assertThat(house.ready(HOLIDAY).first()).isFalse()

        rig.configure { it.with(HOLIDAY, LAMP, OFF) }

        assertThat(house.ready(HOLIDAY).first()).isTrue()
        assertThat(house.ready(PARTY).first()).isFalse()
        assertThat(PlugsHouse(rig.settings, null, MODES).ready(HOLIDAY).first()).isFalse()
    }

    /** 12 §5.1: fails if the preview takes its names from the tab's flow instead of a read, or lists Leave plugs, or reorders. */
    @Test
    fun previewNamesComeFromARead() = runTest {
        val rig = PlugsRig(lamp(ON_NOW), fan(ON_NOW), desk(OFF_NOW))
        rig.configure { it.adding(LAMP).adding(FAN).adding(DESK).with(HOLIDAY, LAMP, OFF).with(HOLIDAY, DESK, ON) }
        rig.system.added.value = emptyList()

        assertThat(rig.house().preview(HOLIDAY)).containsExactly(PlugChange("Lamp", false), PlugChange("Desk", true)).inOrder()
    }
}
