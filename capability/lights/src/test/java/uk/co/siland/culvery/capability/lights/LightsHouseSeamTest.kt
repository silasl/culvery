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
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.test.soon

/** 11 §5.1: Holiday mode's way to Holiday lights. */
@RunWith(AndroidJUnit4::class)
class LightsHouseSeamTest {
    @get:Rule val lights = LightsFixture()
    private val settings get() = lights.settings
    private val system = TestLightsSystem(listOf(room("Lounge", on = true)))
    private val house by lazy { LightsHouse({ system }, settings) }
    private val seam by lazy { LightsHouseSeam(house, settings, LightsModes({ system })) }

    /** Fails if turning on with the record already on runs the lights again. */
    @Test
    fun alreadyOnIsLeftAlone() = runTest {
        soon { settings.setHolidayOn(true) }
        assertThat(seam.setHoliday(true, houseAdult())).isEqualTo(HouseStep.Done)
        assertThat(system.calls).isEmpty()
    }

    /** Fails if turning on from off doesn't run, or Partly (rooms left on) is reported as a failure. */
    @Test
    fun partlyIsDone() = runTest {
        system.allOffAnswer = AllOffResult.RoomsLeftOn(1)
        assertThat(seam.setHoliday(true, houseAdult())).isEqualTo(HouseStep.Done)
        assertThat(system.calls).containsExactly("allOff []")
        assertThat(soon { settings.prefs.first() }.holidayOn).isTrue()
    }

    /** Fails if a bridge that can't be reached is Done, or fails with no line. */
    @Test
    fun cantReachIsFailedWithItsLine() = runTest {
        system.allOffAnswer = AllOffResult.CantReach(LightsProblem.UNREACHABLE)
        assertThat(seam.setHoliday(true, houseAdult())).isEqualTo(HouseStep.Failed(problemLine(LightsProblem.UNREACHABLE)))
    }

    /** Fails if Hue refusing isn't Failed with its line. */
    @Test
    fun refusedIsFailedWithItsLine() = runTest {
        system.allOffAnswer = AllOffResult.Refused
        assertThat(seam.setHoliday(true, houseAdult())).isEqualTo(HouseStep.Failed(HUE_REFUSED))
    }

    /** 11 §5.4. Fails if turning off is skipped when the record says off: it still stops what Culvery started. */
    @Test
    fun offAlwaysRuns() = runTest {
        soon { settings.setHolidayOn(false) }
        soon { settings.setHolidayStarted(setOf("mimic-id")) }
        system.automationList = listOf(MIMIC.copy(on = true))
        assertThat(seam.setHoliday(false, houseAdult())).isEqualTo(HouseStep.Done)
        assertThat(system.calls).contains("setAutomation mimic-id false")
    }

    /** Fails if a run already holding the house is reported as Hue refusing, or the busy line is lost. */
    @Test
    fun busyIsFailed() = runTest {
        val open = CompletableDeferred<Unit>().also { system.gates["allOff []"] = it }
        val run = async { house.allOff() }
        soon { while ("allOff []" !in system.calls) delay(5) }
        assertThat(seam.setHoliday(true, houseAdult())).isEqualTo(HouseStep.Failed("The lights are busy. Culvery will try again in a few minutes."))
        open.complete(Unit)
        run.await()
    }

    /** Fails if ready is true before a bridge is paired, or doesn't follow pairing. */
    @Test
    fun readyFollowsTheBridge() = runTest {
        assertThat(seam.ready.first()).isFalse()
        soon { settings.connect("lights.hue", link()) }
        assertThat(seam.ready.first()).isTrue()
    }

    /** Fails if the seam's mode calls don't reach LightsModes over the same system. */
    @Test
    fun theModeCallsReachLightsModes() = runTest {
        system.rooms.value = listOf(room("Lounge", on = true, brightness = 40, scenes = listOf(RELAX), active = RELAX.id))
        val choice = RoomChoice("Lounge-id", "Lounge", LightChoice.Scene(RELAX.id, "Relax"))
        val direct = LightsModes({ system })
        assertThat(seam.lightOptions()).isEqualTo(direct.options())
        val before = seam.modeBefore(listOf(choice))
        assertThat(before).isEqualTo(direct.before(listOf(choice)))
        assertThat(before).hasSize(1)
        assertThat(seam.modeOn(before)).isEqualTo(direct.on(before))
        assertThat(seam.modeBack(before)).isEqualTo(direct.back(before))
        system.failures["recallScene Lounge-id relax-id"] = LightsRefusedException()
        assertThat(seam.modeOn(before)).containsExactly("Lounge")
    }

    /** Fails if the names aren't in the bridge's order, keep one the bridge no longer has, or read the bridge with none chosen. */
    @Test
    fun holidayAutomationNamesFollowTheBridge() = runTest {
        assertThat(seam.holidayAutomationNames()).isEmpty()
        assertThat(system.calls).doesNotContain("automations")

        soon { settings.setHolidayAutomations(setOf("away-id", "gone-id", "mimic-id")) }
        assertThat(seam.holidayAutomationNames()).containsExactly("Mimic presence", "Away from home").inOrder()
        assertThat(system.calls).doesNotContain("setAutomation mimic-id true")
    }

    /** Fails if a bridge that can't be read throws or answers anything but null. */
    @Test
    fun holidayAutomationNamesAreNullWhenTheBridgeCantBeRead() = runTest {
        soon { settings.setHolidayAutomations(setOf("mimic-id")) }
        system.failures["automations"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        assertThat(seam.holidayAutomationNames()).isNull()
        system.failures["automations"] = LightsRefusedException()
        assertThat(seam.holidayAutomationNames()).isNull()
    }
}
