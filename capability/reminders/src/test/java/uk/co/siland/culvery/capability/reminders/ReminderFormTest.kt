package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd
import uk.co.siland.culvery.core.ui.ClockField

class ReminderFormTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val mia = PersonId("mia")
    private val alex = PersonId("alex")
    private val sam = PersonId("sam")

    private fun form(now: LocalTime = LocalTime.of(10, 0), childOnly: PersonId? = null, original: Reminder? = null) =
        ReminderForm(original, today, now, childOnly)

    private fun reminder(
        words: String = "PE kit",
        who: List<PersonId> = listOf(alex),
        time: LocalTime = LocalTime.of(7, 30),
        schedule: Schedule = Schedule.Once(today.plusDays(2)),
        event: EventLink? = null,
    ) = Reminder(7, words, who, alex, time, schedule, 0, null, null, event)

    @Test
    fun aNewOneOffLateAtNightStartsTomorrowRatherThanAtMidnightToday() {
        val late = form(now = LocalTime.of(23, 56))
        assertThat(late.time).isEqualTo(LocalTime.MIDNIGHT)
        assertThat(late.date).isEqualTo(today.plusDays(1))
        assertThat(form(now = LocalTime.of(23, 55)).date).isEqualTo(today)
    }

    @Test
    fun aChildsWhoIsFixedToThem() {
        val child = form(childOnly = mia)
        assertThat(child.who).containsExactly(mia)
        child.toggleWho(alex)
        child.toggleWho(PersonId.FAMILY)
        child.toggleWho(mia)
        assertThat(child.who).containsExactly(mia)
    }

    @Test
    fun anAdultsStartsAsFamilyAndPicksAsTheCalendarsDo() {
        val adult = form()
        assertThat(adult.who).containsExactly(PersonId.FAMILY)
        adult.toggleWho(alex)
        assertThat(adult.who).containsExactly(alex)
        adult.toggleWho(sam)
        assertThat(adult.who).containsExactly(alex, sam).inOrder()
        adult.toggleWho(alex)
        adult.toggleWho(sam)
        assertThat(adult.who).containsExactly(sam)
        adult.toggleWho(PersonId.FAMILY)
        assertThat(adult.who).containsExactly(PersonId.FAMILY)
    }

    @Test
    fun saveNeedsWordsAndSomeone() {
        val f = form()
        assertThat(f.canSave).isFalse()
        f.words = "   \n "
        assertThat(f.canSave).isFalse()
        f.words = "PE kit"
        assertThat(f.canSave).isTrue()
        f.repeat.choose(RepeatChoice.Weekly)
        assertThat(f.canSave).isTrue()
        assertThat(form(original = reminder(who = emptyList())).canSave).isFalse()
    }

    @Test
    fun theDraftTidiesTheWordsAndIsOnceUntilARepeatIsPicked() {
        val f = form(now = LocalTime.of(10, 3))
        f.words = "  PE \n kit "
        f.toggleWho(mia)
        assertThat(f.toDraft()).isEqualTo(ReminderDraft("PE kit", listOf(mia), LocalTime.of(10, 5), Schedule.Once(today)))
    }

    @Test
    fun aRepeatFromThePickerBecomesARepeatingSchedule() {
        val f = form()
        f.words = "PE kit"
        f.date = today.plusDays(5)
        f.repeat.choose(RepeatChoice.Weekly)
        f.repeat.toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY)
        assertThat(f.toDraft().schedule).isEqualTo(Schedule.Repeating(today.plusDays(5), "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE"))
        f.repeat.stopsAfter()
        assertThat(f.toDraft().schedule).isEqualTo(Schedule.Repeating(today.plusDays(5), "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=10"))
        f.repeat.choose(RepeatChoice.Never)
        assertThat(f.toDraft().schedule).isEqualTo(Schedule.Once(today.plusDays(5)))
    }

    @Test
    fun anExistingRepeatOpensOnItsChoice() {
        val monday = today.plusDays(5)
        val f = form(original = reminder(schedule = Schedule.Repeating(monday, "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;COUNT=6")))
        assertThat(f.date).isEqualTo(monday)
        assertThat(f.repeat.choice).isEqualTo(RepeatChoice.Fortnightly)
        assertThat(f.repeat.extraDays).containsExactly(DayOfWeek.THURSDAY)
        assertThat(f.repeat.end).isEqualTo(RepeatEnd.After(6))
        assertThat(f.edited).isFalse()

        val phone = form(original = reminder(schedule = Schedule.Repeating(monday, "RRULE:FREQ=MONTHLY;INTERVAL=3")))
        assertThat(phone.repeat.keeping).isTrue()
        assertThat(phone.toDraft().schedule).isEqualTo(Schedule.Repeating(monday, "RRULE:FREQ=MONTHLY;INTERVAL=3"))
    }

    @Test
    fun theDayRunsFromTodayToAYearAhead() {
        val f = form()
        assertThat(f.date).isEqualTo(today)
        assertThat(f.allowedDay(today)).isTrue()
        assertThat(f.allowedDay(today.minusDays(1))).isFalse()
        assertThat(f.allowedDay(today.plusYears(1))).isTrue()
        assertThat(f.allowedDay(today.plusYears(1).plusDays(1))).isFalse()
    }

    @Test
    fun theTimeStartsAtNowUpToTheNextFiveMinutes() {
        assertThat(form(now = LocalTime.of(10, 3, 20)).time).isEqualTo(LocalTime.of(10, 5))
        assertThat(form(now = LocalTime.of(10, 5)).time).isEqualTo(LocalTime.of(10, 5))
        assertThat(form(now = LocalTime.of(10, 5, 1)).time).isEqualTo(LocalTime.of(10, 10))
        assertThat(form(now = LocalTime.of(10, 58)).time).isEqualTo(LocalTime.of(11, 0))
        assertThat(form(now = LocalTime.of(23, 58)).time).isEqualTo(LocalTime.MIDNIGHT)
    }

    @Test
    fun minutesStepInFives() {
        val f = form(now = LocalTime.of(10, 0))
        f.step(ClockField.Minute, up = true)
        assertThat(f.time).isEqualTo(LocalTime.of(10, 5))
        f.time = LocalTime.of(10, 3)
        f.step(ClockField.Minute, up = true)
        assertThat(f.time).isEqualTo(LocalTime.of(10, 5))
        f.time = LocalTime.of(10, 3)
        f.step(ClockField.Minute, up = false)
        assertThat(f.time).isEqualTo(LocalTime.of(10, 0))
        f.time = LocalTime.of(10, 0)
        f.step(ClockField.Minute, up = false)
        assertThat(f.time).isEqualTo(LocalTime.of(9, 55))
        f.time = LocalTime.of(10, 55)
        f.step(ClockField.Minute, up = true)
        assertThat(f.time).isEqualTo(LocalTime.of(11, 0))
    }

    @Test
    fun hoursStepByOneAndTheDayWraps() {
        val f = form()
        f.step(ClockField.Hour, up = true)
        assertThat(f.time).isEqualTo(LocalTime.of(11, 0))
        f.time = LocalTime.of(23, 40)
        f.step(ClockField.Hour, up = true)
        assertThat(f.time).isEqualTo(LocalTime.of(0, 40))
        f.step(ClockField.Hour, up = false)
        assertThat(f.time).isEqualTo(LocalTime.of(23, 40))
        f.time = LocalTime.of(23, 58)
        f.step(ClockField.Minute, up = true)
        assertThat(f.time).isEqualTo(LocalTime.MIDNIGHT)
        f.step(ClockField.Minute, up = false)
        assertThat(f.time).isEqualTo(LocalTime.of(23, 55))
    }

    @Test
    fun wordsStopAtSixty() {
        val f = form()
        f.words = "a".repeat(WORDS_MAX + 1)
        assertThat(f.words).hasLength(WORDS_MAX)
        f.words = "b".repeat(200)
        assertThat(f.words).hasLength(WORDS_MAX)
    }

    @Test
    fun anExistingReminderOpensAsItIs() {
        val event = EventLink("event-1", today.plusDays(3), LocalTime.of(9, 0), null, followsRepeat = false)
        val weekly = reminder(who = listOf(mia, sam), time = LocalTime.of(8, 15), schedule = weeklyOn(DayOfWeek.SATURDAY), event = event)
        val f = form(original = weekly)
        assertThat(f.words).isEqualTo("PE kit")
        assertThat(f.who).containsExactly(mia, sam).inOrder()
        assertThat(f.repeat.choice).isEqualTo(RepeatChoice.Weekly)
        assertThat(f.date).isEqualTo(weekly.schedule.first)
        assertThat(f.time).isEqualTo(LocalTime.of(8, 15))
        assertThat(f.toDraft()).isEqualTo(ReminderDraft("PE kit", listOf(mia, sam), LocalTime.of(8, 15), weekly.schedule, event))
        val once = form(original = reminder())
        assertThat(once.repeat.repeats).isFalse()
        assertThat(once.date).isEqualTo(today.plusDays(2))
    }

    @Test
    fun itIsEditedOnceAnythingChanges() {
        val f = form(original = reminder())
        assertThat(f.edited).isFalse()
        f.words = "PE kit!"
        assertThat(f.edited).isTrue()
        f.words = "PE kit"
        assertThat(f.edited).isFalse()
        f.step(ClockField.Hour, up = true)
        assertThat(f.edited).isTrue()
        assertThat(form().edited).isFalse()
        val picked = form(original = reminder())
        picked.repeat.choose(RepeatChoice.Never)
        assertThat(picked.edited).isTrue()
    }
}
