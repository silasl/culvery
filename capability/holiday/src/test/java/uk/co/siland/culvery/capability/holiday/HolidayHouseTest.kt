package uk.co.siland.culvery.capability.holiday

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.holiday.HolidayStep.ECO_OFF
import uk.co.siland.culvery.capability.holiday.HolidayStep.ECO_ON
import uk.co.siland.culvery.capability.holiday.HolidayStep.HOOVER
import uk.co.siland.culvery.capability.holiday.HolidayStep.LIGHTS_ON
import uk.co.siland.culvery.capability.holiday.HolidayStep.PLUGS_ON
import uk.co.siland.culvery.capability.holiday.StepState.DONE
import uk.co.siland.culvery.capability.holiday.StepState.MISSED
import uk.co.siland.culvery.capability.holiday.StepState.PENDING
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.WallClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HolidayHouseTest {
    /** The settings over one store, the three seams, and the local time and wall millis the test sets. */
    private class Rig(private val scope: CoroutineScope, val store: MemoryStore = MemoryStore()) {
        val settings = HolidaySettings(store, scope)
        val lights = FakeLights()
        val heating = FakeHeating()
        val hoover = FakeHoover()
        val plugs = FakePlugs()
        var now: LocalDateTime? = THU_0900
        var wall = millis(THU_0900)

        fun at(time: LocalDateTime) {
            now = time
            wall = millis(time)
        }

        fun house(
            lights: HouseLights? = this.lights,
            heating: HouseHeating? = this.heating,
            hoover: HouseHoover? = this.hoover,
            settings: HolidaySettings = this.settings,
            plugs: HousePlugs? = this.plugs,
        ) = HolidayHouse(settings, lights, heating, hoover, plugs, WallClock { wall }, { now })

        /** The process starting again: a new store over the same file, seams with nothing done, and a new house. */
        fun restarted(lights: HouseLights? = this.lights, hoover: HouseHoover? = this.hoover) =
            house(lights = lights, hoover = hoover, settings = HolidaySettings(store, scope))
    }

    private suspend fun adult(): Authorised = TestAccess(Role.ADULT).authorise(HousePermissions.HOLIDAY_MODE)!!

    private fun TestScope.rig() = Rig(backgroundScope)

    /** Fails if the record isn't saved before the lights, or the lights don't go before Eco, or Holiday mode isn't on after. */
    @Test
    fun turningOnRunsTheStepsInOrder() = runTest {
        val rig = rig()
        val house = rig.house()
        val order = mutableListOf<String>()
        rig.lights.onCall = { order += "lights, saved ${rig.settings.prefs.first().on}" }
        rig.heating.onCall = { order += "eco" }

        house.turnOn(SAT_31_OCT, adult())

        assertThat(order).containsExactly("lights, saved true", "eco").inOrder()
        assertThat(rig.lights.calls).containsExactly(true)
        assertThat(rig.heating.calls).containsExactly(true)
        assertThat(house.holiday.value).isTrue()
        assertThat(house.record.value!!.steps).isEqualTo(mapOf(LIGHTS_ON to DONE, ECO_ON to DONE, HOOVER to PENDING, ECO_OFF to PENDING))
        assertThat(rig.settings.prefs.first()).isEqualTo(house.record.value)
    }

    /** Fails if a second tap while the first is running turns on twice. */
    @Test
    fun turningOnTwiceRunsOnce() = runTest {
        val rig = rig()
        val house = rig.house()
        val gate = CompletableDeferred<Unit>()
        rig.lights.gate = gate
        val by = adult()

        val taps = listOf(launch { house.turnOn(SAT_31_OCT, by) }, launch { house.turnOn(SAT_31_OCT, by) })
        runCurrent()
        assertThat(house.running.value).isEqualTo(HolidayRun.TURNING_ON)
        gate.complete(Unit)
        taps.joinAll()
        house.turnOn(SAT_31_OCT, by)

        assertThat(rig.lights.calls).hasSize(1)
        assertThat(rig.heating.calls).hasSize(1)
        assertThat(house.running.value).isNull()
    }

    /** Fails if a part with no seam, or one not ready, is planned, recorded or called. */
    @Test
    fun eachMissingPartIsLeftOut() = runTest {
        val rig = rig()
        assertThat(rig.house().plan(SAT_31_OCT)).containsExactly(LIGHTS_ON, ECO_ON, HOOVER, ECO_OFF).inOrder()
        rig.heating.readyNow.value = false
        val house = rig.house(lights = null, hoover = null)

        assertThat(house.plan(SAT_31_OCT)).isEmpty()
        house.turnOn(SAT_31_OCT, adult())
        assertThat(house.record.value!!.steps).isEmpty()
        assertThat(house.holiday.value).isTrue()
        rig.at(SAT_0001)
        house.tick()
        house.turnOff(adult())

        assertThat(rig.heating.calls).isEmpty()
        assertThat(rig.hoover.calls).isEmpty()
        assertThat(house.holiday.value).isFalse()
    }

    /** Fails if a failed step is tried again before five minutes on the wall, or not at five, or its line stays after it works. */
    @Test
    fun aFailedStepIsRetriedEveryFiveMinutes() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        rig.heating.results += HouseStep.Failed(ECO_ON_LINE)

        house.line.test {
            assertThat(awaitItem()).isNull()
            house.turnOn(SAT_31_OCT, adult())
            assertThat(awaitItem()).isEqualTo(ECO_ON_LINE)
            assertThat(house.record.value!!.steps[ECO_ON]).isEqualTo(PENDING)

            val turnedOn = rig.wall
            rig.now = THU_0900.plusMinutes(4)
            rig.wall = turnedOn + HolidayHouse.RETRY_MS - 60_000
            house.tick()
            assertThat(rig.heating.calls).hasSize(1)

            rig.now = THU_0900.plusMinutes(5)
            rig.wall = turnedOn + HolidayHouse.RETRY_MS
            house.tick()
            assertThat(rig.heating.calls).containsExactly(true, true)
            assertThat(house.record.value!!.steps[ECO_ON]).isEqualTo(DONE)
            assertThat(awaitItem()).isNull()
        }
    }

    /** Fails if a failing part stops Holiday mode ending, or its line doesn't show for ten minutes and then go. */
    @Test
    fun turningOffEndsEvenWhenAPartFails() = runTest {
        val rig = rig()
        val house = rig.house()
        house.turnOn(SAT_31_OCT, adult())
        rig.heating.results += HouseStep.Failed("The heating didn’t come off Eco. Turn it off in the Nest app.")
        rig.at(FRI_30_OCT.atTime(18, 0))
        val off = rig.wall

        house.turnOff(adult())

        assertThat(rig.lights.calls).containsExactly(true, false).inOrder()
        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
        assertThat(house.holiday.value).isFalse()
        assertThat(house.record.value).isEqualTo(HolidayRecord())
        assertThat(rig.settings.prefs.first()).isEqualTo(HolidayRecord())
        assertThat(house.line.value).isEqualTo(STILL_ON_ECO)
        rig.wall = off + HolidayHouse.OFF_LINE_MS - 60_000
        house.tick()
        assertThat(house.line.value).isEqualTo(STILL_ON_ECO)
        rig.wall = off + HolidayHouse.OFF_LINE_MS
        house.tick()
        assertThat(house.line.value).isNull()
    }

    /** Fails if the off line outlives the next turn-on. */
    @Test
    fun theOffLineGoesWhenItTurnsOnAgain() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.lights.results += HouseStep.Failed("The lights are busy. Try again in a minute.")
        house.turnOff(adult())
        assertThat(house.line.value).isEqualTo("Holiday lights are still on. Turn them off in the Lights tab.")

        house.turnOn(SAT_31_OCT, adult())

        assertThat(house.line.value).isNull()
    }

    /** Fails if a clear that failed at turn-off isn't tried again, so a restart would bring Holiday mode back. */
    @Test
    fun aFailedClearAtTurnOffIsTriedAgainAtTheNextTick() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.store.failingWrites = 1
        house.turnOff(adult())
        assertThat(rig.settings.prefs.first().on).isTrue()

        house.tick()

        assertThat(rig.settings.prefs.first()).isEqualTo(HolidayRecord())
        assertThat(rig.restarted(hoover = null).also { it.tick() }.holiday.value).isFalse()
    }

    /** Fails if a save that failed at turn-on isn't tried again, so a restart would lose Holiday mode. */
    @Test
    fun aFailedSaveAtTurnOnIsTriedAgainAtTheNextTick() = runTest {
        val rig = rig()
        val house = rig.house(lights = null, heating = null, hoover = null)
        rig.store.failingWrites = 1
        house.turnOn(SAT_31_OCT, adult())
        assertThat(rig.settings.prefs.first().on).isFalse()

        house.tick()

        assertThat(rig.settings.prefs.first()).isEqualTo(house.record.value)
        assertThat(rig.restarted(lights = null, hoover = null).also { it.tick() }.holiday.value).isTrue()
    }

    /** Fails if coming back early leaves the heating on Eco. */
    @Test
    fun turningOffEarlyTakesEcoOff() = runTest {
        val rig = rig()
        val house = rig.house()
        house.turnOn(SAT_31_OCT, adult())
        rig.at(FRI_30_OCT.atTime(12, 0))

        house.turnOff(adult())

        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
    }

    /** 11 §5.7: fails if turning off after Eco came off at 00:01 touches the heating again. */
    @Test
    fun turningOffAfterEcoOffLeavesTheHeating() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.at(SAT_0001)
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()

        house.turnOff(adult())

        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
        assertThat(rig.lights.calls).containsExactly(true, false).inOrder()
    }

    /** Fails if a grant for something else (Holiday lights, say) turns the house on or off. */
    @Test
    fun aGrantWithoutHolidayModeIsRefused() = runTest {
        val rig = rig()
        val house = rig.house()
        val lightsOnly = grant("lights.holiday")

        assertThat(runCatching { house.turnOn(SAT_31_OCT, lightsOnly) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        house.turnOn(SAT_31_OCT, adult())
        assertThat(runCatching { house.turnOff(lightsOnly) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)

        assertThat(rig.lights.calls).containsExactly(true)
        assertThat(house.holiday.value).isTrue()
    }

    /** Fails if the hoover starts before 10:00 the day before, not at 10:00, or twice. */
    @Test
    fun theHooverRunsAt1000TheDayBefore() = runTest {
        val rig = rig()
        val house = rig.house()
        house.turnOn(SAT_31_OCT, adult())

        rig.at(FRI_30_OCT.atTime(9, 59))
        house.tick()
        assertThat(rig.hoover.calls).isEmpty()
        rig.at(FRI_30_OCT.atTime(10, 0))
        house.tick()
        assertThat(rig.hoover.calls).hasSize(1)
        rig.at(FRI_30_OCT.atTime(10, 1))
        house.tick()
        rig.at(FRI_30_OCT.atTime(10, 6))
        house.tick()

        assertThat(rig.hoover.calls).hasSize(1)
        assertThat(house.record.value!!.steps[HOOVER]).isEqualTo(DONE)
    }

    /** Fails if a hoover already cleaning (the seam's Done) is started again or shows a line. */
    @Test
    fun aHooverAlreadyCleaningCountsAsDone() = runTest {
        val rig = rig()
        val house = rig.house()
        house.turnOn(SAT_31_OCT, adult())
        rig.hoover.results += HouseStep.Done

        rig.at(FRI_30_OCT.atTime(10, 0))
        house.tick()
        rig.at(FRI_30_OCT.atTime(10, 5))
        house.tick()

        assertThat(rig.hoover.calls).hasSize(1)
        assertThat(house.record.value!!.steps[HOOVER]).isEqualTo(DONE)
        assertThat(house.line.value).isNull()
    }

    /** Fails if a mode turned on at or after 10:00 the day before still plans or runs the hoover. */
    @Test
    fun aHooverTurnedOnAfter1000TheDayBeforeIsNotPlanned() = runTest {
        val rig = rig()
        val house = rig.house()
        rig.at(FRI_30_OCT.atTime(9, 59))
        assertThat(house.plan(SAT_31_OCT)).contains(HOOVER)
        rig.at(FRI_30_OCT.atTime(10, 0))
        assertThat(house.plan(SAT_31_OCT)).doesNotContain(HOOVER)

        rig.at(FRI_30_OCT.atTime(10, 30))
        assertThat(house.plan(SAT_31_OCT)).containsExactly(LIGHTS_ON, ECO_ON, ECO_OFF).inOrder()
        house.turnOn(SAT_31_OCT, adult())
        rig.at(FRI_30_OCT.atTime(10, 31))
        house.tick()

        assertThat(house.record.value!!.steps).doesNotContainKey(HOOVER)
        assertThat(rig.hoover.calls).isEmpty()
    }

    /** Fails if a restart at 22:00 the day before starts the hoover, or one at 21:59 doesn't. */
    @Test
    fun aRestartAfterTheHooverWindowSkipsIt() = runTest {
        val late = rig()
        late.house().turnOn(SAT_31_OCT, adult())
        late.at(FRI_30_OCT.atTime(22, 0))
        val afterWindow = late.restarted()
        afterWindow.tick()
        assertThat(late.hoover.calls).isEmpty()
        assertThat(afterWindow.record.value!!.steps[HOOVER]).isEqualTo(MISSED)
        assertThat(afterWindow.line.value).isEqualTo("The hoover didn’t run.")

        val inTime = rig()
        inTime.house().turnOn(SAT_31_OCT, adult())
        inTime.at(FRI_30_OCT.atTime(21, 59))
        inTime.restarted().tick()
        assertThat(inTime.hoover.calls).hasSize(1)
    }

    /** Fails if Eco comes off before 00:01 on the back day, or not at 00:01. */
    @Test
    fun ecoComesOffAt0001() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())

        rig.at(SAT_31_OCT.atTime(0, 0))
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true)
        rig.at(SAT_0001)
        house.tick()

        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
        assertThat(house.record.value!!.steps[ECO_OFF]).isEqualTo(DONE)
        assertThat(house.holiday.value).isTrue()
    }

    /** Fails if a tablet that was off at 00:01 doesn't take Eco off when it starts. */
    @Test
    fun aRestartAfter0001TurnsEcoOffAtOnce() = runTest {
        val rig = rig()
        rig.house(hoover = null).turnOn(SAT_31_OCT, adult())

        rig.at(LocalDateTime.of(2026, 11, 1, 14, 0))
        rig.restarted(hoover = null).tick()

        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
    }

    /** Fails if Eco on that never worked stops Eco off, or is still tried after 00:01, or leaves a line of its own. */
    @Test
    fun ecoOnThatNeverWorkedIsMissedAt0001() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        rig.heating.otherwise = HouseStep.Failed(ECO_ON_LINE)
        house.turnOn(SAT_31_OCT, adult())
        rig.at(SAT_31_OCT.atTime(0, 0))
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true, true)

        rig.heating.otherwise = HouseStep.Done
        rig.at(SAT_0001)
        house.tick()

        assertThat(rig.heating.calls).containsExactly(true, true, false).inOrder()
        assertThat(house.record.value!!.steps[ECO_ON]).isEqualTo(MISSED)
        assertThat(house.record.value!!.steps[ECO_OFF]).isEqualTo(DONE)
        assertThat(house.line.value).isNull()
    }

    /** Fails if a lights step left pending by a restart is tried without a grant, or not reported. */
    @Test
    fun aPendingLightsStepAfterARestartIsReported() = runTest {
        val store = MemoryStore()
        val rig = Rig(backgroundScope, store)
        rig.settings.save(HolidayRecord(on = true, back = SAT_31_OCT, since = rig.wall, steps = mapOf(LIGHTS_ON to PENDING)))
        val house = rig.house()

        house.tick()

        assertThat(rig.lights.calls).isEmpty()
        assertThat(house.record.value!!.steps[LIGHTS_ON]).isEqualTo(MISSED)
        assertThat(house.line.value).isEqualTo("Holiday lights didn’t switch on. Turn them on in the Lights tab.")
        assertThat(house.holiday.value).isTrue()
    }

    /** 11 §5.3: fails if the hoover or Eco off follow UTC across the spring change rather than the wall's local time. */
    @Test
    fun springForwardKeepsLocalTimes() = runTest {
        val rig = rig()
        val house = rig.house()
        rig.at(LocalDateTime.of(2027, 3, 26, 9, 0))
        val back = LocalDate.of(2027, 3, 29)
        house.turnOn(back, adult())

        rig.at(LocalDateTime.of(2027, 3, 28, 9, 59))
        house.tick()
        assertThat(rig.hoover.calls).isEmpty()
        rig.at(LocalDateTime.of(2027, 3, 28, 10, 0))
        house.tick()
        assertThat(rig.hoover.calls).hasSize(1)
        rig.at(LocalDateTime.of(2027, 3, 29, 0, 0))
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true)
        rig.at(LocalDateTime.of(2027, 3, 29, 0, 1))
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
    }

    /** 11 §5.3: as above, across the autumn change. */
    @Test
    fun autumnBackKeepsLocalTimes() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        rig.at(LocalDateTime.of(2027, 10, 28, 9, 0))
        house.turnOn(LocalDate.of(2027, 10, 31), adult())

        rig.at(LocalDateTime.of(2027, 10, 31, 0, 0))
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true)
        rig.at(LocalDateTime.of(2027, 10, 31, 0, 1))
        house.tick()
        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
    }

    /** Fails if retries are spaced on local time, which repeats an hour when the clocks go back. */
    @Test
    fun retriesAreSpacedOnTheWallClock() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        rig.heating.otherwise = HouseStep.Failed(ECO_ON_LINE)
        rig.at(LocalDateTime.of(2026, 10, 25, 1, 58))
        house.turnOn(SAT_31_OCT, adult())
        val turnedOn = rig.wall

        // The second 01:02 and 01:03, after the clocks went back at 02:00.
        rig.now = LocalDateTime.of(2026, 10, 25, 1, 2)
        rig.wall = turnedOn + HolidayHouse.RETRY_MS - 60_000
        house.tick()
        assertThat(rig.heating.calls).hasSize(1)
        rig.now = LocalDateTime.of(2026, 10, 25, 1, 3)
        rig.wall = turnedOn + HolidayHouse.RETRY_MS
        house.tick()

        assertThat(rig.heating.calls).hasSize(2)
    }

    /** Fails if the plugs' run isn't begun with the turn-on's own grant before the record is saved, or enter doesn't follow the heating. */
    @Test
    fun turnOnBeginsThePlugsWithItsGrantBeforeTheRecord() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house()
        val order = mutableListOf<String>()
        rig.plugs.onCall = { order += "$it, saved ${rig.settings.prefs.first().on}" }
        rig.lights.onCall = { order += "setHoliday(true)" }
        rig.heating.onCall = { order += "setEco(true)" }
        val by = adult()

        house.turnOn(SAT_31_OCT, by)

        assertThat(rig.plugs.begins).containsExactly(by)
        assertThat(order).containsExactly(
            "leave:holiday, saved false", "begin:holiday, saved false", "setHoliday(true)", "setEco(true)", "enter:holiday, saved true",
        ).inOrder()
        assertThat(house.record.value!!.steps)
            .isEqualTo(mapOf(LIGHTS_ON to DONE, ECO_ON to DONE, PLUGS_ON to DONE, HOOVER to PENDING, ECO_OFF to PENDING))
    }

    /** Fails if plugs that aren't ready get a step, a begin or an enter, or turning off skips putting them back. */
    @Test
    fun noPlugsReadyNoStep() = runTest {
        val rig = rig()
        val house = rig.house()

        assertThat(house.plan(SAT_31_OCT)).doesNotContain(PLUGS_ON)
        house.turnOn(SAT_31_OCT, adult())
        assertThat(house.record.value!!.steps).doesNotContainKey(PLUGS_ON)
        assertThat(rig.plugs.calls).containsExactly("leave:holiday")
        house.turnOff(adult())

        assertThat(rig.plugs.calls).containsExactly("leave:holiday", "leave:holiday")
        assertThat(rig.plugs.begins).isEmpty()
    }

    /** Fails if a begin that threw stops the record being saved, or the missing run isn't missed with its own line. */
    @Test
    fun aPlugsStepWithNoRunIsMissed() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        rig.plugs.throwOnBegin = true
        val house = rig.house(hoover = null)

        house.turnOn(SAT_31_OCT, adult())

        assertThat(rig.settings.prefs.first().on).isTrue()
        assertThat(rig.plugs.calls).containsExactly("leave:holiday", "begin:holiday", "enter:holiday").inOrder()
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(MISSED)
        assertThat(house.line.value).isEqualTo("The plugs didn’t switch. Switch them on the Plugs tab.")
    }

    /** Fails if a failed plugs step shows no line, is tried before five minutes or not at five, or keeps its line once it works. */
    @Test
    fun aFailedPlugsStepIsRetriedEveryFiveMinutes() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house(hoover = null)
        rig.plugs.enters += HouseStep.Failed(LAMP_LINE)

        house.line.test {
            assertThat(awaitItem()).isNull()
            house.turnOn(SAT_31_OCT, adult())
            assertThat(awaitItem()).isEqualTo(LAMP_LINE)
            assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(PENDING)

            val turnedOn = rig.wall
            rig.now = THU_0900.plusMinutes(4)
            rig.wall = turnedOn + HolidayHouse.RETRY_MS - 60_000
            house.tick()
            assertThat(rig.plugs.enterCount()).isEqualTo(1)

            rig.now = THU_0900.plusMinutes(5)
            rig.wall = turnedOn + HolidayHouse.RETRY_MS
            house.tick()
            assertThat(rig.plugs.enterCount()).isEqualTo(2)
            assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(DONE)
            assertThat(awaitItem()).isNull()
        }
    }

    /** Fails if an enter that threw shows no line or isn't tried again. */
    @Test
    fun aPlugsStepThatThrowsShowsItsLine() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        rig.plugs.throwOnEnter = true
        val house = rig.house(hoover = null)

        house.turnOn(SAT_31_OCT, adult())
        assertThat(house.line.value).isEqualTo("The plugs didn’t switch. They’ll keep trying.")
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(PENDING)

        rig.plugs.throwOnEnter = false
        val turnedOn = rig.wall
        rig.wall = turnedOn + HolidayHouse.RETRY_MS - 60_000
        house.tick()
        assertThat(rig.plugs.enterCount()).isEqualTo(1)
        rig.wall = turnedOn + HolidayHouse.RETRY_MS
        house.tick()

        assertThat(rig.plugs.enterCount()).isEqualTo(2)
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(DONE)
        assertThat(house.line.value).isNull()
    }

    /** Pin: fails if the plugs step ever has a window that closes, through 00:01 on the back day and the day after. */
    @Test
    fun thePlugsStepIsNeverMissedByTime() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        rig.plugs.enterOtherwise = HouseStep.Failed(LAMP_LINE)
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())

        rig.at(SAT_31_OCT.atTime(0, 0))
        house.tick()
        assertThat(rig.plugs.enterCount()).isEqualTo(2)
        rig.at(SAT_0001)
        house.tick()
        assertThat(rig.plugs.enterCount()).isEqualTo(2)
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(PENDING)
        rig.at(SAT_31_OCT.atTime(0, 6))
        house.tick()
        assertThat(rig.plugs.enterCount()).isEqualTo(3)
        rig.at(SAT_31_OCT.plusDays(1).atTime(12, 0))
        house.tick()

        assertThat(rig.plugs.enterCount()).isEqualTo(4)
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(PENDING)
        assertThat(house.line.value).isEqualTo(LAMP_LINE)
    }

    /** Fails if a plug that won't switch stops the hoover at 10:00 or Eco off at 00:01. */
    @Test
    fun aFailedPlugDoesntStopHolidayMode() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        rig.plugs.enterOtherwise = HouseStep.Failed(LAMP_LINE)
        val house = rig.house()
        house.turnOn(SAT_31_OCT, adult())

        rig.at(FRI_30_OCT.atTime(10, 0))
        house.tick()
        assertThat(rig.hoover.calls).hasSize(1)
        rig.at(SAT_0001)
        house.tick()

        assertThat(rig.heating.calls).containsExactly(true, false).inOrder()
        assertThat(house.record.value!!.steps[HOOVER]).isEqualTo(DONE)
        assertThat(house.record.value!!.steps[ECO_OFF]).isEqualTo(DONE)
    }

    /** Fails if a plugs step left pending by a restart is skipped, or asks for a grant the restart forgot. */
    @Test
    fun aPendingPlugsStepIsRetriedAfterARestart() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        rig.settings.save(HolidayRecord(on = true, back = SAT_31_OCT, since = rig.wall, steps = mapOf(PLUGS_ON to PENDING)))
        rig.plugs.begun += HouseModes.HOLIDAY
        val house = rig.house()

        house.tick()

        assertThat(rig.plugs.calls).containsExactly("enter:holiday")
        assertThat(rig.plugs.begins).isEmpty()
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(DONE)
    }

    /** Fails if the plugs aren't put back after the heating and before the record clears, or are touched at 00:01. */
    @Test
    fun plugsGoBackAtTapOff() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.at(SAT_0001)
        house.tick()
        assertThat(rig.plugs.calls).containsExactly("leave:holiday", "begin:holiday", "enter:holiday").inOrder()

        val early = rig()
        early.plugs.readyNow.value = true
        val tapped = early.house(hoover = null)
        tapped.turnOn(SAT_31_OCT, adult())
        early.at(FRI_30_OCT.atTime(18, 0))
        val order = mutableListOf<String>()
        early.lights.onCall = { order += "setHoliday(false)" }
        early.heating.onCall = { order += "setEco(false)" }
        early.plugs.onCall = { order += "$it, record on ${early.settings.prefs.first().on}" }

        tapped.turnOff(adult())

        assertThat(order).containsExactly("setHoliday(false)", "setEco(false)", "leave:holiday, record on true").inOrder()
        assertThat(early.settings.prefs.first()).isEqualTo(HolidayRecord())
        assertThat(tapped.line.value).isNull()
    }

    /** Review Focus 5: fails if plugs that didn't go back stop Holiday mode ending, or their line doesn't show for ten minutes. */
    @Test
    fun turningOffWithPlugsFailingStillEnds() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        rig.at(FRI_30_OCT.atTime(18, 0))
        val off = rig.wall

        house.turnOff(adult())

        assertThat(house.holiday.value).isFalse()
        assertThat(rig.settings.prefs.first()).isEqualTo(HolidayRecord())
        assertThat(house.line.value).isEqualTo(LAMP_BACK_LINE)
        rig.wall = off + HolidayHouse.OFF_LINE_MS - 60_000
        house.tick()
        assertThat(house.line.value).isEqualTo(LAMP_BACK_LINE)
        rig.wall = off + HolidayHouse.OFF_LINE_MS
        house.tick()
        assertThat(house.line.value).isNull()

        house.turnOn(SAT_31_OCT, adult())
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        rig.heating.results += HouseStep.Failed("The heating didn’t come off Eco. Turn it off in the Nest app.")
        house.turnOff(adult())

        assertThat(house.line.value).isEqualTo("$STILL_ON_ECO\n$LAMP_BACK_LINE")
    }

    /** Fails if tap-off shows only one failure, or shows them in another order than heating, plugs, lights (12 §5.5). */
    @Test
    fun tapOffShowsEveryFailureInOrder() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.lights.results += HouseStep.Failed("The lights are busy. Try again in a minute.")
        rig.heating.results += HouseStep.Failed("The heating didn’t come off Eco. Turn it off in the Nest app.")
        rig.plugs.leaves += HouseStep.Failed(LAMP_BACK_LINE)
        rig.at(FRI_30_OCT.atTime(18, 0))
        val off = rig.wall

        house.turnOff(adult())

        assertThat(house.line.value).isEqualTo("$STILL_ON_ECO\n$LAMP_BACK_LINE\n${HolidayWords.LIGHTS_STILL_ON}")
        rig.wall = off + HolidayHouse.OFF_LINE_MS
        house.tick()
        assertThat(house.line.value).isNull()
    }

    /** Pin: while Holiday mode is on the tile keeps its one latest line, even with two steps failing (12 §5.5). */
    @Test
    fun whileOnTheTileKeepsOneLine() = runTest {
        val rig = rig()
        val house = rig.house(hoover = null)
        rig.lights.results += HouseStep.Failed("The lights are busy. Try again in a minute.")
        rig.heating.results += HouseStep.Failed(ECO_ON_LINE)

        house.turnOn(SAT_31_OCT, adult())

        assertThat(house.line.value).isEqualTo(ECO_ON_LINE)
    }

    /** Review Focus 5: fails if a leave that threw stops Holiday mode ending, or shows no line. */
    @Test
    fun turningOffWithPlugsThrowingStillEnds() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house(hoover = null)
        house.turnOn(SAT_31_OCT, adult())
        rig.plugs.throwOnLeave = true

        house.turnOff(adult())

        assertThat(house.holiday.value).isFalse()
        assertThat(rig.settings.prefs.first()).isEqualTo(HolidayRecord())
        assertThat(house.line.value).isEqualTo("Some plugs may not have gone back. Check them on the Plugs tab.")
    }

    /** Review Focus 4: fails if a run left by a turn-on that never saved isn't put back once, with no line, when the record loads as off. */
    @Test
    fun anOrphanRunIsPutBackWhenHolidayLoadsAsOff() = runTest {
        val rig = rig()
        rig.plugs.begun += HouseModes.HOLIDAY
        val house = rig.house()

        house.tick()
        assertThat(rig.plugs.calls).containsExactly("leave:holiday")
        assertThat(house.line.value).isNull()
        assertThat(rig.plugs.begun).isEmpty()
        house.tick()

        assertThat(rig.plugs.calls).containsExactly("leave:holiday")
    }

    /** Pin: fails if a record saved before the plugs step existed no longer reads as saved. */
    @Test
    fun oldRecordsStillRead() = runTest {
        val rig = rig()
        val old = HolidayRecord(
            on = true, back = SAT_31_OCT, since = rig.wall,
            steps = mapOf(LIGHTS_ON to DONE, ECO_ON to DONE, HOOVER to PENDING, ECO_OFF to PENDING),
        )
        rig.settings.save(old)
        val house = rig.house()

        house.tick()

        assertThat(house.record.value).isEqualTo(old)
        assertThat(rig.plugs.calls).isEmpty()
    }

    /** Fails if a run left begun by a leave whose last write failed stops the next turn-on beginning and entering, or turning on leaves first. */
    @Test
    fun aStuckRunIsClearedBeforeTheNextTurnOn() = runTest {
        val rig = rig()
        rig.plugs.readyNow.value = true
        val house = rig.house(hoover = null)
        val by = adult()
        house.tick()
        rig.plugs.begun += HouseModes.HOLIDAY

        house.turnOn(SAT_31_OCT, by)

        assertThat(rig.plugs.calls).containsExactly("leave:holiday", "begin:holiday", "enter:holiday").inOrder()
        assertThat(rig.plugs.begins).containsExactly(by)
        assertThat(house.record.value!!.steps[PLUGS_ON]).isEqualTo(DONE)
    }

    private companion object {
        val LONDON: ZoneId = ZoneId.of("Europe/London")
        val THU_0900: LocalDateTime = LocalDateTime.of(2026, 10, 29, 9, 0)
        val FRI_30_OCT: LocalDate = LocalDate.of(2026, 10, 30)
        val SAT_31_OCT: LocalDate = LocalDate.of(2026, 10, 31)
        val SAT_0001: LocalDateTime = SAT_31_OCT.atTime(0, 1)
        const val ECO_ON_LINE = "The heating didn’t go to Eco. Check the Nest app."
        const val STILL_ON_ECO = "The heating is still on Eco. Turn it off in the Nest app."
        const val LAMP_LINE = "Lamp didn’t switch off. It’ll keep trying."
        const val LAMP_BACK_LINE = "Lamp didn’t switch back on. Switch it on the Plugs tab."

        fun FakePlugs.enterCount() = calls.count { it == "enter:holiday" }

        fun millis(time: LocalDateTime) = time.atZone(LONDON).toInstant().toEpochMilli()
    }
}
