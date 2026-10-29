package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.MonthDay
import org.junit.Test

class SeasonalTest {
    private val halloween = SeasonalTheme.HALLOWEEN.defaultDates

    @Test
    fun halloweenRunsFrom24To31October() {
        assertThat(LocalDate.of(2026, 10, 23) in halloween).isFalse()
        assertThat(LocalDate.of(2026, 10, 24) in halloween).isTrue()
        assertThat(LocalDate.of(2026, 10, 31) in halloween).isTrue()
        assertThat(LocalDate.of(2026, 11, 1) in halloween).isFalse()
    }

    @Test
    fun aWrappedRangeCoversBothEnds() {
        val christmas = SeasonDates(MonthDay.of(12, 20), MonthDay.of(1, 2))
        assertThat(LocalDate.of(2026, 12, 19) in christmas).isFalse()
        assertThat(LocalDate.of(2026, 12, 20) in christmas).isTrue()
        assertThat(LocalDate.of(2027, 1, 1) in christmas).isTrue()
        assertThat(LocalDate.of(2027, 1, 2) in christmas).isTrue()
        assertThat(LocalDate.of(2027, 1, 3) in christmas).isFalse()
    }

    @Test
    fun aOneDayRange() {
        val day = SeasonDates(MonthDay.of(2, 14), MonthDay.of(2, 14))
        assertThat(LocalDate.of(2026, 2, 13) in day).isFalse()
        assertThat(LocalDate.of(2026, 2, 14) in day).isTrue()
        assertThat(LocalDate.of(2026, 2, 15) in day).isFalse()
    }

    @Test
    fun theDefaultsAreEveryThemesOwn() {
        val defaults = defaultSeasonDates()
        assertThat(defaults.keys).containsExactlyElementsIn(SeasonalTheme.entries)
        assertThat(defaults.getValue(SeasonalTheme.HALLOWEEN)).isEqualTo(SeasonDates(MonthDay.of(10, 24), MonthDay.of(10, 31)))
    }

    @Test
    fun everyThemeHasALookWithBothSets() {
        SeasonalTheme.entries.forEach {
            assertThat(it.look.night.isLight).isFalse()
            assertThat(it.look.day.isLight).isTrue()
        }
    }
}
