package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.plugin.test.soon

/** 11 §5.1: Holiday mode's way to Holiday lights. */
@RunWith(AndroidJUnit4::class)
class LightsHouseSeamTest {
    @get:Rule val lights = LightsFixture()
    private val settings get() = lights.settings
    private val system = TestLightsSystem(listOf(room("Lounge", on = true)))
    private val house by lazy { LightsHouse({ system }, settings) }
    private val seam by lazy { LightsHouseSeam(house, settings) }

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

    /** Fails if turning off is skipped because the record says off: it always runs. */
    @Test
    fun offAlwaysRuns() = runTest {
        soon { settings.setHolidayOn(true) }
        soon { settings.setHolidayStarted(setOf("mimic-id")) }
        system.automationList = listOf(MIMIC.copy(on = true))
        assertThat(seam.setHoliday(false, houseAdult())).isEqualTo(HouseStep.Done)
        assertThat(system.calls).contains("setAutomation mimic-id false")
        assertThat(soon { settings.prefs.first() }.holidayOn).isFalse()
    }

    /** Fails if ready is true before a bridge is paired, or doesn't follow pairing. */
    @Test
    fun readyFollowsTheBridge() = runTest {
        assertThat(seam.ready.first()).isFalse()
        soon { settings.connect("lights.hue", link()) }
        assertThat(seam.ready.first()).isTrue()
    }
}
