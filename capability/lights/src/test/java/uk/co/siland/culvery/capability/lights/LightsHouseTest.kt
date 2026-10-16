package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 7 §4.4, §4.5: All lights off and Holiday mode, one run at a time, stopping only what Culvery started. */
@RunWith(AndroidJUnit4::class)
class LightsHouseTest {
    @get:Rule val lights = LightsFixture()
    private val settings get() = lights.settings
    private val system = TestLightsSystem(listOf(room("Lounge", on = true), room("Garden", on = true)))
    private val house by lazy { LightsHouse({ system }, settings) }

    private suspend fun prefs() = soon { settings.prefs.first() }

    private suspend fun choose(vararg ids: String) = soon { settings.setHolidayAutomations(ids.toSet()) }

    private suspend fun waitFor(call: String) = soon { while (call !in system.calls) delay(5) }

    private fun gate(call: String) = CompletableDeferred<Unit>().also { system.gates[call] = it }

    /** Fails if the left-out rooms aren't passed, or All lights off doesn't show as running while it runs. */
    @Test
    fun allLightsOffPassesTheLeftOutRooms() = runTest {
        soon { settings.setLeftOut(setOf("Hall-id", "Garden-id")) }
        val open = gate("allOff [Garden-id, Hall-id]")
        val run = async { house.allOff() }
        waitFor("allOff [Garden-id, Hall-id]")
        assertThat(house.running.value).isEqualTo(HouseRun.ALL_OFF)
        open.complete(Unit)
        assertThat(run.await()).isEqualTo(AllOffResult.Done)
        assertThat(house.running.value).isNull()
        assertThat(system.calls).containsExactly("allOff [Garden-id, Hall-id]")
    }

    /** Fails if the rooms that stayed on aren't counted in the result. */
    @Test
    fun allLightsOffSaysHowManyStayedOn() = runTest {
        system.allOffAnswer = AllOffResult.RoomsLeftOn(2)
        assertThat(house.allOff()).isEqualTo(AllOffResult.RoomsLeftOn(2))
    }

