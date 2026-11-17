package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

class RepeatWordsTest {
    private fun weekly(vararg days: DayOfWeek) = Schedule.Weekly(days.toSet())

    @Test
    fun aOneOffIsOnce() {
        assertThat(repeatWords(Schedule.Once(LocalDate.of(2026, 11, 20)))).isEqualTo("Once")
    }

    @Test
    fun oneDayNamesTheDayInFull() {
        assertThat(repeatWords(weekly(DayOfWeek.FRIDAY))).isEqualTo("Every Friday")
    }

    @Test
    fun allSevenAreEveryDay() {
        assertThat(repeatWords(Schedule.Weekly(DayOfWeek.entries.toSet()))).isEqualTo("Every day")
    }

    @Test
    fun threeOrMoreInARowAreARange() {
        assertThat(repeatWords(Schedule.Weekly(DayOfWeek.entries.take(5).toSet()))).isEqualTo("Mon to Fri")
        assertThat(repeatWords(weekly(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))).isEqualTo("Wed to Sun")
    }

    @Test
    fun anythingElseListsTheShortNames() {
        assertThat(repeatWords(weekly(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))).isEqualTo("Mon, Wed, Fri")
        assertThat(repeatWords(weekly(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY))).isEqualTo("Sat, Sun")
    }

    @Test
    fun aRunThatWrapsPastSundayIsListed() {
        assertThat(repeatWords(weekly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY))).isEqualTo("Mon, Sat, Sun")
    }
}
