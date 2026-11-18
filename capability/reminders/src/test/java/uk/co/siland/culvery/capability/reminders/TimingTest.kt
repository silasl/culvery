package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId

class TimingTest {
    private val zone = ZoneId.of("Europe/London")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun utc(text: String) = Instant.parse(text).toEpochMilli()

    private fun reminder(schedule: Schedule, time: LocalTime, answeredAt: Long) =
        Reminder(1, "", listOf(PersonId.FAMILY), PersonId.FAMILY, time, schedule, answeredAt, null, null)

    private fun due(dueAt: Long, snoozedUntil: Long? = null, chimedAt: Long? = null) =
        Due(DueKey.Mine(1), dueAt, snoozedUntil, chimedAt)

    private val mwf = weeklyOn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    private val half7 = LocalTime.of(7, 30)

    @Test
    fun aOneOffIsWaitingFromItsMinute() {
        val r = reminder(Schedule.Once(LocalDate.of(2026, 11, 20)), LocalTime.of(16, 30), at("2026-11-20T10:00:00"))
        assertThat(r.due(at("2026-11-20T16:29:59"), zone)).isNull()
        val due = r.due(at("2026-11-20T16:30:00"), zone)!!
        assertThat(due.dueAtMillis).isEqualTo(at("2026-11-20T16:30:00"))
        assertThat(r.due(at("2026-11-23T16:30:00"), zone)).isEqualTo(due)
    }

    @Test
    fun weeklyDaysFireOnTheirDays() {
        val r = reminder(mwf, half7, at("2026-11-16T07:31:00"))
        assertThat(r.due(at("2026-11-17T12:00:00"), zone)).isNull()
        assertThat(r.due(at("2026-11-18T07:30:00"), zone)!!.dueAtMillis).isEqualTo(at("2026-11-18T07:30:00"))
    }

    @Test
    fun threeMissedTimesAreOneWaitingShowingTheLatest() {
        val r = reminder(mwf, half7, at("2026-11-08T12:00:00"))
        assertThat(r.due(at("2026-11-13T12:00:00"), zone)!!.dueAtMillis).isEqualTo(at("2026-11-13T07:30:00"))
    }

    @Test
    fun aTimeMissedWhileOffIsStillWaiting() {
        val r = reminder(weeklyOn(DayOfWeek.FRIDAY), half7, at("2026-11-06T12:00:00"))
        assertThat(r.due(at("2026-11-19T12:00:00"), zone)!!.dueAtMillis).isEqualTo(at("2026-11-13T07:30:00"))
    }

    @Test
    fun doneEndsItUntilTheNextTime() {
        val now = at("2026-11-18T12:00:00")
        val r = reminder(mwf, half7, now)
        assertThat(r.due(now, zone)).isNull()
        assertThat(r.nextDue(now, zone)).isEqualTo(at("2026-11-20T07:30:00"))
        val once = reminder(Schedule.Once(LocalDate.of(2026, 11, 18)), half7, 0)
        assertThat(once.nextDue(now, zone)).isNull()
    }

    @Test
    fun aSpringGapTimeFiresOnceAnHourOn() {
        val r = reminder(weeklyOn(DayOfWeek.SUNDAY), LocalTime.of(1, 30), utc("2027-03-27T12:00:00Z"))
        assertThat(timeOn(LocalDate.of(2027, 3, 28), LocalTime.of(1, 30), zone)).isEqualTo(utc("2027-03-28T01:30:00Z"))
        assertThat(r.due(utc("2027-03-28T01:29:59Z"), zone)).isNull()
        assertThat(r.due(utc("2027-03-28T01:30:00Z"), zone)!!.dueAtMillis).isEqualTo(utc("2027-03-28T01:30:00Z"))
    }

    @Test
    fun anAutumnOverlapTimeFiresOnce() {
        val sunday = weeklyOn(DayOfWeek.SUNDAY)
        val r = reminder(sunday, LocalTime.of(1, 30), utc("2027-10-30T12:00:00Z"))
        assertThat(r.due(utc("2027-10-31T01:30:00Z"), zone)!!.dueAtMillis).isEqualTo(utc("2027-10-31T00:30:00Z"))
        val answered = reminder(sunday, LocalTime.of(1, 30), utc("2027-10-31T00:31:00Z"))
        assertThat(answered.due(utc("2027-10-31T01:30:00Z"), zone)).isNull()
    }