    /** Fails if the lights don't go off first, an automation that is off isn't started, or a started one isn't recorded. */
    @Test
    fun holidayOnTurnsTheLightsOffThenStartsWhatIsOff() = runTest {
        choose("mimic-id", "away-id")
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.Done)
        assertThat(system.calls).containsExactly(
            "allOff []", "automations", "setAutomation mimic-id true", "setAutomation away-id true",
        ).inOrder()
        val prefs = prefs()
        assertThat(prefs.holidayOn).isTrue()
        assertThat(prefs.holidayStarted).containsExactly("mimic-id", "away-id")
    }

    /** Fails if Holiday mode is saved on only after the automations, so a run cut short there shows it off. */
    @Test
    fun holidayModeIsSavedOnAsSoonAsTheLightsAreOff() = runTest {
        choose("mimic-id")
        val open = gate("setAutomation mimic-id true")
        val run = async { house.setHoliday(true, adult()) }
        waitFor("setAutomation mimic-id true")
        assertThat(prefs().holidayOn).isTrue()
        open.complete(Unit)
        assertThat(run.await()).isEqualTo(HolidayResult.Done)
    }

    /** Fails if All lights off refused by Hue still saves Holiday mode on or goes on to the automations. */
    @Test
    fun aRefusedAllOffLeavesHolidayModeOff() = runTest {
        choose("mimic-id")
        system.allOffAnswer = AllOffResult.Refused
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.Refused)
        assertThat(prefs().holidayOn).isFalse()
        assertThat(system.calls).containsExactly("allOff []")
    }

    /** Fails if an automation already on is recorded, started again, or stopped when Holiday mode ends. */
    @Test
    fun anAutomationAlreadyOnIsLeftOnWhenHolidayModeEnds() = runTest {
        system.automationList = listOf(MIMIC, AWAY.copy(on = true))
        choose("mimic-id", "away-id")
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.Done)
        assertThat(system.calls).doesNotContain("setAutomation away-id true")
        assertThat(prefs().holidayStarted).containsExactly("mimic-id")
        assertThat(house.setHoliday(false, adult())).isEqualTo(HolidayResult.Done)
        assertThat(system.calls).contains("setAutomation mimic-id false")
        assertThat(system.calls).doesNotContain("setAutomation away-id false")
        assertThat(system.automationList.single { it.id == "away-id" }.on).isTrue()
    }

    /** Fails if Holiday mode off stops what is ticked now rather than what Culvery started. */
    @Test
    fun anUntickedAutomationCulveryStartedIsStillStopped() = runTest {
        choose("mimic-id")
        house.setHoliday(true, adult())
        choose()
        assertThat(house.setHoliday(false, adult())).isEqualTo(HolidayResult.Done)
        assertThat(system.calls).contains("setAutomation mimic-id false")
        val prefs = prefs()
        assertThat(prefs.holidayStarted).isEmpty()
        assertThat(prefs.holidayOn).isFalse()
    }

    /** Fails if no automations chosen asks the bridge for them, or doesn't save Holiday mode on and then off. */
    @Test
    fun noAutomationsChosenIsLightsOffAndOn() = runTest {
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.Done)
        assertThat(prefs().holidayOn).isTrue()
        assertThat(house.setHoliday(false, adult())).isEqualTo(HolidayResult.Done)
        assertThat(prefs().holidayOn).isFalse()
        assertThat(system.calls).containsExactly("allOff []")
    }

    /** Fails if a start the read-back didn't confirm is recorded, turns Holiday mode off, or loses its name. */
    @Test
    fun oneThatIsntConfirmedIsOnWithItsName() = runTest {
        choose("mimic-id")
        system.results["mimic-id"] = AutomationResult.NOT_CONFIRMED
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.Partly(0, listOf("mimic-id")))
        val prefs = prefs()
        assertThat(prefs.holidayOn).isTrue()
        assertThat(prefs.holidayStarted).isEmpty()
        assertThat(house.nameOf("mimic-id")).isEqualTo("Mimic presence")
        assertThat(house.nameOf("gone-id")).isNull()
    }

    /** Fails if a chosen automation no longer on the bridge is sent a command or counted as not started. */
    @Test
    fun aGoneAutomationIsSkipped() = runTest {
        choose("mimic-id", "gone-id")
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.Done)
        assertThat(system.calls.filter { it.startsWith("setAutomation") }).containsExactly("setAutomation mimic-id true")
        assertThat(prefs().holidayStarted).containsExactly("mimic-id")
    }

    /** Fails if a bridge that can't be reached still changes Holiday mode either way, or the run goes on to the automations. */
    @Test
    fun anUnreachableBridgeChangesNothing() = runTest {
        choose("mimic-id")
        system.allOffAnswer = AllOffResult.CantReach(LightsProblem.UNREACHABLE)
        assertThat(house.setHoliday(true, adult())).isEqualTo(HolidayResult.CantReach(LightsProblem.UNREACHABLE))
        assertThat(prefs().holidayOn).isFalse()
        assertThat(system.calls).containsExactly("allOff []")

        system.allOffAnswer = AllOffResult.Done
        house.setHoliday(true, adult())
        system.failures["automations"] = LightsUnreachableException(LightsProblem.OFFLINE)
        assertThat(house.setHoliday(false, adult())).isEqualTo(HolidayResult.CantReach(LightsProblem.OFFLINE))
        val prefs = prefs()
        assertThat(prefs.holidayOn).isTrue()
        assertThat(prefs.holidayStarted).containsExactly("mimic-id")
    }

    /** Fails if the bridge dropping after the lights went off leaves Holiday mode off or says nothing about the automations. */
    @Test
    fun aDropAfterTheLightsWentOffIsOnWithTheLine() = runTest {
        choose("mimic-id", "away-id")
        system.failures["automations"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        val result = house.setHoliday(true, adult())
        assertThat(result).isInstanceOf(HolidayResult.Partly::class.java)
        result as HolidayResult.Partly
        assertThat(result.roomsLeftOn).isEqualTo(0)
        assertThat(result.notStarted).containsExactly("mimic-id", "away-id")
        val prefs = prefs()
        assertThat(prefs.holidayOn).isTrue()
        assertThat(prefs.holidayStarted).isEmpty()
    }

    /** Fails if Holiday mode off touches the lights, or is saved off before the last automation has stopped. */
    @Test
    fun holidayOffStopsWhatCulveryStartedAndLeavesTheLights() = runTest {
        choose("mimic-id", "away-id")
        house.setHoliday(true, adult())
        system.calls.clear()
        val open = gate("setAutomation away-id false")
        val run = async { house.setHoliday(false, adult()) }
        waitFor("setAutomation away-id false")
        assertThat(prefs().holidayOn).isTrue()
        open.complete(Unit)
        assertThat(run.await()).isEqualTo(HolidayResult.Done)
        assertThat(system.calls).containsExactly("automations", "setAutomation mimic-id false", "setAutomation away-id false").inOrder()
        val prefs = prefs()
        assertThat(prefs.holidayOn).isFalse()
        assertThat(prefs.holidayStarted).isEmpty()
    }

    /** Fails if a stop that wasn't confirmed or was refused saves Holiday mode off or forgets the automation. */
    @Test
    fun aStopThatIsntConfirmedKeepsItOn() = runTest {
        choose("mimic-id")
        house.setHoliday(true, adult())
        system.results["mimic-id"] = AutomationResult.NOT_CONFIRMED
        assertThat(house.setHoliday(false, adult())).isEqualTo(HolidayResult.Refused)
        assertThat(prefs().holidayOn).isTrue()
        assertThat(prefs().holidayStarted).containsExactly("mimic-id")

        system.results.clear()
        system.failures["setAutomation mimic-id false"] = LightsRefusedException()
        assertThat(house.setHoliday(false, adult())).isEqualTo(HolidayResult.Refused)
        assertThat(prefs().holidayOn).isTrue()
        assertThat(prefs().holidayStarted).containsExactly("mimic-id")
    }

    /** Fails if a tap while an automation is read back starts a second run, or the button doesn't say "Turning on…" meanwhile. */
    @Test
    fun aSecondTapDuringTheReadBackIsIgnored() = runTest {
        choose("mimic-id")
        val open = gate("setAutomation mimic-id true")
        val first = async { house.setHoliday(null, adult()) }
        waitFor("setAutomation mimic-id true")
        assertThat(house.setHoliday(null, adult())).isEqualTo(HolidayResult.Busy)
        assertThat(house.allOff()).isNull()
        assertThat(house.running.value).isEqualTo(HouseRun.TURNING_ON)
        open.complete(Unit)
        assertThat(first.await()).isEqualTo(HolidayResult.Done)
        assertThat(house.running.value).isNull()
        assertThat(system.calls).containsExactly("allOff []", "automations", "setAutomation mimic-id true").inOrder()
    }

    /** Fails if a toggle doesn't read the saved record: turning on twice, or off when it is off. */
    @Test
    fun theToggleIsDecidedFromTheSavedRecord() = runTest {
        choose("mimic-id")
        assertThat(house.setHoliday(null, adult())).isEqualTo(HolidayResult.Done)
        assertThat(prefs().holidayOn).isTrue()
        system.calls.clear()
        assertThat(house.setHoliday(null, adult())).isEqualTo(HolidayResult.Done)
        assertThat(prefs().holidayOn).isFalse()
        assertThat(system.calls).containsExactly("automations", "setAutomation mimic-id false").inOrder()
    }

    /** Fails if a caller holding no Holiday mode permission can switch it, either way. */
    @Test
    fun aCallWithoutTheHolidayPermissionIsRefusedOutright() = runTest {
        assertThat(runCatching { house.setHoliday(true, nobody()) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { house.setHoliday(null, nobody()) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(system.calls).isEmpty()
        assertThat(prefs().holidayOn).isFalse()
    }

    /** Pin. Fails if the bridge reporting the automation stopped turns Holiday mode off, or makes Holiday mode off send a stop. */
    @Test
    fun holidayModeIsCulverysOwnRecord() = runTest {
        choose("mimic-id")
        house.setHoliday(true, adult())
        system.automationList = listOf(MIMIC, AWAY)
        assertThat(house.allOff()).isEqualTo(AllOffResult.Done)
        val prefs = prefs()
        assertThat(prefs.holidayOn).isTrue()
        assertThat(prefs.holidayStarted).containsExactly("mimic-id")
        system.calls.clear()
        assertThat(house.setHoliday(null, adult())).isEqualTo(HolidayResult.Done)
        assertThat(system.calls).containsExactly("automations")
        assertThat(prefs().holidayOn).isFalse()
    }
}
