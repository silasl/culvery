package uk.co.siland.culvery.capability.holiday

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class HolidayWordsTest {
    private val today = LocalDate.of(2026, 10, 28)
    private val back = LocalDate.of(2026, 10, 31)

    /** Fails if a sheet line differs from the copy, or its dates aren't filled in. */
    @Test
    fun theSheetsLines() {
        assertThat(HolidayWords.stepLine(HolidayStep.LIGHTS_ON, back)).isEqualTo("Switch on Holiday lights now.")
        assertThat(HolidayWords.stepLine(HolidayStep.ECO_ON, back))
            .isEqualTo("Put the heating on Eco now, and take it off Eco at 00:01 on Saturday 31 October.")
        assertThat(HolidayWords.stepLine(HolidayStep.HOOVER, back)).isEqualTo("Run the hoover at 10:00 on Friday 30 October.")
        assertThat(HolidayWords.stepLine(HolidayStep.ECO_OFF, back)).isNull()
        assertThat(HolidayWords.DOOR).isEqualTo("Keep the doorbell quiet. Rings and motion still show in the Door tab.")
        assertThat(HolidayWords.SCREEN).isEqualTo("Turn the screen off until someone taps it.")
    }

    /** Fails if the tile's back date isn't short, or the spoken one isn't in full. */
    @Test
    fun theBackDate() {
        assertThat(HolidayWords.backShort(back)).isEqualTo("Back Sat 31 Oct")
        assertThat(HolidayWords.backShort(LocalDate.of(2026, 11, 2))).isEqualTo("Back Mon 2 Nov")
        assertThat(HolidayWords.backShort(LocalDate.of(2027, 9, 4))).isEqualTo("Back Sat 4 Sep")
        assertThat(HolidayWords.backSpoken(back)).isEqualTo("Saturday 31 October")
    }

    /** Fails if the calendar line names the weekday alone a week or more away, or the full date within six days. */
    @Test
    fun theCalendarLine() {
        val cornwall = BackDatePick("Cornwall", LocalDate.of(2026, 11, 1))
        assertThat(HolidayWords.fromCalendar(cornwall, today)).isEqualTo("From the calendar: Cornwall, ends Sunday.")
        assertThat(HolidayWords.fromCalendar(cornwall.copy(back = today.plusDays(6)), today)).isEqualTo("From the calendar: Cornwall, ends Tuesday.")
        assertThat(HolidayWords.fromCalendar(cornwall.copy(back = today.plusDays(7)), today))
            .isEqualTo("From the calendar: Cornwall, ends Wednesday 4 November.")
        assertThat(HolidayWords.fromCalendar(cornwall.copy(back = LocalDate.of(2027, 1, 2)), today))
            .isEqualTo("From the calendar: Cornwall, ends Saturday 2 January.")
        assertThat(HolidayWords.fromCalendar(null, today)).isEqualTo("Pick the day you’re back.")
    }

    /** Fails if TalkBack's tile words differ from the copy. */
    @Test
    fun theTileSpoken() {
        assertThat(HolidayWords.tileSpoken(false, null)).isEqualTo("Holiday mode, off.")
        assertThat(HolidayWords.tileSpoken(true, back)).isEqualTo("Holiday mode, on, back Saturday 31 October.")
    }
}
