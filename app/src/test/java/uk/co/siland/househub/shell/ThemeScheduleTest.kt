package uk.co.siland.househub.shell

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test
import uk.co.siland.househub.core.plugin.SunTimes

class ThemeScheduleTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun fallbackIsLightFromSevenUntilNineteen() {
        assertThat(ThemeSchedule.isDark(t(6, 59), null)).isTrue()
        assertThat(ThemeSchedule.isDark(t(7, 0), null)).isFalse()
        assertThat(ThemeSchedule.isDark(t(18, 59), null)).isFalse()
        assertThat(ThemeSchedule.isDark(t(19, 0), null)).isTrue()
    }

    @Test
    fun usesSunriseAndSunsetWhenKnown() {
        val sun = SunTimes(t(6, 12), t(19, 48))
        assertThat(ThemeSchedule.isDark(t(6, 11), sun)).isTrue()
        assertThat(ThemeSchedule.isDark(t(6, 12), sun)).isFalse()
        assertThat(ThemeSchedule.isDark(t(19, 30), sun)).isFalse()
        assertThat(ThemeSchedule.isDark(t(19, 48), sun)).isTrue()
    }

    @Test
    fun invertedSunTimesFallBack() {
        val nonsense = SunTimes(t(20, 0), t(4, 0))
        assertThat(ThemeSchedule.isDark(t(12, 0), nonsense)).isFalse()
        assertThat(ThemeSchedule.isDark(t(22, 0), nonsense)).isTrue()
    }
}
