package uk.co.siland.culvery.display

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.display.Screen.ASLEEP
import uk.co.siland.culvery.display.Screen.AWAKE
import uk.co.siland.culvery.display.Screen.NIGHT_CLOCK

private val DAY: LocalDate = LocalDate.of(2026, 10, 19)
private val SUN = SunTimes(LocalTime.of(7, 12), LocalTime.of(18, 5))
private const val DAYTIME = 0.70f
private const val EVENING = 0.25f

/** A brightness over inputs the test sets, on the test's scheduler. */
private class Room(private val test: TestScope, at: LocalDateTime?, night: Boolean?) {
    val screen = MutableStateFlow(AWAKE)
    val night = MutableStateFlow(night)
    val inFront = MutableStateFlow(true)
    val now = MutableStateFlow(at)
    val sun = MutableStateFlow<SunTimes?>(SUN)
    val prefs = MutableStateFlow(DisplayPrefs())
    val brightness = ScreenBrightness(screen, this.night, inFront, now, sun, prefs, test.backgroundScope)

    val level: Float get() = brightness.level.value

    fun after(ms: Long) {
        test.advanceTimeBy(ms)
        test.runCurrent()
    }

    fun <T> set(flow: MutableStateFlow<T>, value: T) {
        flow.value = value
        test.runCurrent()
    }
}

private fun TestScope.room(at: LocalDateTime? = DAY.atTime(12, 0), night: Boolean? = false) =
    Room(this, at, night).also { runCurrent() }

@RunWith(AndroidJUnit4::class)
class ScreenBrightnessTest {
    /** Fails if evening isn't the theme's dark side of sunset, or starts a minute late. */
    @Test
    fun eveningIsTheThemesDarkWithUsableSunTimes() {
        assertThat(isEvening(LocalTime.of(12, 0), SUN)).isFalse()
        assertThat(isEvening(LocalTime.of(18, 4), SUN)).isFalse()
        assertThat(isEvening(LocalTime.of(18, 5), SUN)).isTrue()
    }

    /** Fails if missing or unusable sun times fall back to the theme's 19:00 instead of the daytime level all day. */
    @Test
    fun noUsableSunTimesIsNeverEvening() {
        assertThat(isEvening(LocalTime.of(23, 0), null)).isFalse()
        assertThat(isEvening(LocalTime.of(23, 0), SunTimes(LocalTime.of(19, 0), LocalTime.of(7, 0)))).isFalse()
    }

    /** Fails if the dark screen or the night clock use a set level rather than the window's lowest. */
    @Test
    fun asleepAndTheNightClockAreTheLowest() {
        val prefs = DisplayPrefs()
        for (night in listOf(false, true)) {
            for (evening in listOf(false, true)) {
                assertThat(targetBrightness(ASLEEP, true, night, evening, prefs)).isEqualTo(LOWEST)
                assertThat(targetBrightness(NIGHT_CLOCK, true, night, evening, prefs)).isEqualTo(LOWEST)
            }
        }
    }

    /** Fails if awake by day isn't the daytime percent, or awake in the evening isn't the evening percent. */
    @Test
    fun awakeIsTheDayOrEveningLevel() {
        val prefs = DisplayPrefs()
        assertThat(targetBrightness(AWAKE, true, night = false, evening = false, prefs)).isEqualTo(DAYTIME)
        assertThat(targetBrightness(AWAKE, true, night = false, evening = true, prefs)).isEqualTo(EVENING)
    }

    /** Fails if a tap waking the night clock before sunset in summer dazzles at the daytime level. */
    @Test
    fun aTapAtNightGivesTheEveningLevel() {
        assertThat(targetBrightness(AWAKE, true, night = true, evening = false, DisplayPrefs())).isEqualTo(EVENING)
    }

    /** Fails if Culvery's window keeps any level of its own while another app is in front. */
    @Test
    fun notInFrontLeavesAndroidsBrightness() {
        for (screen in Screen.entries) {
            assertThat(targetBrightness(screen, false, night = false, evening = false, DisplayPrefs())).isEqualTo(ANDROIDS)
            assertThat(targetBrightness(screen, false, night = true, evening = true, DisplayPrefs())).isEqualTo(ANDROIDS)
        }
    }

    /** Fails if night not known yet is taken as day, which would dazzle at a restart in the night. */
    @Test
    fun nightNotKnownYetIsTheEveningLevel() = runTest {
        val room = room(at = null, night = null)
        assertThat(room.level).isEqualTo(EVENING)

        val noon = room(night = null)
        assertThat(noon.level).isEqualTo(EVENING)
    }

    /** Fails if night becoming known as day after a daytime restart waits for another input before the daytime level. */
    @Test
    fun nightBecomingKnownChangesTheLevelAtOnce() = runTest {
        val room = room(night = null)
        assertThat(room.level).isEqualTo(EVENING)

        room.set(room.night, false)
        assertThat(room.level).isEqualTo(DAYTIME)
    }

    /** Fails if the switch at sunset jumps, steps other than every 2 s, or ends a millisecond early or late. */
    @Test
    fun theSwitchAtSunsetFadesOverAMinute() = runTest {
        val room = room(at = DAY.atTime(18, 4))
        assertThat(room.level).isEqualTo(DAYTIME)

        room.set(room.now, DAY.atTime(18, 5))
        assertThat(room.level).isEqualTo(DAYTIME)
        room.after(30_000)
        assertThat(room.level).isAtLeast(0.46f)
        assertThat(room.level).isAtMost(0.49f)
        room.after(28_000)
        assertThat(room.level).isGreaterThan(EVENING)
        room.after(1_999)
        assertThat(room.level).isGreaterThan(EVENING)
        room.after(1)
        assertThat(room.level).isEqualTo(EVENING)
    }

    /** Fails if the next minute's tick during the fade cuts it short or starts it again. */
    @Test
    fun aTickDuringTheFadeLetsItRun() = runTest {
        val room = room(at = DAY.atTime(18, 4))
        room.set(room.now, DAY.atTime(18, 5))
        room.after(20_000)
        val partWay = room.level
        room.set(room.now, DAY.atTime(18, 6))
        assertThat(room.level).isEqualTo(partWay)
        room.after(40_000)
        assertThat(room.level).isEqualTo(EVENING)
    }

    /** Fails if sleeping or waking fades rather than changing at once. */
    @Test
    fun wakingAndSleepingAreAtOnce() = runTest {
        val room = room()
        room.set(room.screen, ASLEEP)
        assertThat(room.level).isEqualTo(LOWEST)
        room.set(room.screen, AWAKE)
        assertThat(room.level).isEqualTo(DAYTIME)
        room.set(room.screen, NIGHT_CLOCK)
        assertThat(room.level).isEqualTo(LOWEST)
        room.set(room.inFront, false)
        assertThat(room.level).isEqualTo(ANDROIDS)
    }

    /** Fails if a slider let go fades, or doesn't reach the window. */
    @Test
    fun aSliderChangeIsAtOnce() = runTest {
        val room = room()
        room.set(room.prefs, DisplayPrefs(dayPercent = 40))
        assertThat(room.level).isEqualTo(0.40f)
    }

    /** Fails if the level goes anywhere but Culvery's own window. */
    @Test
    fun theLevelIsSetOnCulverysWindow() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        applyBrightness(activity.window, EVENING)
        assertThat(activity.window.attributes.screenBrightness).isEqualTo(EVENING)
    }
}
