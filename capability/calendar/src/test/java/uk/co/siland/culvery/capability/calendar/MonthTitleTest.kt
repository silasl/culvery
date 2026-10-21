package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class MonthTitleTest {
    private val wed2Sep = LocalDate.of(2026, 9, 2)
    private val wed23Sep = LocalDate.of(2026, 9, 23)
    private val wed30Sep = LocalDate.of(2026, 9, 30)

    @Test
    fun oneMonth() {
        assertThat(monthTitle(wed2Sep)).isEqualTo(MonthTitle("September", "2026", "September 2026"))
    }

    @Test
    fun twoMonths() {
        assertThat(monthTitle(wed23Sep).months).isEqualTo("September")
        assertThat(monthTitle(wed30Sep)).isEqualTo(MonthTitle("September – October", "2026", "September to October 2026"))
    }

    @Test
    fun acrossTheYear() {
        val title = monthTitle(LocalDate.of(2026, 12, 30))
        assertThat(title.months).isEqualTo("December 2026 – January")
        assertThat(title.year).isEqualTo("2027")
        assertThat(title.spoken).isEqualTo("December 2026 to January 2027")
    }

    @Test
    fun theRange() {
        assertThat(weekRange(wed23Sep)).isEqualTo("Wed 23 – Tue 29 Sep")
        assertThat(weekRange(wed30Sep)).isEqualTo("Wed 30 Sep – Tue 6 Oct")
    }

    @Test
    fun theSubtitle() {
        assertThat(weekSubtitleLine(0, wed23Sep, "Updated from Google Calendar 2 minutes ago"))
            .isEqualTo("This week · Wed 23 – Tue 29 Sep · updated from Google Calendar 2 minutes ago")
        assertThat(weekSubtitleLine(1, wed30Sep, "Not updated yet")).isEqualTo("Next week · Wed 30 Sep – Tue 6 Oct · not updated yet")
        assertThat(weekSubtitleLine(2, wed23Sep, "Updated just now")).startsWith("In 2 weeks · ")
        assertThat(weekSubtitleLine(0, wed23Sep, "Out of date — updated 3 hours ago"))
            .isEqualTo("This week · Wed 23 – Tue 29 Sep · out of date — updated 3 hours ago")
        assertThat(weekSubtitleLine(0, wed23Sep, "The tablet is offline. Check the Wi-Fi, then Update now."))
            .isEqualTo("This week · Wed 23 – Tue 29 Sep · The tablet is offline. Check the Wi-Fi, then Update now.")
        assertThat(weekSubtitleLine(0, wed23Sep, "Updating…")).endsWith("· Updating…")
    }
}
