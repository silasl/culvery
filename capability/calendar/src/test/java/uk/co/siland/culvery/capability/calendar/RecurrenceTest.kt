package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** 5 §A.4: what Save writes for each choice, both UNTIL forms, and that it reads back as a simple label. */
class RecurrenceTest {
    private val london = ZoneId.of("Europe/London")

    @Test
    fun eachChoiceWritesItsFrequencyAndInterval() {
        assertThat(RepeatChoice.entries.map { recurrenceRule(it, endsOn = null, allDay = false, zone = london) }).containsExactly(
            null,
            "RRULE:FREQ=DAILY;INTERVAL=1",
            "RRULE:FREQ=WEEKLY;INTERVAL=1",
            "RRULE:FREQ=WEEKLY;INTERVAL=2",
            "RRULE:FREQ=MONTHLY;INTERVAL=1",
            "RRULE:FREQ=YEARLY;INTERVAL=1",
        ).inOrder()
        assertThat(RepeatChoice.entries.map { it.label })
            .containsExactly("Never", "Every day", "Every week", "Every 2 weeks", "Every month", "Every year").inOrder()
    }

    @Test
    fun anAllDaySeriesEndsOnADate() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 12, 31), allDay = true, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231")
    }

    @Test
    fun aTimedSeriesEndsAtTheEndOfThatDayInTheHouseholdZoneAsUtc() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 12, 31), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231T235959Z")
        assertThat(recurrenceRule(RepeatChoice.Daily, LocalDate.of(2027, 6, 30), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=DAILY;INTERVAL=1;UNTIL=20270630T225959Z")
    }

    /** The change happens in the small hours, so the end of the day is in the evening's offset. */
    @Test
    fun onTheDaysTheClocksChangeUntilUsesThatEveningsOffset() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 10, 25), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261025T235959Z")
        assertThat(recurrenceRule(RepeatChoice.Weekly, LocalDate.of(2026, 3, 29), allDay = false, zone = london))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20260329T225959Z")
    }

    /** Never "Yes": whatever Save writes, the detail sheet's Repeats row reads as the chip that wrote it. */
    @Test
    fun whatSaveWritesReadsBackAsItsChipsLabel() {
        val starts = listOf(
            EventTime.AllDay(LocalDate.of(2026, 9, 23)) to true,
            EventTime.Timed(Instant.parse("2026-09-23T17:00:00Z")) to false,
        )
        for ((start, allDay) in starts) {
            for (choice in RepeatChoice.entries - RepeatChoice.Never) {
                for (end in listOf(null, LocalDate.of(2026, 12, 31))) {
                    assertThat(repeatsLabel(recurrenceRule(choice, end, allDay, london), start, london)).isEqualTo(choice.label)
                }
            }
        }
    }
}
