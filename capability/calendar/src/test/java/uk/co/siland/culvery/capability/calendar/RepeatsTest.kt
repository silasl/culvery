package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd
import uk.co.siland.culvery.core.repeat.recurrenceRule
import uk.co.siland.culvery.core.repeat.wording

/** The detail sheet reads a series back in the editor's words (9 §3.4). */
class RepeatsTest {
    private val london = ZoneId.of("Europe/London")

    // Saturday 26 September 2026.
    private val saturday = LocalDate.of(2026, 9, 26)

    /** Never "Yes": whatever either editor writes, the detail sheet's Repeats row starts with the words for it. */
    @Test
    fun whatSaveWritesReadsBackInItsWords() {
        val starts = listOf(EventTime.AllDay(LocalDate.of(2026, 9, 23)), EventTime.Timed(Instant.parse("2026-09-23T17:00:00Z")))
        val day = LocalDate.of(2026, 9, 23)
        for (start in starts) {
            for (choice in RepeatChoice.entries - RepeatChoice.Never) {
                for (end in listOf(RepeatEnd.Never, RepeatEnd.After(5))) {
                    assertThat(repeatsLabel(recurrenceRule(choice, end, day), start, london, day)).startsWith(choice.wording(day))
                }
            }
        }
    }

    @Test
    fun aTimedStartIsReadInTheHouseholdZone() {
        // 23:30 UTC on Friday 25 September is 00:30 on Saturday 26 September in London.
        assertThat(repeatsLabel("RRULE:FREQ=WEEKLY;BYDAY=SA", EventTime.Timed(Instant.parse("2026-09-25T23:30:00Z")), london, saturday))
            .isEqualTo("Every Saturday")
    }
}
