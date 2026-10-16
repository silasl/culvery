package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role

/** 7 §2.8, §4.3, §6: commands to the system, the paced slider, the row's line when Hue refuses, the PIN for Holiday mode. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LightsActionsTest {
    @get:Rule val fixture = LightsFixture()
    private val lounge = room("Lounge", on = true)
    private val kitchen = room("Kitchen", on = true)
    private val system = TestLightsSystem(listOf(lounge, kitchen))

    private fun TestScope.actions(s: LightsSystem? = system) = LightsActions({ s }, backgroundScope, { testScheduler.currentTime })

    private val house by lazy { LightsHouse({ system }, fixture.settings) }

    private fun TestScope.actions(access: TestAccess) =
        LightsActions({ system }, backgroundScope, { testScheduler.currentTime }, house, access)

    private suspend fun waitFor(call: String) = soon { while (call !in system.calls) delay(5) }

    private suspend fun waitForHoliday(on: Boolean) = soon {
        fixture.settings.prefs.first { it.holidayOn == on }
        house.running.first { it == null }
    }

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

    /** Fails if a slider moved on a room that is off sends a separate on, which would flash its last brightness first. */
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
        assertThat(system.calls).containsExactly("setBrightness Hall-id 10", "setBrightness Hall-id 20", "setBrightness Hall-id 25").inOrder()
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

    /** Fails if Holiday mode asks for no PIN, asks only one way, or asks for a permission other than Holiday mode's. */
    @Test
    fun holidayModeAsksForTheFreshPinBothWays() = runTest {
        val access = TestAccess(Role.ADULT)
        val actions = actions(access)
        actions.toggleHoliday()
        waitForHoliday(on = true)
        actions.toggleHoliday()
        waitForHoliday(on = false)
        assertThat(access.asked).containsExactly(listOf(LightsPermissions.HOLIDAY), listOf(LightsPermissions.HOLIDAY)).inOrder()
    }

    /** Fails if a Child's PIN, which doesn't hold Holiday mode, still reaches the bridge. */
    @Test
    fun aChildsPinIsRefused() = runTest {
        val access = TestAccess(Role.CHILD)
        val work = Job()
        val actions = LightsActions({ system }, CoroutineScope(backgroundScope.coroutineContext + work), { 0L }, house, access)
        actions.toggleHoliday()
        work.children.forEach { it.join() }
        assertThat(access.asked).containsExactly(listOf(LightsPermissions.HOLIDAY))
        assertThat(system.calls).isEmpty()
        assertThat(soon { fixture.settings.prefs.first() }.holidayOn).isFalse()
    }

    /** Fails if a tap that will be ignored still shows the PIN pad. */
    @Test
    fun aTapWhileRunningAsksForNoPin() = runTest {
        val access = TestAccess(Role.ADULT)
        val actions = actions(access)
        val open = CompletableDeferred<Unit>().also { system.gates["allOff []"] = it }
        val run = async { house.toggleHoliday(adult()) }
        waitFor("allOff []")
        actions.toggleHoliday()
        runCurrent()
        assertThat(access.asked).isEmpty()
        open.complete(Unit)
        assertThat(run.await()).isEqualTo(HolidayResult.Done)
    }

    /** Fails if a room command or All lights off asks for a PIN. */
    @Test
    fun theLightsNeedNoPin() = runTest {
        val access = TestAccess(null)
        val actions = actions(access)
        system.allOffAnswer = AllOffResult.RoomsLeftOn(1)
        actions.setOn(lounge, false)
        actions.allOff()
        waitFor("allOff []")
        assertThat(soon { actions.houseLine.first { it != null } }).isEqualTo("1 room didn’t turn off. Try again.")
        assertThat(system.calls).contains("setOn Lounge-id false")
        assertThat(access.asked).isEmpty()
    }

    /** Fails if the rooms left on or the automation not started is missing from the line, or they run together. */
    @Test
    fun theHouseLineSaysWhatHappened() = runTest {
        soon { fixture.settings.setHolidayAutomations(setOf("mimic-id")) }
        system.allOffAnswer = AllOffResult.RoomsLeftOn(2)
        system.results["mimic-id"] = AutomationResult.NOT_CONFIRMED
        val actions = actions(TestAccess(Role.ADMIN))
        actions.toggleHoliday()
        assertThat(soon { actions.houseLine.first { it != null } }).isEqualTo(
            "2 rooms didn’t turn off. Try again. Lights are off, but Culvery couldn’t start Mimic presence. Start it in the Hue app.",
        )
    }
}
