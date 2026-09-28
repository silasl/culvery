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

/**
 * Wednesday 23 September 2026 in London. The form has no Android types (2b-2 design §3.1); Robolectric runs it only
 * because Compose's Android runtime backs `mutableStateOf` with a Parcelable.
 */
@RunWith(AndroidJUnit4::class)
class EventFormTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = PersonId("alex")
    private val mia = PersonId("mia")
    private val names = mapOf(PersonId.FAMILY to "Family", alex to "Alex", mia to "Mia")

    private fun new(now: String = "10:54", day: LocalDate? = null, signedIn: PersonId? = null, on: LocalDate = today) =
        EventForm(EventForm.Mode.New, on, LocalTime.parse(now), london, signedIn, day)

    private fun edit(start: EventTime, end: EventTime, title: String = "Dinner with Jo & Priya", forPerson: String? = "alex") =
        EventForm(
            EventForm.Mode.Edit(EditableEvent(EventRef("c1", "s1", "e1"), title, start, end, forPerson)),
            today, LocalTime.of(10, 54), london, signedIn = null, preselectedDay = null,
        )

    private fun editTimed(start: LocalDateTime, minutes: Long, forPerson: String? = "alex"): EventForm {
        val from = start.atZone(london).toInstant()
        return edit(EventTime.Timed(from), EventTime.Timed(from.plusSeconds(minutes * 60)), forPerson = forPerson)
    }

    private fun EventForm.summary() = summary { names.getValue(it) }

    private fun slot(s: TimeSlot) = TimeChoice.Slot(s)

    @Test
    fun theDefaultTimeIsTheNextSlotStillToComeToday() {
        val expected = mapOf(
            "08:59" to slot(TimeSlot.Morning),
            "09:00" to slot(TimeSlot.Afternoon),
            "13:59" to slot(TimeSlot.Afternoon),
            "14:00" to slot(TimeSlot.Evening),
            "17:59" to slot(TimeSlot.Evening),
            "18:00" to TimeChoice.AllDay,
            "23:30" to TimeChoice.AllDay,
        )
        expected.forEach { (now, time) -> assertThat(new(now).time).isEqualTo(time) }
    }

    @Test
    fun aLaterDayDefaultsToMorningAndAWeekColumnPresetsIt() {
        val form = new(now = "20:00", day = today.plusDays(2))
        assertThat(form.day).isEqualTo(today.plusDays(2))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Morning))
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun changingTheDayReappliesTheDefaultUntilTimeIsTouched() {
        val form = new(now = "20:00")
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.chooseDay(today.plusDays(1))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Morning))
        form.chooseDay(today)
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.chooseTime(slot(TimeSlot.Evening))
        form.chooseDay(today.plusDays(1))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Evening))
    }

    @Test
    fun whoStartsOnTheSignedInPersonOtherwiseFamily() {
        assertThat(new(signedIn = mia).who).isEqualTo(mia)
        assertThat(new().who).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun aPickedTimeThatIsASlotsTimeSelectsThatSlot() {
        val form = new()
        form.chooseTime(TimeChoice.Custom(LocalTime.of(14, 0)))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Afternoon))
        form.chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(16, 15)))
    }

    @Test
    fun thePickerOpensOnTheChosenTimeOrTheDefaultSlotsTime() {
        assertThat(new(now = "10:54").pickerTime).isEqualTo(LocalTime.of(14, 0))
        // All day is the default from 18:00; the picker then opens on Evening's time.
        assertThat(new(now = "20:00").pickerTime).isEqualTo(LocalTime.of(18, 0))
        val custom = new()
        custom.chooseTime(TimeChoice.Custom(LocalTime.of(16, 15)))
        assertThat(custom.pickerTime).isEqualTo(LocalTime.of(16, 15))
    }

    @Test
    fun anEditStartsFromTheEventWithItsOwnTimeAndLength() {
        val form = editTimed(today.atTime(19, 30), 90)
        assertThat(form.title).isEqualTo("Dinner with Jo & Priya")
        assertThat(form.who).isEqualTo(alex)
        assertThat(form.day).isEqualTo(today)
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(19, 30)))
        assertThat(form.length).isEqualTo(Duration.ofMinutes(90))
        assertThat(form.lengths.map(::lengthLabel)).containsExactly("30 min", "1 h", "2 h", "1 h 30").inOrder()
        assertThat(form.datesLocked).isFalse()
        assertThat(form.unchanged).isTrue()
    }

    @Test
    fun anEditStartingOnASlotSelectsItAndAddsNoLengthChip() {
        val form = editTimed(today.atTime(18, 0), 60)
        assertThat(form.time).isEqualTo(slot(TimeSlot.Evening))
        assertThat(form.lengths).isEqualTo(LENGTH_CHOICES)
    }

    @Test
    fun lengthLabels() {
        assertThat(listOf(30L, 45L, 60L, 65L, 90L, 180L).map { lengthLabel(Duration.ofMinutes(it)) })
            .containsExactly("30 min", "45 min", "1 h", "1 h 05", "1 h 30", "3 h").inOrder()
    }

    @Test
    fun aDateOutsideTheWeekShowsOnPickDate() {
        assertThat(editTimed(LocalDateTime.of(2026, 10, 5, 18, 0), 60).pickedDateLabel).isEqualTo("Mon 5 Oct")
        assertThat(editTimed(today.plusDays(6).atTime(18, 0), 60).pickedDateLabel).isNull()
        assertThat(new().dayChoices).containsExactlyElementsIn((0L..6L).map { today.plusDays(it) }).inOrder()
    }

    @Test
    fun anUntaggedEventStaysUntaggedUnlessWhoIsChanged() {
        val form = editTimed(today.atTime(13, 0), 30, forPerson = null)
        assertThat(form.who).isEqualTo(PersonId.FAMILY)
        assertThat(form.draft(createdBy = null).forPerson).isNull()
        assertThat(form.unchanged).isTrue()
        // Family is already chosen, so choosing it again changes nothing.
        form.chooseWho(PersonId.FAMILY)
        assertThat(form.draft(createdBy = null).forPerson).isNull()
        assertThat(form.unchanged).isTrue()
        form.chooseWho(mia)
        form.chooseWho(PersonId.FAMILY)
        assertThat(form.draft(createdBy = null).forPerson).isEqualTo("family")
        assertThat(form.unchanged).isFalse()
    }

    @Test
    fun aDepartedPersonsTagIsKeptOnATitleEditAndTheSummaryEndsAtTheTime() {
        val form = editTimed(today.atTime(19, 30), 90, forPerson = "gone")
        form.updateTitle("Dinner at Jo's")
        assertThat(form.draft(createdBy = null).forPerson).isEqualTo("gone")
        // Only the id is stored, so there is no name to show.
        assertThat(form.summary { names[it] }).isEqualTo("Today · 19:30–21:00")
    }

    @Test
    fun anEventWithNoLengthOpensOnOneHourWithNoExtraChip() {
        val form = editTimed(today.atTime(19, 30), 0)
        assertThat(form.lengths).isEqualTo(LENGTH_CHOICES)
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun anAllDayEditIsAllDayWithTheUsualLengths() {
        val saturday = LocalDate.of(2026, 9, 26)
        val form = edit(EventTime.AllDay(saturday), EventTime.AllDay(saturday.plusDays(1)), title = "Bin day", forPerson = "family")
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        assertThat(form.day).isEqualTo(saturday)
        assertThat(form.lengths).isEqualTo(LENGTH_CHOICES)
        assertThat(form.length).isEqualTo(Duration.ofHours(1))
        assertThat(form.unchanged).isTrue()
    }

    @Test
    fun anEventOverSeveralDaysLocksItsDates() {
        val halfTerm = edit(EventTime.AllDay(LocalDate.of(2026, 9, 22)), EventTime.AllDay(LocalDate.of(2026, 9, 25)), "Half term", "family")
        assertThat(halfTerm.datesLocked).isTrue()
        assertThat(halfTerm.lockedDatesLabel).isEqualTo("Tue 22 – Thu 24 · change dates on your phone")
        assertThat(halfTerm.summary()).isEqualTo("Yesterday – Tomorrow · All day · Family")
        assertThat(edit(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1))).datesLocked).isFalse()
        // 22:00 to 01:00 crosses midnight; 22:00 to 00:00 ends at midnight, which counts as the same day.
        assertThat(editTimed(today.atTime(22, 0), 180).datesLocked).isTrue()
        assertThat(editTimed(today.atTime(22, 0), 120).datesLocked).isFalse()
        assertThat(editTimed(today.atTime(22, 0), 120).lockedDatesLabel).isNull()
    }

    @Test
    fun aLockedSpanCrossingAMonthOrYearNamesTheMonthOnBothEnds() {
        val crossMonth = edit(
            EventTime.AllDay(LocalDate.of(2026, 9, 30)),
            EventTime.AllDay(LocalDate.of(2026, 10, 3)),
            "Half term", "family",
        )
        assertThat(crossMonth.lockedDatesLabel).isEqualTo("Wed 30 Sep – Fri 2 Oct · change dates on your phone")

        val crossYear = edit(
            EventTime.AllDay(LocalDate.of(2026, 12, 31)),
            EventTime.AllDay(LocalDate.of(2027, 1, 3)),
            "New Year trip", "family",
        )
        assertThat(crossYear.lockedDatesLabel).isEqualTo("Thu 31 Dec – Sat 2 Jan · change dates on your phone")
    }

    @Test
    fun aLockedEditKeepsItsDatesAndChangesOnlyTitleAndWho() {
        val start = EventTime.AllDay(LocalDate.of(2026, 9, 22))
        val end = EventTime.AllDay(LocalDate.of(2026, 9, 25))
        val form = edit(start, end, title = "Half term", forPerson = "family")
        form.updateTitle("Half term at Gran's")
        form.chooseWho(mia)
        val draft = form.draft(createdBy = null)
        assertThat(listOf(draft.start, draft.end)).containsExactly(start, end).inOrder()
        assertThat(draft.title to draft.forPerson).isEqualTo("Half term at Gran's" to "mia")
    }

    @Test
    fun theSummaryReadsLikeTheWhenRowWithThePerson() {
        val form = new(now = "10:54")
        assertThat(form.summary()).isEqualTo("Today · 14:00–15:00 · Family")
        form.chooseDay(today.plusDays(1))
        form.chooseTime(slot(TimeSlot.Afternoon))
        form.chooseWho(mia)
        assertThat(form.summary()).isEqualTo("Tomorrow · 14:00–15:00 · Mia")
        form.chooseDay(LocalDate.of(2026, 9, 26))
        form.chooseTime(TimeChoice.AllDay)
        assertThat(form.summary()).isEqualTo("Sat 26 Sep · All day · Mia")
    }

    @Test
    fun anAllDayDraftIsOneWholeDate() {
        val form = new(now = "20:00")
        form.updateTitle("  Bin day ")
        val draft = form.draft(createdBy = "alex")
        assertThat(draft).isEqualTo(
            EventDraft("Bin day", EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1)), "family", "alex"),
        )
    }

    @Test
    fun aTimedDraftIsInTheHouseholdZone() {
        val form = new(now = "10:54")
        form.chooseLength(Duration.ofMinutes(30))
        val draft = form.draft(createdBy = null)
        // 14:00 BST is 13:00Z.
        assertThat(draft.start).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T13:00:00Z")))
        assertThat(draft.end).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T13:30:00Z")))
    }

    @Test
    fun aDraftOnTheAutumnChangeDayUsesTheLocalTimes() {
        val form = new(now = "10:00", on = LocalDate.of(2026, 10, 24))
        form.updateTitle("Bonfire")
        form.chooseDay(LocalDate.of(2026, 10, 25))
        form.chooseTime(slot(TimeSlot.Evening))
        // 18:00 on 25 October is after the clocks go back: GMT.
        val evening = form.draft(createdBy = null)
        assertThat(evening.start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T18:00:00Z")))
        assertThat(evening.end).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T19:00:00Z")))
        // 01:30 happens twice that night: the earlier (BST) one is taken, and 2 h is two hours of real time.
        form.chooseTime(TimeChoice.Custom(LocalTime.of(1, 30)))
        form.chooseLength(Duration.ofHours(2))
        val early = form.draft(createdBy = null)
        assertThat(early.start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T00:30:00Z")))
        assertThat(early.end).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T02:30:00Z")))
        assertThat(form.summary()).isEqualTo("Tomorrow · 01:30–02:30 · Family")
    }

    @Test
    fun aDraftInTheSpringGapMovesForward() {
        val form = new(on = LocalDate.of(2026, 3, 28))
        form.updateTitle("Early start")
        form.chooseDay(LocalDate.of(2026, 3, 29))
        form.chooseTime(TimeChoice.Custom(LocalTime.of(1, 30)))
        // 01:30 doesn't exist on 29 March: it moves forward by the gap, to 02:30 BST (01:30Z).
        assertThat(form.draft(createdBy = null).start).isEqualTo(EventTime.Timed(Instant.parse("2026-03-29T01:30:00Z")))
    }

    @Test
    fun anEditThatLeavesTheTimesAloneKeepsTheEventsOwnInstants() {
        // 01:30 GMT on 25 October is the second 01:30 that night; rebuilt from the chips it would be the first (BST).
        val secondHalfPast = Instant.parse("2026-10-25T01:30:00Z")
        val start = EventTime.Timed(secondHalfPast)
        val end = EventTime.Timed(secondHalfPast.plusSeconds(3_600))
        val form = edit(start, end)
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(1, 30)))
        assertThat(form.unchanged).isTrue()
        form.updateTitle("Night feed")
        val draft = form.draft(createdBy = null)
        assertThat(listOf(draft.start, draft.end)).containsExactly(start, end).inOrder()
    }

    @Test
    fun aNewEventEndsAtMidnightAtTheLatest() {
        val form = new()
        form.chooseTime(TimeChoice.Custom(LocalTime.of(23, 30)))
        val draft = form.draft(createdBy = null)
        // 23:30 BST + 1 h would cross midnight: it stops at 00:00 BST (23:00Z).
        assertThat(draft.start).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T22:30:00Z")))
        assertThat(draft.end).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T23:00:00Z")))
        assertThat(form.summary()).isEqualTo("Today · 23:30–00:00 · Family")
        // Opened again to edit, it is a one-day event, not a locked multi-day one.
        val reopened = edit(draft.start, draft.end)
        assertThat(reopened.datesLocked).isFalse()
        assertThat(reopened.length).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun tomorrowChosenAt2330IsTheDateItShowed() {
        // Opened at 23:30 on the 24th. The chip holds a date, not "today + 1", so a Save after midnight keeps it;
        // Task 9's host test moves the clock past midnight before Save.
        val form = new(now = "23:30", on = LocalDate.of(2026, 10, 24))
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        form.updateTitle("Bonfire")
        form.chooseDay(form.dayChoices[1])
        assertThat(form.day).isEqualTo(LocalDate.of(2026, 10, 25))
        assertThat(form.time).isEqualTo(slot(TimeSlot.Morning))
        // The draft depends only on the chosen date: 09:00 GMT on the 25th, the clock-change day.
        assertThat(form.draft(createdBy = null).start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T09:00:00Z")))
    }

    @Test
    fun canSaveNeedsATitleOtherThanSpaces() {
        val form = new()
        assertThat(form.canSave).isFalse()
        form.updateTitle("   ")
        assertThat(form.canSave).isFalse()
        form.updateTitle("Swim")
        assertThat(form.canSave).isTrue()
    }

    @Test
    fun typingPastTheLimitIsIgnoredButShorteningIsAllowed() {
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
    fun anEditIsUnchangedUntilSomethingDiffersBeyondSurroundingSpaces() {
        val form = editTimed(today.atTime(19, 30), 90)
        form.updateTitle("  Dinner with Jo & Priya  ")
        assertThat(form.unchanged).isTrue()
        form.chooseWho(alex)
        assertThat(form.unchanged).isTrue()
        form.chooseLength(Duration.ofHours(1))
        assertThat(form.unchanged).isFalse()
    }

    @Test
    fun aNewEventIsNeverUnchanged() {
        assertThat(new().unchanged).isFalse()
    }

    @Test
    fun anUntouchedEditWithNoValidLengthRebuildsFromTheFormsChips() {
        val expectedStart = today.atTime(19, 30).atZone(london).toInstant()
        val expectedEnd = today.atTime(20, 30).atZone(london).toInstant()

        val zero = editTimed(today.atTime(19, 30), 0)
        val zeroDraft = zero.draft(createdBy = null)
        assertThat(zeroDraft.start).isEqualTo(EventTime.Timed(expectedStart))
        assertThat(zeroDraft.end).isEqualTo(EventTime.Timed(expectedEnd))

        val negative = editTimed(today.atTime(19, 30), -30)
        val negativeDraft = negative.draft(createdBy = null)
        assertThat(negativeDraft.start).isEqualTo(EventTime.Timed(expectedStart))
        assertThat(negativeDraft.end).isEqualTo(EventTime.Timed(expectedEnd))
    }

    @Test
    fun tappingTheAlreadySelectedDayTimeOrLengthChangesNothing() {
        // 01:30 GMT on 25 October is the second 01:30 that night; re-choosing it must not silently rebuild it as the first.
        val secondHalfPast = Instant.parse("2026-10-25T01:30:00Z")
        val start = EventTime.Timed(secondHalfPast)
        val end = EventTime.Timed(secondHalfPast.plusSeconds(3_600))
        val form = edit(start, end)
        form.chooseDay(form.day)
        form.chooseTime(form.time)
        form.chooseLength(form.length)
        val draft = form.draft(createdBy = null)
        assertThat(listOf(draft.start, draft.end)).containsExactly(start, end).inOrder()
        assertThat(form.unchanged).isTrue()
    }

    @Test
    fun chooseDayOnALockedEditDoesNotChangeItsDates() {
        val start = EventTime.AllDay(LocalDate.of(2026, 9, 22))
        val end = EventTime.AllDay(LocalDate.of(2026, 9, 25))
        val form = edit(start, end, title = "Half term", forPerson = "family")
        form.chooseDay(today)
        val draft = form.draft(createdBy = null)
        assertThat(listOf(draft.start, draft.end)).containsExactly(start, end).inOrder()
    }

    @Test
    fun anEditsTimeSurvivesChooseDay() {
        val form = editTimed(today.atTime(19, 30), 90)
        form.chooseDay(today.plusDays(2))
        assertThat(form.time).isEqualTo(TimeChoice.Custom(LocalTime.of(19, 30)))
    }

    @Test
    fun theFormUsesTheHouseholdZoneNotTheJvmDefault() {
        // Pacific/Auckland is far from Europe/London, the gate machine's JVM default; NZ's DST hasn't started yet.
        val auckland = ZoneId.of("Pacific/Auckland")
        val form = EventForm(EventForm.Mode.New, today, LocalTime.of(10, 54), auckland, signedIn = null, preselectedDay = null)
        val draft = form.draft(createdBy = null)
        // 14:00 NZST (UTC+12) is 02:00Z, not 13:00Z as it would be in London.
        assertThat(draft.start).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T02:00:00Z")))
        assertThat(draft.end).isEqualTo(EventTime.Timed(Instant.parse("2026-09-23T03:00:00Z")))
        assertThat(form.summary { names.getValue(it) }).isEqualTo("Today · 14:00–15:00 · Family")

        // 22:00 Auckland + 3 h crosses midnight there (13:00Z start), but the same instants read as 23:00-02:00 BST
        // in London, a single day: a household-zone regression to the JVM default would flip this to unlocked.
        val lateStart = LocalDate.of(2026, 9, 23).atTime(22, 0).atZone(auckland).toInstant()
        val lateEnd = lateStart.plusSeconds(3 * 3_600)
        val lateForm = EventForm(
            EventForm.Mode.Edit(
                EditableEvent(EventRef("c1", "s1", "e1"), "Late one", EventTime.Timed(lateStart), EventTime.Timed(lateEnd), "alex"),
            ),
            today, LocalTime.of(10, 54), auckland, signedIn = null, preselectedDay = null,
        )
        assertThat(lateForm.datesLocked).isTrue()
    }
}
