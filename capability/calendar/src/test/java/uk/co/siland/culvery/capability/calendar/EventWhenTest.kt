package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class EventWhenTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val now = LocalTime.of(10, 20)
    private val sat = LocalDate.of(2026, 9, 26)
    private val fri23Oct = LocalDate.of(2026, 10, 23)

    private fun at(hour: Int, minute: Int = 0) = LocalTime.of(hour, minute)

    private fun timed(
        start: LocalTime,
        end: LocalTime,
        endDays: Int = 0,
        date: LocalDate? = sat,
    ) = EventWhen(allDay = false, startDate = date, startClock = start, endDays = endDays, endClock = end)

    private fun EventWhen.clocks() = startClock to endClock

    @Test
    fun theDefaultIsTheNextHourTodayForAnHour() {
        val d = EventWhen.defaults(today, today, now)
        assertThat(d.allDay).isFalse()
        assertThat(d.startClock).isEqualTo(at(11))
        assertThat(d.endClock).isEqualTo(at(12))
        assertThat(d.endDays).isEqualTo(0)
    }

    @Test
    fun anotherDayDefaultsToNine() {
        val d = EventWhen.defaults(sat, today, now)
        assertThat(d.clocks()).isEqualTo(at(9) to at(10))
        assertThat(EventWhen.defaults(null, today, now).startClock).isEqualTo(at(11))
    }

    @Test
    fun lateTodayCapsAtElevenAndEndsAtMidnight() {
        val d = EventWhen.defaults(today, today, at(23, 10))
        assertThat(d.clocks()).isEqualTo(at(23) to at(0))
        assertThat(d.endDays).isEqualTo(1)
    }

    @Test
    fun pickingAStartsDateWithUntouchedTimesReappliesTheDefault() {
        val d = EventWhen.defaults(today, today, now)
        val picked = d.pickStartDate(sat, defaults = true, today = today, now = now)
        assertThat(picked).isEqualTo(EventWhen.defaults(sat, today, now))
        assertThat(picked.clocks()).isEqualTo(at(9) to at(10))
    }

    @Test
    fun movingTheStartDateKeepsTheLength() {
        val w = timed(at(18), at(16), endDays = 2, date = fri23Oct)
        val moved = w.pickStartDate(fri23Oct.plusDays(1), defaults = false, today = today, now = now)
        assertThat(moved.startDate).isEqualTo(LocalDate.of(2026, 10, 24))
        assertThat(moved.endDate).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(moved.clocks()).isEqualTo(at(18) to at(16))
    }

    @Test
    fun aStartsStepMovesTheEndAndKeepsTheLength() {
        assertThat(timed(at(10), at(11)).stepStart(ClockField.Hour, up = true).clocks()).isEqualTo(at(11) to at(12))
        assertThat(timed(at(11), at(12)).stepStart(ClockField.Minute, up = false).clocks()).isEqualTo(at(10, 45) to at(11, 45))
        assertThat(timed(at(10, 45), at(11, 45)).stepStart(ClockField.Minute, up = true).clocks()).isEqualTo(at(11) to at(12))
    }

    @Test
    fun anOffQuarterMinuteSnapsToTheNextQuarter() {
        val w = timed(at(10, 37), at(11, 37))
        assertThat(w.stepStart(ClockField.Minute, up = true).startClock).isEqualTo(at(10, 45))
        assertThat(w.stepStart(ClockField.Minute, up = false).startClock).isEqualTo(at(10, 30))
    }

    @Test
    fun aStartsStepWrapsWithinTheDayAndTheEndFollows() {
        val a = timed(at(22), at(23)).stepStart(ClockField.Hour, up = true)
        assertThat(a.clocks()).isEqualTo(at(23) to at(0))
        assertThat(a.endDays).isEqualTo(1)
        val b = a.stepStart(ClockField.Hour, up = true)
        assertThat(b.clocks()).isEqualTo(at(0) to at(1))
        assertThat(b.endDays).isEqualTo(0)
        assertThat(b.startDate).isEqualTo(sat)

        val up = timed(at(23, 45), at(0, 45), endDays = 1).stepStart(ClockField.Minute, up = true)
        assertThat(up.startClock).isEqualTo(at(0))
        assertThat(up.startDate).isEqualTo(sat)
        assertThat(up.endDays).isEqualTo(0)
        val down = timed(at(0), at(1)).stepStart(ClockField.Minute, up = false)
        assertThat(down.startClock).isEqualTo(at(23, 45))
        assertThat(down.endClock).isEqualTo(at(0, 45))
        assertThat(down.endDays).isEqualTo(1)
        assertThat(down.startDate).isEqualTo(sat)
    }

    @Test
    fun anEndsStepThatWouldReachTheStartIsDisabled() {
        var w = timed(at(10), at(11))
        assertThat(w.canStepEnd(ClockField.Hour, up = false)).isFalse()
        for (expected in listOf(at(10, 45), at(10, 30), at(10, 15))) {
            w = checkNotNull(w.stepEnd(ClockField.Minute, up = false))
            assertThat(w.endClock).isEqualTo(expected)
        }
        assertThat(w.canStepEnd(ClockField.Minute, up = false)).isFalse()
        assertThat(w.stepEnd(ClockField.Minute, up = false)).isNull()
        assertThat(w.canStepEnd(ClockField.Minute, up = true)).isTrue()
    }

    @Test
    fun anEndsStepThatWouldLeaveItsDayIsDisabled() {
        assertThat(timed(at(10), at(23)).canStepEnd(ClockField.Hour, up = true)).isFalse()
        assertThat(timed(at(10), at(23, 45)).canStepEnd(ClockField.Minute, up = true)).isFalse()
        assertThat(timed(at(10), at(0), endDays = 1).canStepEnd(ClockField.Minute, up = false)).isFalse()
        assertThat(timed(at(10), at(0), endDays = 1).canStepEnd(ClockField.Hour, up = false)).isFalse()
    }

    @Test
    fun onALaterDayTheEndClockIsFree() {
        val w = timed(at(18), at(16), endDays = 2)
        assertThat(w.stepEnd(ClockField.Hour, up = false)?.endClock).isEqualTo(at(15))
        assertThat(w.stepEnd(ClockField.Hour, up = true)?.endClock).isEqualTo(at(17))
        assertThat(timed(at(18), at(9), endDays = 1).stepEnd(ClockField.Hour, up = false)?.endClock).isEqualTo(at(8))
    }

    @Test
    fun movingTheEndBackToTheStartsDayMakesItAnHourAfter() {
        val a = checkNotNull(timed(at(10), at(9), endDays = 1).pickEndDate(sat))
        assertThat(a.endDays).isEqualTo(0)
        assertThat(a.endClock).isEqualTo(at(11))

        val b = checkNotNull(timed(at(23, 30), at(0, 15), endDays = 1).pickEndDate(sat))
        assertThat(b.endDays).isEqualTo(1)
        assertThat(b.endClock).isEqualTo(at(0, 30))
    }

    @Test
    fun theEndsDateAllowsTheStartToFiftyNineDaysAfter() {
        val w = timed(at(10), at(11))
        assertThat(w.pickEndDate(sat.plusDays(59))?.endDays).isEqualTo(59)
        assertThat(w.pickEndDate(sat.plusDays(59))?.capError).isNull()
        assertThat(w.pickEndDate(sat.plusDays(60))).isNull()
        assertThat(w.pickEndDate(sat.minusDays(1))).isNull()
        assertThat(timed(at(10), at(11), date = null).pickEndDate(sat)).isNull()
    }

    @Test
    fun pickingALaterEndDayKeepsTheEndTime() {
        val w = checkNotNull(timed(at(10), at(11)).pickEndDate(sat.plusDays(3)))
        assertThat(w.endDays).isEqualTo(3)
        assertThat(w.endClock).isEqualTo(at(11))
    }

    @Test
    fun aSixtyOneDaySpanHasTheCapsMessage() {
        val start = ZonedDateTime.of(sat, at(10), london)
        val w = EventWhen.of(EventSpan.Timed(start, start.plusDays(60)), london)
        assertThat(w.endDays).isEqualTo(60)
        assertThat(w.capError).isEqualTo(UNTIL_TOO_LATE)
        assertThat(UNTIL_TOO_LATE).isEqualTo("An event can last up to 60 days. Pick an earlier end.")
        val allDay = EventWhen.of(EventSpan.AllDay(sat, sat.plusDays(61)), london)
        assertThat(allDay.capError).isEqualTo(UNTIL_TOO_LATE)
        assertThat(EventWhen.of(EventSpan.AllDay(sat, sat.plusDays(60)), london).capError).isNull()
    }

    @Test
    fun allDayKeepsBothDatesAndTogglingBackRestoresTheTimes() {
        val w = timed(at(18), at(16), endDays = 2, date = fri23Oct)
        val allDay = w.withAllDay(true)
        assertThat(allDay.allDay).isTrue()
        assertThat(allDay.startDate).isEqualTo(fri23Oct)
        assertThat(allDay.endDate).isEqualTo(fri23Oct.plusDays(2))
        assertThat(allDay.withAllDay(false)).isEqualTo(w)
    }

    @Test
    fun aMidnightEndToggledToAllDayIsOneDay() {
        val w = EventWhen.defaults(today, today, at(23, 10))
        val allDay = w.withAllDay(true)
        assertThat(allDay.endDays).isEqualTo(0)
        assertThat(allDay.span(london)).isEqualTo(EventSpan.AllDay(today, today.plusDays(1)))
        assertThat(allDay.withAllDay(false)).isEqualTo(w)
        assertThat(allDay.withAllDay(false).clocks()).isEqualTo(at(23) to at(0))
        assertThat(allDay.withAllDay(false).endDays).isEqualTo(1)
    }

    @Test
    fun anAllDayEventToggledOffAndOnKeepsItsDays() {
        val w = EventWhen.of(EventSpan.AllDay(sat, sat.plusDays(4)), london)
        val timed = w.withAllDay(false)
        assertThat(timed.allDay).isFalse()
        assertThat(timed.startDate).isEqualTo(sat)
        assertThat(timed.endDate).isEqualTo(sat.plusDays(3))
        assertThat(timed.clocks()).isEqualTo(at(9) to at(10))
        assertThat(timed.withAllDay(true)).isEqualTo(w)
    }

    @Test
    fun anEndsDatePickedWhileTimedSurvivesAllDayOn() {
        val w = checkNotNull(timed(at(10), at(11)).pickEndDate(sat.plusDays(3)))
        assertThat(w.withAllDay(true).endDate).isEqualTo(sat.plusDays(3))
    }

    @Test
    fun anEndsDatePickedWhileAllDaySurvivesAllDayOff() {
        val w = checkNotNull(timed(at(10), at(11)).withAllDay(true).pickEndDate(sat.plusDays(3)))
        val back = w.withAllDay(false)
        assertThat(back.endDate).isEqualTo(sat.plusDays(3))
        assertThat(back.clocks()).isEqualTo(at(10) to at(11))
    }

    @Test
    fun anEndsDatePickedWhileAllDayAfterAMidnightEndKeepsItsDate() {
        val midnight = timed(at(23), at(0), endDays = 1).withAllDay(true)
        val back = checkNotNull(midnight.pickEndDate(sat.plusDays(2))).withAllDay(false)
        assertThat(back.endDate).isEqualTo(sat.plusDays(2))
        assertThat(back.endDays).isEqualTo(2)
        assertThat(back.capError).isNull()
        assertThat(back.span(london)).isInstanceOf(EventSpan.Timed::class.java)
    }

    @Test
    fun theMaximumEndsDatePickedWhileAllDayAfterAMidnightEndIsNotOverTheCap() {
        val midnight = timed(at(22), at(0), endDays = 1).withAllDay(true)
        val back = checkNotNull(midnight.pickEndDate(sat.plusDays(59))).withAllDay(false)
        assertThat(back.endDays).isEqualTo(59)
        assertThat(back.capError).isNull()
    }

    @Test
    fun anEndsDatePickedWhileAllDayKeepsAnEndTimeThatIsNotMidnight() {
        val allDay = timed(at(10), at(15)).withAllDay(true)
        val back = checkNotNull(allDay.pickEndDate(sat.plusDays(2))).withAllDay(false)
        assertThat(back.endDays).isEqualTo(2)
        assertThat(back.endClock).isEqualTo(at(15))
        assertThat(back.capError).isNull()
    }

    @Test
    fun aSpanOverTheAutumnClockChangeWhoseWallEndIsBeforeItsStartOpensValid() {
        val day = LocalDate.of(2026, 10, 25)
        val start = ZonedDateTime.of(day, at(1, 45), london).withEarlierOffsetAtOverlap()
        val end = ZonedDateTime.of(day, at(1, 15), london).withLaterOffsetAtOverlap()
        assertThat(end.isAfter(start)).isTrue()
        var w = EventWhen.of(EventSpan.Timed(start, end), london)
        assertThat(w.length.toMinutes()).isGreaterThan(0)
        repeat(7) { w = w.stepStart(ClockField.Minute, up = false) }
        assertThat(w.startClock).isEqualTo(at(0))
        assertThat(w.length.toMinutes()).isEqualTo(60)
    }

    @Test
    fun aSameMinuteSpanOpensAnHourAfter() {
        val start = ZonedDateTime.of(sat, LocalTime.of(10, 0, 10), london)
        val w = EventWhen.of(EventSpan.Timed(start, start.plusSeconds(30)), london)
        assertThat(w.clocks()).isEqualTo(at(10) to at(11))
    }

    @Test
    fun aSpanStartingInTheSpringGapNeverEndsBeforeItsStart() {
        val day = LocalDate.of(2027, 3, 28)
        val span = timed(at(1, 30), at(2), date = day).span(london) as EventSpan.Timed
        assertThat(span.end.isAfter(span.start)).isTrue()
        assertThat(Duration.between(span.start, span.end).toMinutes()).isEqualTo(30)
    }

    @Test
    fun aStartsStepThatPushesTheEndPastSixtyDaysIsReportedByTheCap() {
        val w = timed(at(0, 30), at(1, 30), endDays = 59)
        assertThat(w.capError).isNull()
        assertThat(w.stepStart(ClockField.Hour, up = false).capError).isEqualTo(UNTIL_TOO_LATE)
    }

    @Test
    fun aMidnightEndOverSeveralDaysRoundTripsAllDay() {
        val w = timed(at(22), at(0), endDays = 3)
        assertThat(w.withAllDay(true).endDays).isEqualTo(2)
        assertThat(w.withAllDay(true).withAllDay(false)).isEqualTo(w)
    }

    @Test
    fun aTimedSpanOverTheClockChangeKeepsItsWallTimes() {
        val w = timed(at(18), at(16), endDays = 2, date = fri23Oct)
        val span = w.span(london) as EventSpan.Timed
        assertThat(span.start.offset.id).isEqualTo("+01:00")
        assertThat(span.end.offset.id).isEqualTo("Z")
        assertThat(span.start.toLocalTime()).isEqualTo(at(18))
        assertThat(span.end.toLocalTime()).isEqualTo(at(16))
        assertThat(span.end.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 25))
        assertThat(EventWhen.of(span, london)).isEqualTo(w)
    }

    @Test
    fun anAllDaySpanIsWrittenWithTheDayAfter() {
        val w = EventWhen(true, LocalDate.of(2026, 10, 24), at(9), 1, at(10))
        assertThat(w.span(london)).isEqualTo(EventSpan.AllDay(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26)))
        assertThat(timed(at(9), at(10), date = null).span(london)).isNull()
    }

    @Test
    fun anAmbiguousClockTimeTakesTheEarlierOffset() {
        val w = timed(at(1, 30), at(2, 30), date = LocalDate.of(2026, 10, 25))
        val span = w.span(london) as EventSpan.Timed
        assertThat(span.start.offset.id).isEqualTo("+01:00")
    }

    @Test
    fun aChangeOpensOnTheEventsOwnValues() {
        val start = ZonedDateTime.of(fri23Oct, at(18), london)
        val w = EventWhen.of(EventSpan.Timed(start, ZonedDateTime.of(LocalDate.of(2026, 10, 25), at(16), london)), london)
        assertThat(w).isEqualTo(timed(at(18), at(16), endDays = 2, date = fri23Oct))
        val allDay = EventWhen.of(EventSpan.AllDay(sat, sat.plusDays(3)), london)
        assertThat(allDay.allDay).isTrue()
        assertThat(allDay.startDate).isEqualTo(sat)
        assertThat(allDay.endDays).isEqualTo(2)
    }

    @Test
    fun anEndAtOrBeforeTheStartOpensAnHourAfter() {
        val start = ZonedDateTime.of(sat, at(10), london)
        val same = EventWhen.of(EventSpan.Timed(start, start), london)
        assertThat(same.clocks()).isEqualTo(at(10) to at(11))
        val before = EventWhen.of(EventSpan.Timed(start, start.minusHours(3)), london)
        assertThat(before.clocks()).isEqualTo(at(10) to at(11))
        assertThat(before.endDays).isEqualTo(0)
        val late = ZonedDateTime.of(sat, at(23, 30), london)
        val lateBefore = EventWhen.of(EventSpan.Timed(late, late.minusDays(1)), london)
        assertThat(lateBefore.endClock).isEqualTo(at(0, 30))
        assertThat(lateBefore.endDays).isEqualTo(1)
        val backwards = EventWhen.of(EventSpan.AllDay(sat, sat.minusDays(2)), london)
        assertThat(backwards.endDays).isEqualTo(0)
    }

    @Test
    fun providerTimesThatMixTimedAndAllDayReadByTheStartsKind() {
        val timedStart = EventTime.Timed(ZonedDateTime.of(sat, at(10), london).toInstant())
        val mixed = EventWhen.of(timedStart, EventTime.AllDay(sat), london)
        assertThat(mixed.allDay).isFalse()
        assertThat(mixed.clocks()).isEqualTo(at(10) to at(11))
        val farEnd = EventTime.Timed(ZonedDateTime.of(sat.plusDays(2), at(15), london).toInstant())
        val allDayStart = EventWhen.of(EventTime.AllDay(sat), farEnd, london)
        assertThat(allDayStart.allDay).isTrue()
        assertThat(allDayStart.endDays).isEqualTo(2)
        val midnightEnd = EventTime.Timed(ZonedDateTime.of(sat.plusDays(2), at(0), london).toInstant())
        assertThat(EventWhen.of(EventTime.AllDay(sat), midnightEnd, london).endDays).isEqualTo(1)
    }

    @Test
    fun withStartMovesTheEndWithIt() {
        val w = timed(at(10), at(11, 30)).withStart(at(23, 30))
        assertThat(w.clocks()).isEqualTo(at(23, 30) to at(1))
        assertThat(w.endDays).isEqualTo(1)
    }

    @Test
    fun theLastDayLeavesATimedMidnightEndOnTheDayBefore() {
        assertThat(timed(at(18), at(0), endDays = 1).lastDay).isEqualTo(sat)
        assertThat(timed(at(18), at(0), endDays = 2).lastDay).isEqualTo(sat.plusDays(1))
        assertThat(timed(at(18), at(1), endDays = 1).lastDay).isEqualTo(sat.plusDays(1))
        assertThat(timed(at(18), at(0), endDays = 1).withAllDay(true).lastDay).isEqualTo(sat)
        assertThat(timed(at(18), at(0), endDays = 1, date = null).lastDay).isNull()
    }
}
