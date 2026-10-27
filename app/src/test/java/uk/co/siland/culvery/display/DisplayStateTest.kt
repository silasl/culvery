package uk.co.siland.culvery.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.display.Screen.ASLEEP
import uk.co.siland.culvery.display.Screen.AWAKE
import uk.co.siland.culvery.display.Screen.NIGHT_CLOCK

private val DAY: LocalDate = LocalDate.of(2026, 10, 19)
private val NOON: LocalDateTime = DAY.atTime(12, 0)
private const val FIVE_MINUTES = 300_000L
private const val THIRTY_MINUTES = 1_800_000L

/** A display state over inputs the test sets, on the test's scheduler; [display] has run what is due once built. */
private class Wall(
    private val test: TestScope,
    at: LocalDateTime?,
    prefs: DisplayPrefs?,
    inFront: Boolean,
    camera: CameraState,
) {
    val now = MutableStateFlow(at)
    val prefs = MutableStateFlow(prefs)
    val touches = MutableStateFlow(0L)
    val inFront = MutableStateFlow(inFront)
    val camera = MutableStateFlow(camera)
    val holiday = MutableStateFlow<Boolean?>(null)
    val display = DisplayState(this.prefs, now, touches, this.inFront, this.camera, holiday, test.backgroundScope)

    val screen: Screen get() = display.screen.value
    val wanted: Boolean get() = display.cameraWanted.value

    fun after(ms: Long) {
        test.advanceTimeBy(ms)
        test.runCurrent()
    }

    fun at(time: LocalDateTime) {
        now.value = time
        test.runCurrent()
    }

    fun at(hour: Int, minute: Int) = at(DAY.atTime(hour, minute))

    fun touch() {
        touches.value++
        test.runCurrent()
    }

    fun tap() {
        display.tapped()
        test.runCurrent()
    }

    fun move() {
        display.moved()
        test.runCurrent()
    }

    fun hold(on: Boolean) {
        display.held(on)
        test.runCurrent()
    }

    fun front(on: Boolean) {
        inFront.value = on
        test.runCurrent()
    }

    fun set(prefs: DisplayPrefs?) {
        this.prefs.value = prefs
        test.runCurrent()
    }

    fun away(on: Boolean?) {
        holiday.value = on
        test.runCurrent()
    }

    fun asleep() {
        after(FIVE_MINUTES)
        assertThat(screen).isEqualTo(ASLEEP)
    }
}

private fun TestScope.wall(
    at: LocalDateTime? = NOON,
    prefs: DisplayPrefs? = DisplayPrefs(),
    inFront: Boolean = true,
    camera: CameraState = CameraState.ALLOWED,
    settle: Boolean = true,
) = Wall(this, at, prefs, inFront, camera).also { if (settle) runCurrent() }

/** Asserts [screen] [before] ms from now and at [boundary] ms from now, with [later] after. */
private fun Wall.changesAt(boundary: Long, before: Screen, later: Screen) {
    after(boundary - 1)
    assertThat(screen).isEqualTo(before)
    after(1)
    assertThat(screen).isEqualTo(later)
}

@RunWith(AndroidJUnit4::class)
class DisplayStateTest {
    /** Fails if a daytime start is anything but awake, or the night isn't known to be over. */
    @Test
    fun startsAwake() = runTest {
        val wall = wall(settle = false)
        assertThat(wall.screen).isEqualTo(AWAKE)
        assertThat(wall.display.isNight()).isFalse()
    }

    /** Fails if a restart at night, built before the screen resumes, shows the day screen for a minute on resuming. */
    @Test
    fun startsAsTheNightClockAtNight() = runTest {
        val wall = wall(at = DAY.atTime(23, 0), inFront = false, settle = false)
        assertThat(wall.display.isNight()).isTrue()
        wall.front(true)
        assertThat(currentTime).isEqualTo(0L)
        assertThat(wall.screen).isEqualTo(NIGHT_CLOCK)

        val builtInFront = wall(at = DAY.atTime(23, 0), settle = false)
        assertThat(builtInFront.screen).isEqualTo(NIGHT_CLOCK)
    }

