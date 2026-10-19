package uk.co.siland.culvery.capability.calendar.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Rows of 80 px 9 apart, a 30 px divider and nothing line, 480 px of list with the pill taking its bottom 68. */
class TodayFillTest {
    private fun fit(today: List<Int>, tomorrow: List<Int> = emptyList()) =
        fitToday(today, tomorrow, nothing = 30, divider = 30, gap = 9, available = 480, pillSpace = 68)

    private fun rows(count: Int, height: Int = 80) = List(count) { height }

    @Test
    fun moreThanFiveScrollsWithNMinusFour() {
        assertThat(fit(rows(7))).isEqualTo(TodayFit(0, true, 3))
    }

    @Test
    fun sixScrollsWithTwoMore() {
        assertThat(fit(rows(6))).isEqualTo(TodayFit(0, true, 2))
    }

    /** Three rows of 120 clear the pill (the fourth ends at 507 > 412), so the pill counts four more. */
    @Test
    fun tallRowsCountMoreThanNMinusFour() {
        assertThat(fit(rows(7, 120))).isEqualTo(TodayFit(0, true, 4))
    }

    @Test
    fun exactlyFiveIsJustThose() {
        assertThat(fit(rows(5))).isEqualTo(TodayFit(0, false, 0))
    }

    @Test
    fun fewerThanFiveTopUpFromTomorrow() {
        for (n in 1..4) {
            assertThat(fit(rows(n), rows(TODAY_ROWS - n, 60))).isEqualTo(TodayFit(TODAY_ROWS - n, false, 0))
        }
    }

    @Test
    fun nothingTodayShowsFiveFromTomorrow() {
        assertThat(fit(emptyList(), rows(5, 60))).isEqualTo(TodayFit(5, false, 0))
    }

    /** Two tomorrow rows would end at 525: the last goes, and today's three stay without a scroll. */
    @Test
    fun aLongTitleDropsTomorrowRowsFirst() {
        assertThat(fit(rows(3, 110), rows(2, 60))).isEqualTo(TodayFit(1, false, 0))
    }

    @Test
    fun noTomorrowRowFittingLeavesNoDividerAndNoScroll() {
        assertThat(fit(rows(3, 150), rows(1, 60))).isEqualTo(TodayFit(0, false, 0))
    }

    @Test
    fun todayAloneOverflowingScrolls() {
        assertThat(fit(rows(4, 140))).isEqualTo(TodayFit(0, true, 2))
    }
}
