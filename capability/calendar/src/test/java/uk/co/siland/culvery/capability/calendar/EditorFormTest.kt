package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
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
    ) = EditorForm(EventForm.Mode.New, today, LocalTime.parse(now), london, childOnly ?: alex, childOnly, day, order, calendarPerson = null, repeats = repeats)

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
        EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
        today, LocalTime.of(10, 0), london, alex, null, null, order, calendarPerson,
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

    // Day

    @Test
    fun theWeeksAddEventStartsWithNoDay() {
        val form = new(day = null)
        assertThat(form.day).isNull()
        assertThat(form.missing).contains(Needed.Day)
    }

    @Test
    fun anEmptyColumnStartsOnItsDay() {
        val form = new(day = today.plusDays(2))
        assertThat(form.day).isEqualTo(today.plusDays(2))
        assertThat(form.missing).doesNotContain(Needed.Day)
    }

    @Test
    fun aOneDayTapSetsTheDay() {
        val form = ready()
        form.tapDay(today.plusDays(3))
        assertThat(form.day).isEqualTo(today.plusDays(3))
        assertThat(form.until).isNull()
        assertThat(form.awaitingLastDay).isFalse()
        assertThat(form.span()).isEqualTo(allDay(today.plusDays(3), today.plusDays(4)))
    }

    // Range

    @Test
    fun turningOnMultiDayForcesAllDay() {
        val form = ready(day = mon19)
        form.chooseTime(LocalTime.of(18, 0))
        form.setMultiDay(true)
        assertThat(form.multiDay).isTrue()
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        // A time can't be chosen while it lasts more than a day.
        form.chooseHour(9)
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.setMultiDay(false)
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(18, 0)))

        val moved = ready(day = mon19)
        moved.chooseTime(LocalTime.of(18, 0))
        moved.setMultiDay(true)
        moved.tapDay(day(20))
        moved.setMultiDay(false)
        assertThat(moved.time).isEqualTo(TimeChoice.AllDay)
    }

    @Test
    fun theFirstTapWaitsForTheLast() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        assertThat(form.day).isEqualTo(mon19)
        assertThat(form.until).isNull()
        assertThat(form.awaitingLastDay).isTrue()
    }

    @Test
    fun theSecondTapSetsTheLastDay() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(day(22))
        assertThat(form.until).isEqualTo(day(22))
        assertThat(form.awaitingLastDay).isFalse()
        assertThat(form.span()).isEqualTo(allDay(day(19), day(23)))
    }

    @Test
    fun aSecondTapOnTheSameDayIsOneDay() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(mon19)
        assertThat(form.day).isEqualTo(mon19)
        assertThat(form.until).isNull()
        assertThat(form.awaitingLastDay).isFalse()
        assertThat(form.span()).isEqualTo(allDay(day(19), day(20)))
    }

    @Test
    fun aTapBeforeTheFirstStartsAgain() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(day(16))
        assertThat(form.day).isEqualTo(day(16))
        assertThat(form.until).isNull()
        assertThat(form.awaitingLastDay).isTrue()
    }

    @Test
    fun aTapAfterARangeStartsANewOne() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(day(22))
        form.tapDay(day(26))
        assertThat(form.day).isEqualTo(day(26))
        assertThat(form.until).isNull()
        assertThat(form.awaitingLastDay).isTrue()
    }

    @Test
    fun aRangeAcrossAMonthEndsTheDayAfterItsLastDay() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(LocalDate.of(2026, 10, 29))
        form.tapDay(LocalDate.of(2026, 11, 1))
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        assertThat(form.span()).isEqualTo(allDay(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun theRangeKeepsTheSixtyDayCap() {
        val sixty = ready()
        sixty.setMultiDay(true)
        sixty.tapDay(mon19)
        sixty.tapDay(mon19.plusDays(59))
        assertThat(sixty.untilError).isNull()
        assertThat(sixty.canSave).isTrue()
        assertThat(sixty.span()).isEqualTo(allDay(mon19, LocalDate.of(2026, 12, 18)))

        val sixtyOne = ready()
        sixtyOne.setMultiDay(true)
        sixtyOne.tapDay(mon19)
        sixtyOne.tapDay(mon19.plusDays(60))
        assertThat(sixtyOne.untilError).isEqualTo(UNTIL_TOO_LATE)
        assertThat(sixtyOne.reason).isEqualTo(UNTIL_TOO_LATE)
        assertThat(sixtyOne.canSave).isFalse()
    }

    /** The cap's message asks for an earlier last day, so a tap on or after the first day becomes it. */
    @Test
    fun aTapAfterAnOverCapRangeReplacesTheLastDay() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(mon19.plusDays(60))
        assertThat(form.untilError).isEqualTo(UNTIL_TOO_LATE)
        form.tapDay(mon19.plusDays(10))
        assertThat(form.day).isEqualTo(mon19)
        assertThat(form.until).isEqualTo(mon19.plusDays(10))
        assertThat(form.untilError).isNull()
        assertThat(form.awaitingLastDay).isFalse()
        // A range within the cap: the next tap starts a new one, as before.
        form.tapDay(mon19.plusDays(20))
        assertThat(form.day).isEqualTo(mon19.plusDays(20))
        assertThat(form.awaitingLastDay).isTrue()
        form.tapDay(mon19.plusDays(90))
        assertThat(form.untilError).isEqualTo(UNTIL_TOO_LATE)
        form.tapDay(mon19)
        assertThat(form.day).isEqualTo(mon19)
        assertThat(form.until).isNull()
        assertThat(form.awaitingLastDay).isTrue()
    }

    @Test
    fun turningMultiDayOffDropsUntil() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(day(22))
        form.setMultiDay(false)
        assertThat(form.multiDay).isFalse()
        assertThat(form.until).isNull()
        assertThat(form.day).isEqualTo(mon19)
        assertThat(form.span()).isEqualTo(allDay(day(19), day(20)))
    }

    @Test
    fun aRangeAcrossTheYearEnd() {
        val form = ready()
        form.setMultiDay(true)
        form.tapDay(LocalDate.of(2026, 12, 30))
        form.tapDay(LocalDate.of(2027, 1, 2))
        assertThat(form.span()).isEqualTo(allDay(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 3)))
    }

    // Time

    @Test
    fun anHourSetsItOnTheHour() {
        val form = new()
        form.chooseHour(10)
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(10, 0)))
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun aMinuteKeepsTheHour() {
        val form = new()
        form.chooseHour(10)
        form.chooseMinute(45)
        assertThat(form.startTime).isEqualTo(LocalTime.of(10, 45))
        form.chooseHour(14)
        assertThat(form.startTime).isEqualTo(LocalTime.of(14, 45))
    }

    @Test
    fun aMinuteWhileAllDayIsIgnored() {
        val form = new()
        form.chooseMinute(45)
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        assertThat(form.edited).isFalse()
    }

    @Test
    fun thePickersStartWhereTheFormIs() {
        val form = new(now = "09:20")
        assertThat(form.mode).isEqualTo(EventForm.Mode.New)
        assertThat(form.pickerTime).isEqualTo(LocalTime.of(10, 0))
        form.chooseHour(14)
        form.chooseLength(Duration.ofMinutes(45))
        assertThat(form.pickerTime).isEqualTo(LocalTime.of(14, 0))
        assertThat(form.endPickerTime).isEqualTo(LocalTime.of(14, 45))
    }

    @Test
    fun theEndPickerStopsBeforeMidnight() {
        val form = new()
        form.chooseTime(LocalTime.of(23, 30))
        assertThat(form.endPickerTime).isEqualTo(LocalTime.of(23, 59))
    }

    @Test
    fun otherTimeSetsAnyTime() {
        val form = new()
        form.chooseTime(LocalTime.of(6, 30))
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(6, 30)))
    }

    @Test
    fun theLengthsAreTheFour() {
        assertThat(EDITOR_LENGTHS.map(::lengthChip)).containsExactly("30 min", "45 min", "1 hour", "2 hours").inOrder()
    }

    @Test
    fun anEndTimeSetsTheLength() {
        val form = new()
        form.chooseHour(10)
        assertThat(form.untilTimeLabel).isEqualTo("Until…")
        form.chooseEndTime(LocalTime.of(11, 20))
        assertThat(form.length).isEqualTo(Duration.ofMinutes(80))
        assertThat(form.untilTimeLabel).isEqualTo("Until 11:20")
    }

    @Test
    fun anEndAtOrBeforeTheStartIsRefused() {
        val form = ready(day = today)
        form.chooseHour(10)
        form.chooseEndTime(LocalTime.of(9, 30))
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
        assertThat(form.endError).isEqualTo(END_BEFORE_START)
        form.chooseEndTime(LocalTime.of(10, 0))
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
        assertThat(form.endError).isEqualTo(END_BEFORE_START)
        // The draft is as it was, so Save stays enabled.
        assertThat(form.reason).isNull()
        assertThat(form.canSave).isTrue()
        form.chooseLength(Duration.ofMinutes(30))
        assertThat(form.endError).isNull()
        assertThat(form.length).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun anEditsOwnLengthShowsAsUntil() {
        assertThat(editTimed(today.atTime(19, 30), 90).untilTimeLabel).isEqualTo("Until 21:00")
    }

    @Test
    fun aTimedEventStillEndsByMidnight() {
        val form = ready(day = today)
        form.chooseHour(23)
        form.chooseLength(Duration.ofHours(2))
        // 23:00 BST + 2 h would cross midnight: it stops at 00:00 BST (23:00Z).
        assertThat(form.span()).isEqualTo(
            EventTime.Timed(Instant.parse("2026-09-23T22:00:00Z")) to EventTime.Timed(Instant.parse("2026-09-23T23:00:00Z")),
        )
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
        // A range needs a day, so a day can't be missing with the cap broken: the title is.
        val form = new()
        form.toggleWho(mia)
        form.setMultiDay(true)
        form.tapDay(mon19)
        form.tapDay(mon19.plusDays(60))
        assertThat(form.untilError).isEqualTo(UNTIL_TOO_LATE)
        assertThat(form.reason).isEqualTo("Needs a title")
    }

    // Edited and touched

    @Test
    fun choosingOnlyWhoOrADayIsEdited() {
        val answers: List<EditorForm.() -> Unit> = listOf(
            { toggleWho(sam) },
            { tapDay(today.plusDays(1)) },
            { setMultiDay(true) },
            { chooseHour(10) },
            { chooseHour(10); chooseMinute(15) },
            { chooseLength(Duration.ofHours(2)) },
            { chooseRepeat(RepeatChoice.Weekly) },
        )
        answers.forEach { answer ->
            val form = new()
            assertThat(form.edited).isFalse()
            form.answer()
            assertThat(form.edited).isTrue()
        }
        // The last day on its own: an edit's range given a new last day.
        val span = halfTerm()
        span.tapDay(day(19))
        span.tapDay(day(21))
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
    fun aTimedSpanStaysLocked() {
        val trip = timedSpan(mon19.atTime(22, 0), day(21).atTime(9, 0))
        val own = trip.span()
        assertThat(trip.datesLocked).isTrue()
        assertThat(trip.multiDay).isFalse()
        assertThat(trip.lockedDatesLabel("Google Calendar")).isEqualTo("Mon 19 – Wed 21 · change dates in Google Calendar on your phone")
        trip.tapDay(day(20))
        trip.setMultiDay(true)
        trip.chooseHour(8)
        assertThat(trip.span()).isEqualTo(own)
        assertThat(edit(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1))).datesLocked).isFalse()
        // 22:00 to 01:00 crosses midnight; 22:00 to 00:00 ends at midnight, which counts as the same day.
        assertThat(editTimed(today.atTime(22, 0), 180).datesLocked).isTrue()
        assertThat(editTimed(today.atTime(22, 0), 120).datesLocked).isFalse()
        assertThat(editTimed(today.atTime(22, 0), 120).lockedDatesLabel("Google Calendar")).isNull()
    }

    @Test
    fun aMixedAllDayAndTimedEventStaysLocked() {
        val mixed = edit(EventTime.AllDay(mon19), EventTime.Timed(day(21).atTime(9, 0).atZone(london).toInstant()))
        assertThat(mixed.datesLocked).isTrue()
        assertThat(mixed.multiDay).isFalse()
    }

    @Test
    fun anAllDaySpanOpensWithItsFirstAndLastDays() {
        val form = halfTerm()
        assertThat(form.datesLocked).isFalse()
        assertThat(form.day).isEqualTo(day(19))
        assertThat(form.multiDay).isTrue()
        assertThat(form.until).isEqualTo(day(23))
        assertThat(form.awaitingLastDay).isFalse()
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
        form.tapDay(day(19))
        form.tapDay(day(21))
        assertThat(form.touched).containsExactly(EventField.TIMES)
        assertThat(form.span()).isEqualTo(allDay(day(19), day(22)))
    }

    @Test
    fun anEditWhoseTimeWasChosenThenAllDayAgainIsUnchanged() {
        val form = halfTerm()
        form.setMultiDay(false)
        form.chooseHour(18)
        assertThat(form.unchanged).isFalse()
        form.chooseAllDay()
        form.setMultiDay(true)
        form.tapDay(day(19))
        form.tapDay(day(23))
        assertThat(form.until).isEqualTo(day(23))
        assertThat(form.unchanged).isTrue()
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
        form.tapDay(LocalDate.of(2026, 10, 29))
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
        val form = new()
        form.updateTitle("Swim")
        form.updateTitle("")
        form.chooseLength(Duration.ofHours(2))
        form.chooseLength(Duration.ofHours(1))
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
        form.chooseLength(Duration.ofHours(1))
        assertThat(form.unchanged).isFalse()
        assertThat(new().unchanged).isFalse()
    }
}