    /** Fails if the first arrival in front at night is awake first, covers a hold, or a later return skips the minute awake. */
    @Test
    fun theFirstArrivalInFrontAtNightShowsTheClockAtOnce() = runTest {
        val wall = wall(at = DAY.atTime(23, 0), inFront = false)
        wall.after(10_000)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.front(true)
        assertThat(wall.screen).isEqualTo(NIGHT_CLOCK)
        wall.front(false)
        wall.front(true)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)

        val held = wall(at = DAY.atTime(23, 0), inFront = false)
        held.hold(true)
        held.front(true)
        assertThat(held.screen).isEqualTo(AWAKE)
        held.hold(false)
        held.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** Fails if unread settings or an unknown clock count as day or night, or start anything but awake. */
    @Test
    fun nothingKnownYetIsAwakeAndNotKnown() = runTest {
        val unread = wall(at = DAY.atTime(23, 0), prefs = null)
        assertThat(unread.screen).isEqualTo(AWAKE)
        assertThat(unread.display.isNight()).isNull()
        unread.after(THIRTY_MINUTES)
        assertThat(unread.screen).isEqualTo(AWAKE)
        unread.set(DisplayPrefs())
        assertThat(unread.screen).isEqualTo(NIGHT_CLOCK)

        val noClock = wall(at = null)
        assertThat(noClock.screen).isEqualTo(AWAKE)
        assertThat(noClock.display.isNight()).isNull()
    }

