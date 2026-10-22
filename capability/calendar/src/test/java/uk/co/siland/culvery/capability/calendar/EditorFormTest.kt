package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.PersonId

/** Wednesday 23 September 2026, 10:00 in London; ranges are on Monday 19 October. Robolectric backs `mutableStateOf`. */
@RunWith(AndroidJUnit4::class)
class EditorFormTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = PersonId("alex")
    private val sam = PersonId("sam")
    private val mia = PersonId("mia")
    private val order = listOf(alex, sam, mia)
    private val mon19 = LocalDate.of(2026, 10, 19)

    private fun day(d: Int) = mon19.plusDays(d - 19L)

    private fun new(
        now: String = "10:00",
        day: LocalDate? = null,
        childOnly: PersonId? = null,
        repeats: Boolean = true,
    ) = EditorForm(EditorForm.Mode.New, today, LocalTime.parse(now), london, childOnly, day, order, calendarPerson = null, repeats = repeats)

    /** A new event with a title and Mia, so only the day and times are left to choose. */
    private fun ready(day: LocalDate? = null) = new(day = day).apply {
        updateTitle("Holiday")
        toggleWho(mia)
    }

    private fun edit(
        start: EventTime,
        end: EventTime,
        forPerson: String? = "alex",
        title: String = "Dinner with Jo & Priya",
        calendarPerson: PersonId? = null,
    ) = EditorForm(
        EditorForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
        today, LocalTime.of(10, 0), london, null, null, order, calendarPerson,
    )

    private fun editTimed(start: LocalDateTime, minutes: Long, forPerson: String? = "alex", calendarPerson: PersonId? = null): EditorForm {
        val from = start.atZone(london).toInstant()
        return edit(EventTime.Timed(from), EventTime.Timed(from.plusSeconds(minutes * 60)), forPerson, calendarPerson = calendarPerson)
    }

    private fun timedSpan(from: LocalDateTime, to: LocalDateTime) =
        edit(EventTime.Timed(from.atZone(london).toInstant()), EventTime.Timed(to.atZone(london).toInstant()), "family", "Half term")

    /** All day Monday 19 to Friday 23 October (the end is exclusive). */
    private fun halfTerm() = edit(EventTime.AllDay(day(19)), EventTime.AllDay(day(24)), "family", "Half term")

    private fun EditorForm.span() = draft(createdBy = null).let { it.start to it.end }

    private fun allDay(first: LocalDate, endExclusive: LocalDate) = EventTime.AllDay(first) to EventTime.AllDay(endExclusive)

    /** A save in flight closes the editor on what it sent, so nothing changes until it ends. */
    @Test
    fun whileSavingEveryChangeIsIgnored() {
        val form = ready(day(21)).apply {
            stepStart(ClockField.Hour, up = true)
            chooseRepeat(RepeatChoice.Weekly)
            stopsAfter()
        }
        val before = form.draft(createdBy = null)
        val repeatEnd = form.repeatEnd
        form.saving = true
        form.updateTitle("Other")
        form.toggleWho(sam)
        form.pickStartDate(day(22))
        form.pickEndDate(day(23))
        form.chooseAllDay(true)
        form.stepStart(ClockField.Hour, up = true)
        form.stepEnd(ClockField.Minute, up = true)
        form.useFreeStart(LocalTime.of(7, 0))
        form.chooseRepeat(RepeatChoice.Daily)
        form.moreTimes()
        form.keepsGoing()
        assertThat(form.draft(createdBy = null)).isEqualTo(before)
        assertThat(form.repeatEnd).isEqualTo(repeatEnd)
        form.saving = false
        form.pickStartDate(day(22))
        assertThat(form.eventWhen.startDate).isEqualTo(day(22))
    }

    // Who

    @Test
    fun anAdultsNewEventStartsWithNobody() {
        val form = new()
        assertThat(form.who).isEmpty()
        assertThat(form.missing).contains(Needed.Who)
    }

    @Test
    fun aChildsNewEventStartsAsThemselves() {
        val form = new(childOnly = mia)
        assertThat(form.who).containsExactly(mia)
        assertThat(form.missing).doesNotContain(Needed.Who)
    }

    @Test
    fun toggleWhoKeepsTheHouseholdOrder() {
        val form = new()
        form.toggleWho(mia)
        form.toggleWho(alex)
        assertThat(form.who).containsExactly(alex, mia).inOrder()
    }

    @Test
    fun everyoneClearsThePeopleAndAPersonClearsEveryone() {
        val form = new()
        form.toggleWho(sam)
        form.toggleWho(mia)
        form.toggleWho(PersonId.FAMILY)
        assertThat(form.who).containsExactly(PersonId.FAMILY)
        assertThat(form.everyone).isTrue()
        form.toggleWho(alex)
        assertThat(form.who).containsExactly(alex)
        assertThat(form.everyone).isFalse()
    }

    @Test
    fun theLastPersonCanBeClearedInTheForm() {
        val form = new()
        form.toggleWho(sam)
        form.toggleWho(sam)
        assertThat(form.who).isEmpty()
        assertThat(form.missing).contains(Needed.Who)
        // The detail sheet's chips keep the last person.
        assertThat(toggled(listOf(sam), sam)).containsExactly(sam)
    }

    @Test
    fun anEditOpensOnItsSetWithRemovedPeopleDropped() {
        assertThat(editTimed(today.atTime(18, 0), 60, forPerson = "sam,gone").who).containsExactly(sam)
    }

    @Test
    fun anUntaggedEditOpensAsItsCalendarsPerson() {
        val mapped = editTimed(today.atTime(18, 0), 60, forPerson = null, calendarPerson = alex)
        assertThat(mapped.who).containsExactly(alex)
        assertThat(mapped.draft(createdBy = null).forPerson).isNull()
        val unmapped = editTimed(today.atTime(18, 0), 60, forPerson = null)
        assertThat(unmapped.who).containsExactly(PersonId.FAMILY)
        assertThat(unmapped.everyone).isTrue()
    }

    @Test
    fun anUntouchedWhoKeepsTheTagExactly() {
        val untagged = editTimed(today.atTime(18, 0), 60, forPerson = null, calendarPerson = alex)
        untagged.updateTitle("Dinner at Gran’s")
        assertThat(untagged.draft(createdBy = null).forPerson).isNull()
        assertThat(untagged.touched).containsExactly(EventField.TITLE)
        val departed = editTimed(today.atTime(18, 0), 60, forPerson = "sam,gone")
        departed.updateTitle("Dinner at Gran’s")
        assertThat(departed.draft(createdBy = null).forPerson).isEqualTo("sam,gone")
    }

    @Test
    fun theDraftCarriesTheSetsTag() {
        val form = new(day = today)
        form.updateTitle("Cinema")
        form.toggleWho(mia)
        form.toggleWho(sam)
        assertThat(form.draft(createdBy = "alex").forPerson).isEqualTo("sam,mia")
    }

    // Starts and Ends

    @Test
    fun theWeeksAddEventStartsWithNoDay() {
        val form = new(day = null)
        assertThat(form.eventWhen.startDate).isNull()
        assertThat(form.missing).contains(Needed.Day)
    }

    @Test
    fun anEmptyColumnStartsOnItsDay() {
        val form = new(day = today.plusDays(2))
        assertThat(form.eventWhen.startDate).isEqualTo(today.plusDays(2))
        assertThat(form.missing).doesNotContain(Needed.Day)
    }

    @Test
    fun anAllDayStartsPickIsOneDay() {
        val form = ready()
        form.chooseAllDay(true)
        form.pickStartDate(today.plusDays(3))
        assertThat(form.span()).isEqualTo(allDay(today.plusDays(3), today.plusDays(4)))
    }

    @Test
    fun anAllDaySpanEndsTheDayAfterItsLastDay() {
        val form = ready()
        form.chooseAllDay(true)
        form.pickStartDate(LocalDate.of(2026, 10, 29))
        form.pickEndDate(LocalDate.of(2026, 11, 1))
        assertThat(form.span()).isEqualTo(allDay(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 2)))
        form.pickStartDate(LocalDate.of(2026, 12, 30))
        assertThat(form.span()).isEqualTo(allDay(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 3)))
    }

    /** The Ends grid offers the start's day and the 59 after it; another day changes nothing. */
    @Test
    fun anEndsPickPastTheCapChangesNothing() {
        val form = ready(day = mon19)
        form.chooseAllDay(true)
        form.pickEndDate(mon19.plusDays(59))
        assertThat(form.span()).isEqualTo(allDay(mon19, LocalDate.of(2026, 12, 18)))
        form.pickEndDate(mon19.plusDays(60))
        form.pickEndDate(mon19.minusDays(1))
        assertThat(form.eventWhen.endDays).isEqualTo(59)
        assertThat(form.reason).isNull()
    }

    // Missing and reason

    @Test
    fun needsLineNamesWhatIsMissing() {
        val lines = mapOf(
            listOf(Needed.Title) to "Needs a title",
            listOf(Needed.Who) to "Needs who",
            listOf(Needed.Day) to "Needs a day",
            listOf(Needed.Title, Needed.Who) to "Needs a title and who",
            listOf(Needed.Title, Needed.Day) to "Needs a title and a day",
            listOf(Needed.Who, Needed.Day) to "Needs who and a day",
            listOf(Needed.Title, Needed.Who, Needed.Day) to "Needs a title, who and a day",
        )
        lines.forEach { (missing, line) -> assertThat(needsLine(missing)).isEqualTo(line) }
        assertThat(needsLine(emptyList())).isNull()
        assertThat(new().reason).isEqualTo("Needs a title, who and a day")
    }

    @Test
    fun theReasonIsNeedsFirst() {
        val form = timedSpan(mon19.atStartOfDay(), mon19.plusDays(59).atTime(23, 0))
        form.updateTitle("")
        form.stepStart(ClockField.Minute, up = false)
        assertThat(form.eventWhen.capError).isEqualTo(UNTIL_TOO_LATE)
        assertThat(form.reason).isEqualTo("Needs a title")
    }

    // Edited and touched

    @Test
    fun choosingOnlyWhoOrADayIsEdited() {
        val answers: List<EditorForm.() -> Unit> = listOf(
            { toggleWho(sam) },
            { pickStartDate(today.plusDays(1)) },
            { chooseAllDay(true) },
            { chooseRepeat(RepeatChoice.Weekly) },
        )
        answers.forEach { answer ->
            val form = new()
            assertThat(form.edited).isFalse()
            form.answer()
            assertThat(form.edited).isTrue()
        }
        val steps: List<EditorForm.() -> Unit> = listOf(
            { stepStart(ClockField.Hour, up = true) },
            { stepEnd(ClockField.Minute, up = true) },
            { pickEndDate(mon19.plusDays(1)) },
            { useFreeStart(LocalTime.of(14, 45)) },
        )
        steps.forEach { step ->
            val form = new(day = mon19)
            assertThat(form.edited).isFalse()
            form.step()
            assertThat(form.edited).isTrue()
        }
        // The last day on its own: an edit's span given a new last day.
        val span = halfTerm()
        span.pickEndDate(day(21))
        assertThat(span.edited).isTrue()
    }

    @Test
    fun aReorderedSetIsNotTouched() {
        val form = editTimed(today.atTime(18, 0), 60, forPerson = "mia,sam")
        assertThat(form.who).containsExactly(sam, mia).inOrder()
        form.toggleWho(sam)
        form.toggleWho(sam)
        assertThat(form.touched).doesNotContain(EventField.FOR_PERSON)
        assertThat(form.unchanged).isTrue()
        assertThat(form.draft(createdBy = null).forPerson).isEqualTo("mia,sam")
    }

    @Test
    fun everyoneAndAPersonNeverMix() {
        val person = ready(day = today)
        person.toggleWho(mia)
        person.toggleWho(PersonId.FAMILY)
        person.toggleWho(sam)
        assertThat(person.who).containsExactly(sam)
        assertThat(person.draft(createdBy = null).forPerson).isEqualTo("sam")

        val everyone = ready(day = today)
        everyone.toggleWho(sam)
        everyone.toggleWho(PersonId.FAMILY)
        assertThat(everyone.who).containsExactly(PersonId.FAMILY)
        assertThat(everyone.draft(createdBy = null).forPerson).isEqualTo("family")
    }

    // Ported from EventFormTest

    @Test
    fun aTitleIsAtMostAHundredCharacters() {
        val form = new()
        form.updateTitle("a".repeat(MAX_TITLE_LENGTH))
        form.updateTitle("a".repeat(MAX_TITLE_LENGTH + 1))
        assertThat(form.title).hasLength(MAX_TITLE_LENGTH)
        val long = edit(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1)), title = "b".repeat(120))
        long.updateTitle("b".repeat(119))
        assertThat(long.title).hasLength(119)
        long.updateTitle("b".repeat(121))
        assertThat(long.title).hasLength(119)
    }

    @Test
    fun aMixedAllDayAndTimedEventStaysLocked() {
        val mixed = edit(EventTime.AllDay(mon19), EventTime.Timed(day(21).atTime(9, 0).atZone(london).toInstant()))
        val own = mixed.span()
        assertThat(mixed.datesLocked).isTrue()
        assertThat(mixed.lockedDatesLabel("Google Calendar")).isEqualTo("Mon 19 – Wed 21 · change dates in Google Calendar on your phone")
        mixed.chooseAllDay(false)
        mixed.pickStartDate(day(20))
        assertThat(mixed.span()).isEqualTo(own)
        assertThat(edit(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1))).datesLocked).isFalse()
    }

    @Test
    fun anAllDaySpanOpensWithItsFirstAndLastDays() {
        val form = halfTerm()
        assertThat(form.datesLocked).isFalse()
        assertThat(form.eventWhen.allDay).isTrue()
        assertThat(form.eventWhen.startDate).isEqualTo(day(19))
        assertThat(form.eventWhen.endDate).isEqualTo(day(23))
    }

    @Test
    fun anUntouchedAllDaySpanIsUnchanged() {
        val form = halfTerm()
        assertThat(form.unchanged).isTrue()
        assertThat(form.edited).isFalse()
        assertThat(form.span()).isEqualTo(allDay(day(19), day(24)))
        form.updateTitle("Half term at Gran’s")
        assertThat(form.touched).containsExactly(EventField.TITLE)
    }

    @Test
    fun movingTheLastDayOnAnEditTouchesOnlyTheTimes() {
        val form = halfTerm()
        form.pickEndDate(day(21))
        assertThat(form.touched).containsExactly(EventField.TIMES)
        assertThat(form.span()).isEqualTo(allDay(day(19), day(22)))
    }

    @Test
    fun aChildsFormNeverRepeats() {
        val form = new(day = today, childOnly = mia, repeats = false)
        form.updateTitle("Swimming")
        assertThat(form.canRepeat).isFalse()
        form.chooseRepeat(RepeatChoice.Weekly)
        assertThat(form.repeat).isEqualTo(RepeatChoice.Never)
        assertThat(form.draft(createdBy = null).recurrence).isNull()
        assertThat(form.draft(createdBy = null).forPerson).isEqualTo("mia")
        assertThat(editTimed(today.atTime(19, 30), 90).canRepeat).isFalse()
    }

    @Test
    fun theCountStaysBetweenTwoAndFiftyTwo() {
        val form = ready(day = today)
        form.stopsAfter()
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.Never)
        form.chooseRepeat(RepeatChoice.Weekly)
        form.stopsAfter()
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(DEFAULT_TIMES))
        repeat(DEFAULT_TIMES) { form.fewerTimes() }
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(MIN_TIMES))
        repeat(MAX_TIMES) { form.moreTimes() }
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(MAX_TIMES))
        form.stopsAfter()
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(MAX_TIMES))
        assertThat(form.draft(createdBy = null).recurrence)
            .isEqualTo(Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=$MAX_TIMES", london))
        form.keepsGoing()
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.Never)
        form.stopsAfter()
        form.chooseRepeat(RepeatChoice.Never)
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.Never)
    }

    @Test
    fun theStepperSaysTheCountAndTheLastDate() {
        val form = ready(day = LocalDate.of(2026, 9, 26))
        form.chooseRepeat(RepeatChoice.Weekly)
        assertThat(form.stepperLabel).isNull()
        form.stopsAfter()
        form.moreTimes()
        form.moreTimes()
        assertThat(form.stepperLabel).isEqualTo("12 times · last on Sat 12 Dec")
        form.keepsGoing()
        assertThat(form.stepperLabel).isNull()
    }

    @Test
    fun changingTheDayRewordsTheRepeat() {
        val form = ready(day = LocalDate.of(2026, 9, 26))
        form.chooseRepeat(RepeatChoice.MonthlyOnWeekday)
        assertThat(form.repeatsValue).isEqualTo("Every month, on the 4th Saturday")
        form.pickStartDate(LocalDate.of(2026, 10, 29))
        assertThat(form.repeatsValue).isEqualTo("Every month, on the last Thursday")
        assertThat(form.draft(createdBy = null).recurrence?.rule).isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH")
    }

    @Test
    fun theRepeatsRowValue() {
        val form = ready(day = LocalDate.of(2026, 9, 26))
        assertThat(form.repeatsValue).isEqualTo("Doesn’t repeat")
        form.chooseRepeat(RepeatChoice.Weekly)
        assertThat(form.repeatsValue).isEqualTo("Every Saturday")
        form.stopsAfter()
        form.moreTimes()
        form.moreTimes()
        assertThat(form.repeatsValue).isEqualTo("Every Saturday · 12 times")
    }

    @Test
    fun typingThenClearingIsNotEdited() {
        val form = new(day = mon19)
        form.updateTitle("Swim")
        form.updateTitle("")
        form.stepEnd(ClockField.Hour, up = true)
        form.stepEnd(ClockField.Hour, up = false)
        assertThat(form.edited).isFalse()
    }

    @Test
    fun anUnchangedEditIsUnchanged() {
        val form = editTimed(today.atTime(19, 30), 90)
        assertThat(form.unchanged).isTrue()
        form.updateTitle("  Dinner with Jo & Priya  ")
        form.toggleWho(alex)
        form.toggleWho(alex)
        assertThat(form.unchanged).isTrue()
        form.stepEnd(ClockField.Hour, up = false)
        assertThat(form.unchanged).isFalse()
        assertThat(new().unchanged).isFalse()
    }

    // When (9c §3)

    @Test
    fun aNewEventStartsTimedWithTheDefaults() {
        val form = new(now = "10:20")
        assertThat(form.eventWhen).isEqualTo(EventWhen(false, null, LocalTime.of(11, 0), 0, LocalTime.of(12, 0)))
        assertThat(form.timesTouched).isFalse()
        assertThat(form.edited).isFalse()
        val onAnotherDay = new(day = mon19)
        assertThat(onAnotherDay.eventWhen).isEqualTo(EventWhen(false, mon19, LocalTime.of(9, 0), 0, LocalTime.of(10, 0)))
        assertThat(onAnotherDay.edited).isFalse()
    }

    @Test
    fun pickingAStartsDateIsAnEditButTheDefaultsAreNot() {
        val form = new()
        form.pickStartDate(mon19)
        assertThat(form.eventWhen).isEqualTo(EventWhen(false, mon19, LocalTime.of(9, 0), 0, LocalTime.of(10, 0)))
        assertThat(form.edited).isTrue()
        // Times stepped are kept by a later pick; untouched, the day's own defaults come back.
        form.stepStart(ClockField.Hour, up = true)
        assertThat(form.timesTouched).isTrue()
        form.pickStartDate(today)
        assertThat(form.eventWhen).isEqualTo(EventWhen(false, today, LocalTime.of(10, 0), 0, LocalTime.of(11, 0)))
    }

    @Test
    fun aTimedSpanMadeElsewhereOpensUnlocked() {
        val trip = timedSpan(mon19.atTime(22, 0), day(21).atTime(9, 0))
        assertThat(trip.datesLocked).isFalse()
        assertThat(trip.lockedDatesLabel("Google Calendar")).isNull()
        assertThat(trip.eventWhen).isEqualTo(EventWhen(false, mon19, LocalTime.of(22, 0), 2, LocalTime.of(9, 0)))
        trip.stepEnd(ClockField.Hour, up = true)
        assertThat(trip.span()).isEqualTo(
            EventTime.Timed(mon19.atTime(22, 0).atZone(london).toInstant()) to EventTime.Timed(day(21).atTime(10, 0).atZone(london).toInstant()),
        )
        assertThat(trip.touched).containsExactly(EventField.TIMES)
    }

    /** Fri 23 Oct 18:00 to Sun 25 Oct 16:00 over the autumn clock change, made on a phone: only the title is sent. */
    @Test
    fun aTimedSpanMadeElsewhereSavesWithoutTouchingItsTimes() {
        val start = EventTime.Timed(LocalDateTime.of(2026, 10, 23, 18, 0).atZone(london).toInstant())
        val end = EventTime.Timed(LocalDateTime.of(2026, 10, 25, 16, 0).atZone(london).toInstant())
        val camp = edit(start, end, "family", "Camp")
        camp.updateTitle("Scout camp")
        assertThat(camp.touched).containsExactly(EventField.TITLE)
        assertThat(camp.span()).isEqualTo(start to end)
        // An end at its start, read as an hour after, is written back as it was while the When is unchanged.
        val bad = edit(start, start, "family", "Camp")
        assertThat(bad.eventWhen.endClock).isEqualTo(LocalTime.of(19, 0))
        bad.updateTitle("Scout camp")
        assertThat(bad.span()).isEqualTo(start to start)
        assertThat(bad.touched).containsExactly(EventField.TITLE)
    }

    @Test
    fun aTimedSpanOverSixtyDaysSavesATitleChange() {
        val long = timedSpan(mon19.atTime(9, 0), mon19.plusDays(70).atTime(9, 0))
        val own = long.span()
        assertThat(long.datesLocked).isTrue()
        long.updateTitle("Gran staying")
        long.pickStartDate(day(20))
        long.pickEndDate(day(22))
        long.stepStart(ClockField.Hour, up = true)
        long.stepEnd(ClockField.Hour, up = false)
        long.chooseAllDay(true)
        assertThat(long.reason).isNull()
        assertThat(long.span()).isEqualTo(own)
        assertThat(long.touched).containsExactly(EventField.TITLE)
    }

    @Test
    fun anAllDaySpanOverSixtyDaysIsLocked() {
        val long = edit(EventTime.AllDay(mon19), EventTime.AllDay(mon19.plusDays(70)), "family", "Gran staying")
        assertThat(long.datesLocked).isTrue()
        assertThat(long.lockedDatesLabel("Google Calendar")).isEqualTo("Mon 19 Oct – Sun 27 Dec · change dates in Google Calendar on your phone")
        long.chooseAllDay(false)
        assertThat(long.eventWhen.allDay).isTrue()
        assertThat(long.edited).isFalse()
        // Sixty days exactly is within the cap.
        assertThat(edit(EventTime.AllDay(mon19), EventTime.AllDay(mon19.plusDays(60))).datesLocked).isFalse()
    }

    @Test
    fun togglingAllDayBackIsNotAnEdit() {
        val form = halfTerm()
        form.chooseAllDay(false)
        assertThat(form.eventWhen.allDay).isFalse()
        assertThat(form.edited).isTrue()
        form.chooseAllDay(true)
        assertThat(form.edited).isFalse()
        assertThat(form.unchanged).isTrue()
        // Clocks stepped while timed are hidden again under All day, so they aren't an edit either.
        form.chooseAllDay(false)
        form.stepStart(ClockField.Hour, up = true)
        form.chooseAllDay(true)
        assertThat(form.edited).isFalse()
        assertThat(form.span()).isEqualTo(allDay(day(19), day(24)))
    }

    @Test
    fun theDraftOfATimedSpanHasItsOwnEnd() {
        val form = ready()
        form.pickStartDate(day(23))
        form.stepStart(ClockField.Hour, up = true)
        form.pickEndDate(day(25))
        form.stepEnd(ClockField.Minute, up = false)
        assertThat(form.span()).isEqualTo(
            EventTime.Timed(day(23).atTime(10, 0).atZone(london).toInstant()) to EventTime.Timed(day(25).atTime(10, 45).atZone(london).toInstant()),
        )
        form.chooseAllDay(true)
        assertThat(form.span()).isEqualTo(allDay(day(23), day(26)))
    }

    @Test
    fun onlyTheStartsDateIsMissing() {
        val form = ready()
        assertThat(form.missing).containsExactly(Needed.Day)
        assertThat(form.reason).isEqualTo("Needs a day")
        form.pickStartDate(mon19)
        assertThat(form.missing).isEmpty()
        form.chooseAllDay(true)
        assertThat(form.missing).isEmpty()
        assertThat(form.canSave).isTrue()
    }

    /** Only a Starts step can carry an opened span past the cap: Save then says why. */
    @Test
    fun aSpanPastTheCapCannotSave() {
        val form = timedSpan(mon19.atStartOfDay(), mon19.plusDays(59).atTime(23, 0))
        assertThat(form.datesLocked).isFalse()
        assertThat(form.reason).isNull()
        form.stepStart(ClockField.Minute, up = false)
        assertThat(form.eventWhen.endDays).isEqualTo(60)
        assertThat(form.reason).isEqualTo(UNTIL_TOO_LATE)
        assertThat(UNTIL_TOO_LATE).isEqualTo("An event can last up to 60 days. Pick an earlier end.")
        assertThat(form.canSave).isFalse()
    }
}
