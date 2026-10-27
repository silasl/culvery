package uk.co.siland.culvery.capability.holiday

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test
import uk.co.siland.culvery.core.house.MultiDayEvent

class BackDatePickTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val tomorrow = today.plusDays(1)

    /** Fails if nothing on the calendar still yields a guess. */
    @Test
    fun noEventsNoPick() {
        assertThat(backDatePick(emptyList(), today)).isNull()
    }

    /** Fails if an event on today isn't picked, or its back date isn't its last day. */
    @Test
    fun oneEventOnTodayIsPicked() {
        val cornwall = MultiDayEvent("Cornwall", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 1))

        assertThat(backDatePick(listOf(cornwall), today)).isEqualTo(BackDatePick("Cornwall", LocalDate.of(2026, 11, 1)))
    }

    /** 11 §5.2: fails if an event starting tomorrow beats one the family are already in. */
    @Test
    fun aHolidayOnTodayBeatsHalfTermTomorrow() {
        val cornwall = MultiDayEvent("Cornwall", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 1))
        val halfTerm = MultiDayEvent("Half term", tomorrow, LocalDate.of(2026, 11, 6))

        assertThat(backDatePick(listOf(halfTerm, cornwall), today)?.title).isEqualTo("Cornwall")
    }

    /** Fails if an event starting tomorrow isn't picked when nothing is on today, or one starting later is. */
    @Test
    fun tomorrowsEventWhenNothingIsOnToday() {
        val halfTerm = MultiDayEvent("Half term", tomorrow, LocalDate.of(2026, 11, 6))
        val later = MultiDayEvent("Cornwall", today.plusDays(2), LocalDate.of(2026, 11, 8))

        assertThat(backDatePick(listOf(later, halfTerm), today)).isEqualTo(BackDatePick("Half term", LocalDate.of(2026, 11, 6)))
        assertThat(backDatePick(listOf(later), today)).isNull()
    }

    /** Fails if, of the events on today, the latest to start doesn't win, then the later last day. */
    @Test
    fun theMostRecentlyStartedWins() {
        val halfTerm = MultiDayEvent("Half term", LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 1))
        val cornwall = MultiDayEvent("Cornwall", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 30))
        val longer = MultiDayEvent("Cornwall and back", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 31))

        assertThat(backDatePick(listOf(halfTerm, cornwall), today)?.title).isEqualTo("Cornwall")
        assertThat(backDatePick(listOf(cornwall, longer, halfTerm), today)?.title).isEqualTo("Cornwall and back")
    }

    /** Fails if an event ending today, or past the 90 days the picker allows, is picked. */
    @Test
    fun endingTodayOrBeyond90DaysIsIgnored() {
        val endingToday = MultiDayEvent("Half term", LocalDate.of(2026, 10, 24), today)
        val tooLong = MultiDayEvent("Cornwall", LocalDate.of(2026, 10, 27), today.plusDays(91))
        val longest = MultiDayEvent("Cornwall", LocalDate.of(2026, 10, 26), today.plusDays(90))

        assertThat(backDatePick(listOf(endingToday, tooLong), today)).isNull()
        assertThat(backDatePick(listOf(endingToday, tooLong, longest), today)?.back).isEqualTo(today.plusDays(90))
    }

    /** 11 §6: fails if the event's title can be read out of a log line. */
    @Test
    fun toStringHasNoTitle() {
        val text = BackDatePick("Cornwall", LocalDate.of(2026, 11, 1)).toString()
        assertThat(text).doesNotContain("Cornwall")
        assertThat(text).doesNotContain("2026")
    }
}