    /** Fails if the day screen sleeps a millisecond early or late. */
    @Test
    fun fiveMinutesWithNobodyAroundSleeps() = runTest {
        wall().changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Fails if a touch or a movement leaves the first wait running. */
    @Test
    fun aTouchOrMovementRestartsTheWait() = runTest {
        val touched = wall()
        touched.after(200_000)
        touched.touch()
        touched.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)

        val moved = wall()
        moved.after(200_000)
        moved.move()
        moved.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Fails if the delay ignores Settings. */
    @Test
    fun theDelayFollowsSettings() = runTest {
        wall(prefs = DisplayPrefs(sleepAfterMinutes = 2)).changesAt(120_000, AWAKE, ASLEEP)
    }

    /** Fails if a changed delay counts from the last touch rather than from the change. */
    @Test
    fun aChangedDelayStartsTheWaitAgain() = runTest {
        val wall = wall()
        wall.after(100_000)
        wall.set(DisplayPrefs(sleepAfterMinutes = 2))
        wall.changesAt(120_000, AWAKE, ASLEEP)
    }

    /** Fails if movement or a tap leaves the dark screen dark. */
    @Test
    fun movementOrATapWakesTheDarkScreen() = runTest {
        val moved = wall()
        moved.asleep()
        moved.move()
        assertThat(moved.screen).isEqualTo(AWAKE)

        val tapped = wall()
        tapped.asleep()
        tapped.tap()
        assertThat(tapped.screen).isEqualTo(AWAKE)
        tapped.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Fails if a counted touch (a TalkBack focus move, say) wakes the dark screen or the night clock. */
    @Test
    fun aTouchWhileAsleepDoesNotWake() = runTest {
        val day = wall()
        day.asleep()
        day.touch()
        assertThat(day.screen).isEqualTo(ASLEEP)

        val night = wall(at = DAY.atTime(23, 0))
        night.touch()
        assertThat(night.screen).isEqualTo(NIGHT_CLOCK)
    }

    /** Fails if a hold starting (the door card, say) leaves the screen dark, or the screen sleeps under it. */
    @Test
    fun theHoldStartingWakesTheDarkScreen() = runTest {
        val wall = wall()
        wall.asleep()
        wall.hold(true)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(THIRTY_MINUTES)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.hold(false)
        wall.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Fails if a hold that starts and ends before the wait's collector runs leaves the first wait running. */
    @Test
    fun aHoldTooShortToSeeStillRestartsTheWait() = runTest {
        val wall = wall()
        wall.after(200_000)
        wall.display.held(true)
        wall.display.held(false)
        wall.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Fails if night doesn't start at 22:00, from the awake screen or the dark one. */
    @Test
    fun nightStartsAtTwentyTwo() = runTest {
        val awake = wall()
        awake.at(21, 59)
        assertThat(awake.screen).isEqualTo(AWAKE)
        awake.at(22, 0)
        assertThat(awake.screen).isEqualTo(NIGHT_CLOCK)

        val asleep = wall(at = DAY.atTime(21, 50))
        asleep.asleep()
        asleep.at(21, 59)
        assertThat(asleep.screen).isEqualTo(ASLEEP)
        asleep.at(22, 0)
        assertThat(asleep.screen).isEqualTo(NIGHT_CLOCK)
    }

    /** Fails if a tap doesn't wake the night clock, touches don't restart its minute, or the minute is off by one. */
    @Test
    fun aTapAtNightWakesForOneMinute() = runTest {
        val wall = wall(at = DAY.atTime(23, 0))
        wall.tap()
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(30_000)
        wall.touch()
        wall.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** Fails if movement (the camera, were it running) wakes the night clock. */
    @Test
    fun movementNeverWakesTheNightClock() = runTest {
        val wall = wall(at = DAY.atTime(23, 0))
        wall.move()
        assertThat(wall.screen).isEqualTo(NIGHT_CLOCK)
    }

    /** Fails if the clock returns under a hold at night, or doesn't follow a minute after it ends. */
    @Test
    fun aHoldAtNightKeepsItAwakeThenTheClockFollowsAMinuteLater() = runTest {
        val wall = wall(at = DAY.atTime(23, 0))
        wall.hold(true)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(THIRTY_MINUTES)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.hold(false)
        wall.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** Fails if night starting covers a door card still showing, or the clock doesn't follow once it closes. */
    @Test
    fun aHoldAsNightStartsStaysThenTheClockFollows() = runTest {
        val wall = wall()
        wall.at(21, 59)
        wall.hold(true)
        wall.at(22, 0)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(THIRTY_MINUTES)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.hold(false)
        wall.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** Fails if night ending leaves the clock showing, or the day's wait doesn't start from then. */
    @Test
    fun nightEndingWakesTheWall() = runTest {
        val wall = wall(at = DAY.atTime(23, 0))
        wall.after(THIRTY_MINUTES)
        wall.at(DAY.plusDays(1).atTime(7, 0))
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Pin: the night spans midnight and an hour lived twice as the clocks go back (25 October 2026, 02:00 to 01:00). */
    @Test
    fun nightAcrossMidnightAndTheClocksGoingBack() = runTest {
        val saturday = LocalDate.of(2026, 10, 24)
        val sunday = saturday.plusDays(1)
        val wall = wall(at = saturday.atTime(12, 0))
        listOf(
            saturday.atTime(23, 59),
            sunday.atTime(0, 0),
            sunday.atTime(1, 30),
            sunday.atTime(1, 59),
            sunday.atTime(1, 0),
            sunday.atTime(1, 30),
            sunday.atTime(6, 59),
        ).forEach {
            wall.at(it)
            assertThat(wall.screen).isEqualTo(NIGHT_CLOCK)
            assertThat(wall.display.isNight()).isTrue()
        }
        wall.at(sunday.atTime(7, 0))
        assertThat(wall.screen).isEqualTo(AWAKE)
    }

    /** Fails if night starts only on a tick equal to its start time, missing a start skipped as the clocks go forward. */
    @Test
    fun nightStartingInTheSkippedHourStillStarts() = runTest {
        val sunday = LocalDate.of(2026, 3, 29)
        val wall = wall(at = sunday.atTime(0, 30), prefs = DisplayPrefs(nightFrom = LocalTime.of(1, 30)))
        wall.at(sunday.atTime(0, 59))
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.at(sunday.atTime(2, 0))
        assertThat(wall.screen).isEqualTo(NIGHT_CLOCK)
    }

    /** Fails if anything sleeps, shows the clock or reacts while Culvery isn't in front. */
    @Test
    fun notInFrontNothingRuns() = runTest {
        val wall = wall(inFront = false)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(THIRTY_MINUTES)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.at(22, 0)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.tap()
        wall.move()
        assertThat(wall.screen).isEqualTo(AWAKE)
    }

    /** Fails if leaving the front keeps the screen dark or a wait running, or a return doesn't start the waits afresh. */
    @Test
    fun leavingTheFrontWakesAndStopsTheWaits() = runTest {
        val day = wall()
        day.asleep()
        day.front(false)
        assertThat(day.screen).isEqualTo(AWAKE)
        day.after(THIRTY_MINUTES)
        assertThat(day.screen).isEqualTo(AWAKE)
        day.front(true)
        day.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)

        val night = wall(at = DAY.atTime(23, 0))
        night.front(false)
        assertThat(night.screen).isEqualTo(AWAKE)
        night.after(THIRTY_MINUTES)
        assertThat(night.screen).isEqualTo(AWAKE)
        night.front(true)
        assertThat(night.screen).isEqualTo(AWAKE)
        night.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** Fails if new night times wait for the next tick, or cover a hold. */
    @Test
    fun changedNightTimesApplyAtOnce() = runTest {
        val earlier = DisplayPrefs(nightFrom = LocalTime.of(20, 0))
        val free = wall(at = DAY.atTime(21, 0))
        free.set(earlier)
        assertThat(free.screen).isEqualTo(NIGHT_CLOCK)

        val held = wall(at = DAY.atTime(21, 0))
        held.hold(true)
        held.set(earlier)
        assertThat(held.screen).isEqualTo(AWAKE)
        held.hold(false)
        held.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** Fails if equal night times mean all night rather than no night. */
    @Test
    fun equalNightTimesMeanNoNight() = runTest {
        val ten = LocalTime.of(22, 0)
        val wall = wall(at = DAY.atTime(23, 0), prefs = DisplayPrefs(nightFrom = ten, nightUntil = ten))
        assertThat(wall.screen).isEqualTo(AWAKE)
        assertThat(wall.display.isNight()).isFalse()
    }

    /** Fails if the camera is wanted at night, out of front, switched off, not allowed or before the night is known. */
    @Test
    fun theCameraIsWantedOnlyInTheDayInFront() = runTest {
        val day = wall()
        assertThat(day.wanted).isTrue()
        day.asleep()
        assertThat(day.wanted).isTrue()
        day.front(false)
        assertThat(day.wanted).isFalse()

        val night = wall(at = DAY.atTime(23, 0))
        assertThat(night.wanted).isFalse()
        night.tap()
        assertThat(night.screen).isEqualTo(AWAKE)
        assertThat(night.wanted).isFalse()

        assertThat(wall(prefs = DisplayPrefs(wakeByCamera = false)).wanted).isFalse()
        listOf(CameraState.NOT_ASKED, CameraState.REFUSED, CameraState.NO_CAMERA).forEach {
            assertThat(wall(camera = it).wanted).isFalse()
        }
        assertThat(wall(prefs = null).wanted).isFalse()
        assertThat(wall(at = null).wanted).isFalse()

        val allowedLater = wall(camera = CameraState.NOT_ASKED)
        allowedLater.camera.value = CameraState.ALLOWED
        runCurrent()
        assertThat(allowedLater.wanted).isTrue()
    }

    /** 11 §5.6: Fails if Holiday mode turning on leaves the day screen up. */
    @Test
    fun holidayDarkensAtOnce() = runTest {
        val wall = wall()
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.away(true)
        assertThat(wall.screen).isEqualTo(ASLEEP)
    }

    /** 11 §5.6: Fails if night coming on shows the clock while away. */
    @Test
    fun holidayNeverShowsTheNightClock() = runTest {
        val wall = wall(at = DAY.atTime(21, 59))
        wall.away(true)
        wall.at(22, 0)
        assertThat(wall.screen).isEqualTo(ASLEEP)
        wall.tap()
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(FIVE_MINUTES)
        assertThat(wall.screen).isEqualTo(ASLEEP)
        assertThat(wall(at = DAY.atTime(23, 0)).also { it.away(true) }.screen).isEqualTo(ASLEEP)
    }

    /** 11 §5.6: Fails if a tap while away doesn't wake, or the wake sleeps early, late or not at night. */
    @Test
    fun aTapWakesItAndItSleepsAgain() = runTest {
        val day = wall()
        day.away(true)
        day.tap()
        assertThat(day.screen).isEqualTo(AWAKE)
        day.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)

        val night = wall(at = DAY.atTime(23, 0))
        night.away(true)
        night.tap()
        assertThat(night.screen).isEqualTo(AWAKE)
        night.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** 11 §5.6: Fails if the camera is wanted, or wakes the screen, while away. */
    @Test
    fun noCameraWhileAway() = runTest {
        val wall = wall()
        assertThat(wall.wanted).isTrue()
        wall.away(true)
        assertThat(wall.wanted).isFalse()
        wall.move()
        assertThat(wall.screen).isEqualTo(ASLEEP)
    }

    /** 11 §5.6: Fails if Holiday mode ending leaves the wall dark, or night after it skips the minute awake. */
    @Test
    fun holidayEndingWakesIt() = runTest {
        val day = wall()
        day.away(true)
        day.away(false)
        assertThat(day.screen).isEqualTo(AWAKE)
        assertThat(day.wanted).isTrue()

        val night = wall(at = DAY.atTime(23, 0))
        night.away(true)
        night.away(false)
        assertThat(night.screen).isEqualTo(AWAKE)
        night.changesAt(NIGHT_AWAKE_MS, AWAKE, NIGHT_CLOCK)
    }

    /** 11 §5.3: Fails if the Holiday sheet closing leaves the wall awake for the usual wait. */
    @Test
    fun theSheetClosingDarkensAtOnce() = runTest {
        val wall = wall()
        wall.hold(true)
        wall.away(true)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.after(THIRTY_MINUTES)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.hold(false)
        assertThat(wall.screen).isEqualTo(ASLEEP)
    }

    /** 11 §5.6: Fails if a hold ending while already away blanks the screen at once instead of after the wait. */
    @Test
    fun heldStaysAwakeThenSleepsAfterTheUsualWait() = runTest {
        val wall = wall()
        wall.away(true)
        wall.tap()
        wall.hold(true)
        wall.after(THIRTY_MINUTES)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.hold(false)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** 11 §5.6: Fails if coming back from another app while away leaves the screen dark or awake for good. */
    @Test
    fun returningFromAnotherAppWhileAwayWakesThenSleeps() = runTest {
        val wall = wall()
        wall.away(true)
        wall.front(false)
        wall.front(true)
        assertThat(wall.screen).isEqualTo(AWAKE)
        wall.changesAt(FIVE_MINUTES, AWAKE, ASLEEP)
    }

    /** Pin: Fails if a Holiday mode not yet read darkens the wall. */
    @Test
    fun holidayNotYetReadIsNotAway() = runTest {
        val wall = wall()
        wall.away(null)
        wall.after(FIVE_MINUTES - 1)
        assertThat(wall.screen).isEqualTo(AWAKE)
    }
}
