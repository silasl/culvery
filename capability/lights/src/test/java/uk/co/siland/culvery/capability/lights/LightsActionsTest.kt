package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 7 §4.3, §6: commands to the system, the paced slider, the row's line when Hue refuses. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LightsActionsTest {
    private val lounge = room("Lounge", on = true)
    private val kitchen = room("Kitchen", on = true)
    private val system = TestLightsSystem(listOf(lounge, kitchen))

    private fun TestScope.actions(s: LightsSystem? = system) = LightsActions({ s }, backgroundScope, { testScheduler.currentTime })

    private fun TestScope.at(ms: Long) {
        val wait = ms - testScheduler.currentTime
        if (wait > 0) testScheduler.advanceTimeBy(wait)
        runCurrent()
    }

    /** Fails if on and off are mixed up or not sent. */
    @Test
    fun aSwitchSendsOnOrOff() = runTest {
        val actions = actions()
        actions.setOn(lounge, false)
        actions.setOn(kitchen, true)
        runCurrent()
        assertThat(system.calls).containsExactly("setOn Lounge-id false", "setOn Kitchen-id true").inOrder()
    }

    /** Fails if toggle doesn't send the opposite of the room's state. */
    @Test
    fun toggle() = runTest {
        val actions = actions()
        actions.toggle(lounge)
        actions.toggle(room("Hall", on = false))
        runCurrent()
        assertThat(system.calls).containsExactly("setOn Lounge-id false", "setOn Hall-id true").inOrder()
    }

    /** Fails if a middle value is sent, the gap is shorter or longer than 300 ms, or the newest value isn't the one sent. */
    @Test
    fun aDragSendsAtMostOneEveryThreeHundredMilliseconds() = runTest {
        val actions = actions()
        actions.dragBrightness(lounge, 10)
        at(0)
        assertThat(system.calls).containsExactly("setBrightness Lounge-id 10")
        at(100)
        actions.dragBrightness(lounge, 20)
        at(200)
        actions.dragBrightness(lounge, 30)
        at(299)
        assertThat(system.calls).containsExactly("setBrightness Lounge-id 10")
        at(300)
        assertThat(system.calls).containsExactly("setBrightness Lounge-id 10", "setBrightness Lounge-id 30").inOrder()
        at(1_000)
        assertThat(system.calls).doesNotContain("setBrightness Lounge-id 20")
    }

    /** Fails if letting go sends a waiting value or waits for the gap. */
    @Test
    fun theValueLetGoIsTheLastSent() = runTest {
        val actions = actions()
        actions.dragBrightness(lounge, 10)
        at(100)
        actions.dragBrightness(lounge, 40)
        at(150)
        actions.letGoBrightness(lounge, 45)
        at(150)
        assertThat(system.calls).containsExactly("setBrightness Lounge-id 10", "setBrightness Lounge-id 45").inOrder()
        at(1_000)
        assertThat(system.calls).doesNotContain("setBrightness Lounge-id 40")
    }

    /** Fails if one room's drag holds back another's. */
    @Test
    fun eachRoomIsPacedApart() = runTest {
        val actions = actions()
        actions.dragBrightness(lounge, 10)
        at(100)
        actions.dragBrightness(lounge, 20)
        actions.dragBrightness(kitchen, 50)
        at(101)
        assertThat(system.calls).containsExactly("setBrightness Lounge-id 10", "setBrightness Kitchen-id 50").inOrder()
        at(300)
        assertThat(system.calls.last()).isEqualTo("setBrightness Lounge-id 20")
    }

    /** Fails if a slider moved on a room that is off sends brightness without switching it on, or switches on at every send. */
    @Test
    fun movingTheSliderOfAnOffRoomTurnsItOnAtThatBrightness() = runTest {
        val actions = actions()
        val hall = room("Hall", on = false)
        actions.dragBrightness(hall, 10)
        at(300)
        actions.dragBrightness(hall, 20)
        at(600)
        actions.letGoBrightness(hall, 25)
        at(600)
        assertThat(system.calls).containsExactly(
            "setOn Hall-id true", "setBrightness Hall-id 10", "setBrightness Hall-id 20", "setBrightness Hall-id 25",
        ).inOrder()
    }

    /** Fails if a refusal isn't on that room's row, or a later command doesn't clear it. */
    @Test
    fun aRefusedCommandSaysSoOnItsRow() = runTest {
        val actions = actions()
        system.failures["setOn Lounge-id false"] = LightsRefusedException()
        actions.setOn(lounge, false)
        runCurrent()
        assertThat(actions.lines.value).containsExactly("Lounge-id", "Hue didn’t do that. Try again.")
        system.failures.clear()
        actions.setOn(lounge, false)
        assertThat(actions.lines.value).isEmpty()
        system.failures["setBrightness Kitchen-id 30"] = LightsRefusedException()
        actions.letGoBrightness(kitchen, 30)
        runCurrent()
        assertThat(actions.lines.value).containsExactly("Kitchen-id", "Hue didn’t do that. Try again.")
    }

    /** Fails if a bridge that can't be reached puts a line on every row. */
    @Test
    fun anUnreachableBridgeIsTheStatusLinesToSay() = runTest {
        val actions = actions()
        system.failures["setOn Lounge-id false"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        actions.setOn(lounge, false)
        runCurrent()
        assertThat(actions.lines.value).isEmpty()
    }

    /** Fails if a command with no system open throws or sends. */
    @Test
    fun noSystemSendsNothing() = runTest {
        val actions = actions(null)
        actions.setOn(lounge, false)
        actions.dragBrightness(lounge, 10)
        actions.letGoBrightness(lounge, 10)
        actions.recallScene(lounge, RELAX)
        runCurrent()
        assertThat(system.calls).isEmpty()
        assertThat(actions.lines.value).isEmpty()
    }

    /** Fails if the scene isn't the one named. */
    @Test
    fun aSceneIsRecalled() = runTest {
        val actions = actions()
        actions.recallScene(lounge, RELAX)
        runCurrent()
        assertThat(system.calls).containsExactly("recallScene Lounge-id relax-id")
    }
}
