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

    /** **pin**: the entry's dates exist with the stub look. */
    @Test
    fun christmasRunsFrom1DecemberTo6January() {
        val dates = SeasonalTheme.CHRISTMAS.defaultDates
        assertThat(LocalDate.of(2026, 11, 30) in dates).isFalse()
        assertThat(LocalDate.of(2026, 12, 1) in dates).isTrue()
        assertThat(LocalDate.of(2026, 12, 31) in dates).isTrue()
        assertThat(LocalDate.of(2027, 1, 1) in dates).isTrue()
        assertThat(LocalDate.of(2027, 1, 6) in dates).isTrue()
        assertThat(LocalDate.of(2027, 1, 7) in dates).isFalse()
    }

    /** **pin**: no day holds two themes by default, so the order in the enum never has to settle one. */
    @Test
    fun theThemesDontOverlap() {
        listOf(2026, 2028).forEach { year ->
            generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == year }.forEach { day ->
                assertThat(SeasonalTheme.entries.count { day in it.defaultDates }).isAtMost(1)
            }
        }
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
        assertThat(defaults.getValue(SeasonalTheme.CHRISTMAS)).isEqualTo(SeasonDates(MonthDay.of(12, 1), MonthDay.of(1, 6)))
    }

    @Test
    fun everyThemeHasALookWithBothSets() {
        SeasonalTheme.entries.forEach {
            assertThat(it.look.night.isLight).isFalse()
            assertThat(it.look.day.isLight).isTrue()
        }
    }

    /** Pin: a 29 February end still holds the last day of February in a year without one. */
    @Test
    fun aLeapDayEndInAShortYearEndsOnThe28th() {
        val dates = SeasonDates(MonthDay.of(2, 20), MonthDay.of(2, 29))
        assertThat(LocalDate.of(2027, 2, 28) in dates).isTrue()
        assertThat(LocalDate.of(2027, 3, 1) in dates).isFalse()
    }

    /** Fails if a 29 February start in a year without one isn't 1 March, or a leap year or another day moves. */
    @Test
    fun firstDayInPutsALeapDayOn1March() {
        assertThat(MonthDay.of(2, 29).firstDayIn(2027)).isEqualTo(LocalDate.of(2027, 3, 1))
        assertThat(MonthDay.of(2, 29).firstDayIn(2028)).isEqualTo(LocalDate.of(2028, 2, 29))
        assertThat(MonthDay.of(12, 1).firstDayIn(2026)).isEqualTo(LocalDate.of(2026, 12, 1))
    }

    /** Pin: a range starting on 29 February holds 1 March, not the 28th, in a year without one. */
    @Test
    fun aLeapDayStartsOn1MarchInACommonYear() {
        val dates = SeasonDates(MonthDay.of(2, 29), MonthDay.of(3, 10))
        assertThat(LocalDate.of(2027, 2, 28) in dates).isFalse()
        assertThat(LocalDate.of(2027, 3, 1) in dates).isTrue()
        assertThat(LocalDate.of(2028, 2, 28) in dates).isFalse()
        assertThat(LocalDate.of(2028, 2, 29) in dates).isTrue()
    }
}