    @Test
    fun laterClosesItForThirtyMinutesThenOwesAChime() {
        val now = at("2026-11-20T16:30:00")
        val d = due(now, snoozedUntil = now + LATER_MS, chimedAt = now)
        assertThat(d.snoozed(now)).isTrue()
        assertThat(d.snoozed(now + LATER_MS - 1)).isTrue()
        assertThat(d.chimeOwed(now + 1, null)).isFalse()
        assertThat(d.snoozed(now + LATER_MS)).isFalse()
        assertThat(d.chimeOwed(now + LATER_MS, null)).isTrue()
    }

    @Test
    fun aSecondLaterMovesItOn() {
        val now = at("2026-11-20T16:30:00")
        val second = due(now, snoozedUntil = now + 10 * 60_000 + LATER_MS)
        assertThat(second.snoozed(now + LATER_MS)).isTrue()
        assertThat(second.snoozed(now + 10 * 60_000 + LATER_MS)).isFalse()
    }

    @Test
    fun aNewerTimeEndsASnooze() {
        val now = at("2026-11-20T16:30:00")
        assertThat(due(now - 5 * 60_000, snoozedUntil = now + 10 * 60_000).snoozed(now)).isFalse()
        assertThat(due(now - 25 * 60_000, snoozedUntil = now + 10 * 60_000).snoozed(now)).isTrue()
    }

    @Test
    fun aSnoozeFurtherAheadThanLaterHasExpired() {
        val now = at("2026-11-20T16:30:00")
        assertThat(due(now - 60_000, snoozedUntil = now + LATER_MS + 60_000).snoozed(now)).isFalse()
    }

    @Test
    fun aChimeIsOwedOncePerCause() {
        val now = at("2026-11-20T16:30:00")
        assertThat(due(now).chimeOwed(now, null)).isTrue()
        assertThat(due(now, chimedAt = now).chimeOwed(now, null)).isFalse()
        assertThat(due(now - 60_000, chimedAt = now - 30_000).chimeOwed(now, null)).isFalse()
        assertThat(due(now - 60_000, chimedAt = now - 30_000).chimeOwed(now, now - 10_000)).isTrue()
        val held = now - 5 * 60_000
        val snoozed = due(now - 60 * 60_000, snoozedUntil = now + 10 * 60_000, chimedAt = held - 1)
        assertThat(snoozed.chimeOwed(now, held)).isFalse()
        assertThat(due(now - 60 * 60_000, snoozedUntil = now - 1, chimedAt = held).chimeOwed(now, held)).isTrue()
    }

    @Test
    fun aChimeMarkAheadOfTheClockCountsAsNone() {
        val now = at("2026-11-20T16:30:00")
        assertThat(due(now - 60_000, chimedAt = now + 60_000).chimeOwed(now, null)).isTrue()
    }

    @Test
    fun aRepeatMadeAfterTodaysTimeWaitsForItsNextDay() {
        val r = reminder(weeklyOn(DayOfWeek.TUESDAY), half7, at("2026-11-17T09:00:00"))
        assertThat(r.due(at("2026-11-17T09:00:00"), zone)).isNull()
        assertThat(r.due(at("2026-11-17T23:59:00"), zone)).isNull()
        assertThat(r.due(at("2026-11-24T07:30:00"), zone)!!.dueAtMillis).isEqualTo(at("2026-11-24T07:30:00"))
    }

    @Test
    fun aDueHidesItsTimes() {
        val text = due(1_700_000_000_123, snoozedUntil = 1_700_000_999_456, chimedAt = 1_700_000_111_789).toString()
        assertThat(text).doesNotContain("1700000")
        assertThat(text).contains("Mine")
    }

