package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.PersonId

/** "Use this" on a new event (9c §4.4): Wednesday 23 September 2026, 10:00 in London. Robolectric backs `mutableStateOf`. */
@RunWith(AndroidJUnit4::class)
class EditorFormSuggestionTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = PersonId("alex")
    private val sam = PersonId("sam")
    private val mia = PersonId("mia")
    private val order = listOf(alex, sam, mia)

    private fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)

    private fun new(childOnly: PersonId? = null, day: LocalDate? = null) = EditorForm(
        EditorForm.Mode.New, today, LocalTime.of(10, 0), london, childOnly, day, order, calendarPerson = null, repeats = childOnly == null,
    )

    private val weekly = "RRULE:FREQ=WEEKLY;INTERVAL=1"

    private fun guitar(forPerson: String? = "mia", rule: String? = weekly) = Suggestion(
        "Guitar lesson", forPerson, false, LocalTime.of(10, 0), Duration.ofMinutes(45), null, rule, day(9, 5).takeIf { rule != null }, day(9, 12),
    )

    private fun halfTerm(days: Int, rule: String? = null) =
        Suggestion("Half term", "sam", true, null, null, days, rule, day(7, 11).takeIf { rule != null }, day(7, 11))

    @Test
    fun useFillsEverythingButTheDateAndTheEnd() {
        val form = new(day = day(9, 30))
        val s = guitar()
        form.use(s)
        assertThat(form.title).isEqualTo("Guitar lesson")
        assertThat(form.who).containsExactly(mia)
        assertThat(form.eventWhen.startDate).isNull()
        assertThat(form.eventWhen.allDay).isFalse()
        assertThat(form.eventWhen.startClock).isEqualTo(LocalTime.of(10, 0))
        assertThat(form.eventWhen.endClock).isEqualTo(LocalTime.of(10, 45))
        assertThat(form.eventWhen.endDays).isEqualTo(0)
        assertThat(form.repeat).isEqualTo(RepeatChoice.Weekly)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.Never)
        assertThat(form.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts, CopiedRow.Repeats)
        assertThat(form.usedSuggestion).isEqualTo(s)
        assertThat(form.edited).isTrue()
    }

    @Test
    fun anAllDaySuggestionFillsTheDaysAndNoTime() {
        val form = new()
        form.use(halfTerm(3))
        assertThat(form.eventWhen.allDay).isTrue()
        assertThat(form.eventWhen.endDays).isEqualTo(2)
        assertThat(form.eventWhen.startDate).isNull()
        assertThat(form.repeat).isEqualTo(RepeatChoice.Never)
        assertThat(form.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts)
    }

    @Test
    fun aRemovedPersonIsLeftOut() {
        val form = new()
        form.use(guitar(forPerson = "sam,ben"))
        assertThat(form.who).containsExactly(sam)
    }

    @Test
    fun nobodyLeftIsEveryone() {
        val form = new()
        form.use(guitar(forPerson = "ben,zed"))
        assertThat(form.who).containsExactly(PersonId.FAMILY)
        assertThat(form.everyone).isTrue()
        assertThat(form.copied).contains(CopiedRow.Who)
    }

    @Test
    fun aChildKeepsThemselvesAndLosesTheRepeat() {
        val form = new(childOnly = mia)
        form.use(guitar(forPerson = "alex,sam"))
        assertThat(form.who).containsExactly(mia)
        assertThat(form.repeat).isEqualTo(RepeatChoice.Never)
        assertThat(form.copied).containsExactly(CopiedRow.Starts)
        assertThat(bannerLine(guitar(), form.copied, today)).contains(": time.")
    }

    @Test
    fun aRepeatThatDoesNotFitTheSpanIsDropped() {
        val form = new()
        form.use(halfTerm(9, rule = weekly))
        assertThat(form.repeat).isEqualTo(RepeatChoice.Never)
        assertThat(form.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts)
        assertThat(bannerLine(halfTerm(9, rule = weekly), form.copied, today)).doesNotContain("repeats")
        assertThat(form.repeatTooLong).isFalse()
    }

    @Test
    fun pickingTheDateAfterUseThisKeepsTheCopiedTime() {
        val form = new()
        form.use(guitar())
        form.pickStartDate(day(9, 26))
        assertThat(form.eventWhen.startDate).isEqualTo(day(9, 26))
        assertThat(form.eventWhen.startClock).isEqualTo(LocalTime.of(10, 0))
        assertThat(form.eventWhen.endClock).isEqualTo(LocalTime.of(10, 45))
        form.pickStartDate(day(10, 3))
        assertThat(form.eventWhen.startClock).isEqualTo(LocalTime.of(10, 0))
        assertThat(form.eventWhen.endClock).isEqualTo(LocalTime.of(10, 45))
        assertThat(form.copied).contains(CopiedRow.Starts)
    }

    @Test
    fun aCopiedRepeatReadsAsItsWordsBeforeThereIsADate() {
        val form = new()
        form.use(guitar())
        assertThat(form.repeatsValue).isEqualTo("Every Saturday")
        form.pickStartDate(day(9, 27))
        assertThat(form.repeatsValue).isEqualTo("Every Sunday")
    }

    @Test
    fun aDatePickOffTheCopiedWeekdayClearsTheCopiedRepeats() {
        val form = new()
        form.use(guitar())
        form.pickStartDate(day(9, 26))
        assertThat(form.copied).contains(CopiedRow.Repeats)
        form.pickStartDate(day(9, 27))
        assertThat(form.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts)
        assertThat(form.repeat).isEqualTo(RepeatChoice.Weekly)
    }

    @Test
    fun aLastWeekdayRuleIsSavedAsLast() {
        val lastSaturday = guitar(rule = "RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1SA").copy(seriesStart = day(7, 25))
        val form = new()
        form.use(lastSaturday)
        assertThat(form.repeatsValue).isEqualTo("Every month, on the last Saturday")
        form.pickStartDate(day(9, 26))
        assertThat(form.repeatsValue).isEqualTo("Every month, on the last Saturday")
        assertThat(form.draft(null).recurrence!!.rule).contains("BYDAY=-1SA")
        assertThat(form.copied).contains(CopiedRow.Repeats)
        // A Saturday that is not the last of its month is not what was copied.
        form.pickStartDate(day(9, 19))
        assertThat(form.copied).doesNotContain(CopiedRow.Repeats)
        assertThat(form.draft(null).recurrence!!.rule).contains("BYDAY=3SA")
    }

    @Test
    fun aCopiedTagGoesWhenItsRowChanges() {
        val whoChanged = new().apply { use(guitar()); toggleWho(sam) }
        assertThat(whoChanged.copied).containsExactly(CopiedRow.Starts, CopiedRow.Repeats)

        val timeChanged = new().apply { use(guitar()); stepStart(ClockField.Hour, up = true) }
        assertThat(timeChanged.copied).containsExactly(CopiedRow.Who, CopiedRow.Repeats)

        val allDayChanged = new().apply { use(guitar()); chooseAllDay(true) }
        assertThat(allDayChanged.copied).containsExactly(CopiedRow.Who, CopiedRow.Repeats)

        val repeatChanged = new().apply { use(guitar()); chooseRepeat(RepeatChoice.Daily) }
        assertThat(repeatChanged.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts)

        val datePicked = new().apply { use(guitar()); pickStartDate(day(9, 26)) }
        assertThat(datePicked.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts, CopiedRow.Repeats)

        val endStepped = new().apply { use(guitar()); stepEnd(ClockField.Minute, up = true) }
        assertThat(endStepped.copied).containsExactly(CopiedRow.Who, CopiedRow.Repeats)

        val endDatePicked = new().apply { use(guitar()); pickStartDate(day(9, 26)); pickEndDate(day(9, 27)) }
        assertThat(endDatePicked.copied).containsExactly(CopiedRow.Who, CopiedRow.Repeats)

        val freeStart = new().apply { use(guitar()); useFreeStart(LocalTime.of(11, 0)) }
        assertThat(freeStart.copied).containsExactly(CopiedRow.Who, CopiedRow.Repeats)
    }

    @Test
    fun typingAgainKeepsWhatWasFilled() {
        val form = new()
        form.use(guitar())
        form.updateTitle("Guitar lessons")
        assertThat(form.who).containsExactly(mia)
        assertThat(form.eventWhen.endClock).isEqualTo(LocalTime.of(10, 45))
        assertThat(form.repeat).isEqualTo(RepeatChoice.Weekly)
        assertThat(form.copied).containsExactly(CopiedRow.Who, CopiedRow.Starts, CopiedRow.Repeats)
    }

    @Test
    fun useDoesNothingWhileSaving() {
        val form = new()
        form.saving = true
        form.use(guitar())
        assertThat(form.title).isEmpty()
        assertThat(form.who).isEmpty()
        assertThat(form.copied).isEmpty()
        assertThat(form.usedSuggestion).isNull()
    }
}
