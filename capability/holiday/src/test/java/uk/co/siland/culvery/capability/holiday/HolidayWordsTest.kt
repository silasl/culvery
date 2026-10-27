package uk.co.siland.culvery.capability.holiday

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test
import uk.co.siland.culvery.core.house.PlugChange

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

    /** Fails if the plug lines don't sit after the heating and before the hoover, or show with no changes. */
    @Test
    fun willLinesPutThePlugsAfterTheHeating() {
        val steps = listOf(HolidayStep.LIGHTS_ON, HolidayStep.ECO_ON, HolidayStep.PLUGS_ON, HolidayStep.HOOVER, HolidayStep.ECO_OFF)
        val plugs = listOf(PlugChange("Lamp", false), PlugChange("Desk", true))

        assertThat(HolidayWords.willLines(steps, back, plugs)).containsExactly(
            HolidayWords.stepLine(HolidayStep.LIGHTS_ON, back),
            HolidayWords.stepLine(HolidayStep.ECO_ON, back),
            "Switch Lamp off.",
            "Switch Desk on.",
            "Plugs go back as they were when you turn it off.",
            HolidayWords.stepLine(HolidayStep.HOOVER, back),
            HolidayWords.DOOR,
            HolidayWords.SCREEN,
        ).inOrder()
        assertThat(HolidayWords.willLines(steps, back, emptyList())).containsExactly(
            HolidayWords.stepLine(HolidayStep.LIGHTS_ON, back),
            HolidayWords.stepLine(HolidayStep.ECO_ON, back),
            HolidayWords.stepLine(HolidayStep.HOOVER, back),
            HolidayWords.DOOR,
            HolidayWords.SCREEN,
        ).inOrder()
    }

    /** Fails if the plugs step lists a line of its own, or its missed, threw and turn-off lines differ from the copy. */
    @Test
    fun thePlugsStepsOtherLines() {
        assertThat(HolidayWords.stepLine(HolidayStep.PLUGS_ON, back)).isNull()
        assertThat(HolidayWords.missedLine(HolidayStep.PLUGS_ON)).isEqualTo("The plugs didn’t switch. Switch them on the Plugs tab.")
        assertThat(HolidayWords.failedLine(HolidayStep.PLUGS_ON)).isEqualTo("The plugs didn’t switch. They’ll keep trying.")
        assertThat(HolidayWords.PLUGS_LEAVE_THREW).isEqualTo("Some plugs may not have gone back. Check them on the Plugs tab.")
    }
}