    @Test
    fun oldestDueComesFirst() {
        val newest = Due(DueKey.Mine(1), 300, null, null)
        val tiedHigh = Due(DueKey.Mine(9), 100, null, null)
        val tiedLow = Due(DueKey.Mine(2), 100, null, null)
        val bins = Due(DueKey.Bins(LocalDate.of(2026, 11, 20)), 100, null, null)
        val oldest = Due(DueKey.Mine(5), 50, null, null)
        assertThat(inOrder(listOf(newest, tiedHigh, tiedLow, bins, oldest)))
            .containsExactly(oldest, bins, tiedLow, tiedHigh, newest).inOrder()
    }

    @Test
    fun aWeeklyTimeExactlyAWeekBackIsStillWaiting() {
        val now = at("2026-11-18T07:00:00")
        val wednesday = weeklyOn(DayOfWeek.WEDNESDAY)
        assertThat(reminder(wednesday, half7, at("2026-11-11T08:00:00")).due(now, zone)).isNull()
        assertThat(reminder(wednesday, half7, at("2026-11-10T12:00:00")).due(now, zone)!!.dueAtMillis)
            .isEqualTo(at("2026-11-11T07:30:00"))
    }

    @Test
    fun aFortnightlyReminderIsDueEveryOtherWeek() {
        val fortnightly = Schedule.Repeating(LocalDate.of(2026, 11, 6), "RRULE:FREQ=WEEKLY;INTERVAL=2")
        val r = reminder(fortnightly, half7, at("2026-11-05T12:00:00"))
        assertThat(r.due(at("2026-11-06T07:30:00"), zone)!!.dueAtMillis).isEqualTo(at("2026-11-06T07:30:00"))
        assertThat(r.nextDue(at("2026-11-06T07:30:00"), zone)).isEqualTo(at("2026-11-20T07:30:00"))
        val answered = reminder(fortnightly, half7, at("2026-11-06T08:00:00"))
        assertThat(answered.due(at("2026-11-13T12:00:00"), zone)).isNull()
        assertThat(answered.due(at("2026-11-20T07:30:00"), zone)!!.dueAtMillis).isEqualTo(at("2026-11-20T07:30:00"))
    }

    @Test
    fun aMonthlyReminderMissedWhileOffIsWaitingOnce() {
        val thirdWednesday = Schedule.Repeating(LocalDate.of(2026, 11, 18), "RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=3WE")
        val r = reminder(thirdWednesday, half7, at("2026-11-17T12:00:00"))
        assertThat(r.due(at("2027-02-01T12:00:00"), zone)!!.dueAtMillis).isEqualTo(at("2027-01-20T07:30:00"))
        assertThat(r.nextDue(at("2027-02-01T12:00:00"), zone)).isEqualTo(at("2027-02-17T07:30:00"))
    }

    @Test
    fun aCountedReminderHasNoNextAfterItsLast() {
        val threeFridays = Schedule.Repeating(LocalDate.of(2026, 11, 6), "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3")
        val r = reminder(threeFridays, half7, at("2026-11-20T08:00:00"))
        assertThat(r.nextDue(at("2026-11-13T08:00:00"), zone)).isEqualTo(at("2026-11-20T07:30:00"))
        assertThat(r.nextDue(at("2026-11-20T08:00:00"), zone)).isNull()
        assertThat(r.due(at("2026-11-27T08:00:00"), zone)).isNull()
    }

    @Test
    fun aReminderWithARuleItCannotReadNeverFires() {
        listOf("RRULE:FREQ=HOURLY", "RRULE:FREQ=WEEKLY;BYSETPOS=2", "not a rule").forEach { rule ->
            val r = reminder(Schedule.Repeating(LocalDate.of(2026, 11, 6), rule), half7, 0)
            assertThat(r.due(at("2026-11-20T08:00:00"), zone)).isNull()
            assertThat(r.nextDue(at("2026-11-20T08:00:00"), zone)).isNull()
        }
    }

    @Test
    fun aTimeEqualToAnsweredAtIsNotDue() {
        val now = at("2026-11-18T12:00:00")
        assertThat(reminder(weeklyOn(DayOfWeek.WEDNESDAY), half7, at("2026-11-18T07:30:00")).due(now, zone)).isNull()
        assertThat(reminder(Schedule.Once(LocalDate.of(2026, 11, 18)), half7, at("2026-11-18T07:30:00")).due(now, zone)).isNull()
    }
}
