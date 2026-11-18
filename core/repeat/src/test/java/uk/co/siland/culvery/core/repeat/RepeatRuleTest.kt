package uk.co.siland.culvery.core.repeat

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/** 22 §10: the dates of a repeat rule, worked out in one place. */
class RepeatRuleTest {
    private val london: ZoneId = ZoneId.of("Europe/London")

    // Wednesday 18 November 2026.
    private val wednesday = LocalDate.of(2026, 11, 18)

    private fun rule(text: String, zone: ZoneId = london) = checkNotNull(RepeatRule.parse(text, zone)) { "parse" }

    private fun dates(text: String, first: LocalDate, take: Int, zone: ZoneId = london) = rule(text, zone).dates(first).take(take).toList()

    private fun d(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    private fun assertAgrees(choice: RepeatChoice, first: LocalDate, nth: Int = nthWeekday(first)) {
        val text = checkNotNull(recurrenceRule(choice, RepeatEnd.Never, first, nth))
        assertThat(dates(text, first, 6)).isEqualTo(occurrences(choice, first, 6, nth))
    }

    @Test
    fun eachChoiceGivesItsDates() {
        assertAgrees(RepeatChoice.Daily, d(2026, 9, 26))
        assertAgrees(RepeatChoice.Weekly, d(2026, 9, 26))
        assertAgrees(RepeatChoice.Fortnightly, d(2026, 9, 26))
        assertAgrees(RepeatChoice.MonthlyOnWeekday, d(2026, 9, 26))
        assertAgrees(RepeatChoice.MonthlyOnWeekday, d(2026, 10, 29), nth = LAST)
        assertAgrees(RepeatChoice.Monthly, d(2026, 10, 31))
        assertAgrees(RepeatChoice.Yearly, d(2028, 2, 29))
    }

    @Test
    fun weeklyOnSeveralDaysGivesEachDayInOrder() {
        assertThat(dates("RRULE:FREQ=WEEKLY;BYDAY=MO,WE", wednesday, 4))
            .containsExactly(d(2026, 11, 18), d(2026, 11, 23), d(2026, 11, 25), d(2026, 11, 30)).inOrder()
        assertThat(dates("RRULE:FREQ=WEEKLY;BYDAY=WE,MO", wednesday, 2)).containsExactly(d(2026, 11, 18), d(2026, 11, 23)).inOrder()
    }

    @Test
    fun aWeeklyRuleWithoutDaysIsOnTheFirstDaysWeekday() {
        assertThat(dates("RRULE:FREQ=WEEKLY", wednesday, 2)).containsExactly(d(2026, 11, 18), d(2026, 11, 25)).inOrder()
    }

    @Test
    fun everyTwoWeeksOnSeveralDaysSkipsTheOddWeeks() {
        assertThat(dates("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", wednesday, 4))
            .containsExactly(d(2026, 11, 18), d(2026, 11, 30), d(2026, 12, 2), d(2026, 12, 14)).inOrder()
    }

    @Test
    fun aCountRunsAcrossTheDays() {
        assertThat(rule("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=3").dates(wednesday).toList())
            .containsExactly(d(2026, 11, 18), d(2026, 11, 23), d(2026, 11, 25)).inOrder()
    }

    @Test
    fun everyThreeMonthsByDate() {
        assertThat(dates("RRULE:FREQ=MONTHLY;INTERVAL=3", wednesday, 3))
            .containsExactly(d(2026, 11, 18), d(2027, 2, 18), d(2027, 5, 18)).inOrder()
    }

    @Test
    fun aMonthlyRuleOnADayOfTheMonthUsesThatDay() {
        assertThat(dates("RRULE:FREQ=MONTHLY;BYMONTHDAY=20", wednesday, 2)).containsExactly(d(2026, 11, 20), d(2026, 12, 20)).inOrder()
        assertThat(dates("RRULE:FREQ=MONTHLY;BYMONTHDAY=10", wednesday, 2)).containsExactly(d(2026, 12, 10), d(2027, 1, 10)).inOrder()
    }

    @Test
    fun monthlyOnThe31stSkipsShortMonths() {
        assertThat(dates("RRULE:FREQ=MONTHLY", d(2026, 10, 31), 4))
            .containsExactly(d(2026, 10, 31), d(2026, 12, 31), d(2027, 1, 31), d(2027, 3, 31)).inOrder()
    }

    @Test
    fun yearlyOn29FebruarySkipsOtherYears() {
        assertThat(dates("RRULE:FREQ=YEARLY", d(2028, 2, 29), 3)).containsExactly(d(2028, 2, 29), d(2032, 2, 29), d(2036, 2, 29)).inOrder()
    }

    @Test
    fun untilADateIsInclusive() {
        assertThat(rule("RRULE:FREQ=DAILY;UNTIL=20261120").dates(wednesday).toList())
            .containsExactly(d(2026, 11, 18), d(2026, 11, 19), d(2026, 11, 20)).inOrder()
    }

    @Test
    fun untilAUtcTimeIsReadInTheZone() {
        val text = "RRULE:FREQ=DAILY;UNTIL=20261119T120000Z"
        val auckland = ZoneId.of("Pacific/Auckland")
        assertThat(rule(text, auckland).dates(wednesday).last()).isEqualTo(d(2026, 11, 20))
        assertThat(rule(text, ZoneOffset.UTC).dates(wednesday).last()).isEqualTo(d(2026, 11, 19))
    }

    @Test
    fun theLatestOnOrBeforeAndTheFirstAfter() {
        val weekly = rule("RRULE:FREQ=WEEKLY")
        assertThat(weekly.latestOnOrBefore(wednesday, d(2026, 11, 25))).isEqualTo(d(2026, 11, 25))
        assertThat(weekly.latestOnOrBefore(wednesday, d(2026, 11, 24))).isEqualTo(d(2026, 11, 18))
        assertThat(weekly.latestOnOrBefore(wednesday, d(2026, 11, 17))).isNull()
        assertThat(weekly.firstAfter(wednesday, d(2026, 11, 17))).isEqualTo(d(2026, 11, 18))
        assertThat(weekly.firstAfter(wednesday, d(2026, 11, 18))).isEqualTo(d(2026, 11, 25))
        assertThat(weekly.firstAfter(wednesday, d(2026, 11, 24))).isEqualTo(d(2026, 11, 25))
    }

    @Test
    fun theLatestAndFirstAfterHonourCountAndUntil() {
        val counted = rule("RRULE:FREQ=WEEKLY;COUNT=3")
        assertThat(counted.latestOnOrBefore(wednesday, d(2026, 12, 31))).isEqualTo(d(2026, 12, 2))
        assertThat(counted.firstAfter(wednesday, d(2026, 12, 2))).isNull()
        val ended = rule("RRULE:FREQ=WEEKLY;UNTIL=20261201")
        assertThat(ended.latestOnOrBefore(wednesday, d(2027, 6, 1))).isEqualTo(d(2026, 11, 25))
        assertThat(ended.firstAfter(wednesday, d(2026, 11, 25))).isNull()
    }

    @Test
    fun theLatestAndFirstAfterAgreeWithTheDatesAcrossEveryKindOfRule() {
        val first = d(2026, 10, 31)
        val texts = listOf(
            "RRULE:FREQ=DAILY;INTERVAL=3",
            "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,SA",
            "RRULE:FREQ=MONTHLY",
            "RRULE:FREQ=MONTHLY;INTERVAL=2;BYDAY=-1TH",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=29",
            "RRULE:FREQ=YEARLY;INTERVAL=2",
        )
        for (text in texts) {
            val all = rule(text).dates(first).take(500).toList()
            for (offset in 0L..400L) {
                val day = first.minusDays(3).plusDays(offset)
                assertThat(rule(text).latestOnOrBefore(first, day)).isEqualTo(all.lastOrNull { !it.isAfter(day) })
                assertThat(rule(text).firstAfter(first, day)).isEqualTo(all.firstOrNull { it.isAfter(day) })
            }
        }
    }

    @Test
    fun aDailyRuleTenYearsOnStillFindsTodaysDate() {
        val daily = rule("RRULE:FREQ=DAILY")
        val first = d(2016, 11, 18)
        assertThat(daily.latestOnOrBefore(first, wednesday)).isEqualTo(wednesday)
        assertThat(daily.firstAfter(first, wednesday)).isEqualTo(d(2026, 11, 19))
        assertThat(rule("RRULE:FREQ=WEEKLY;BYDAY=MO,WE").firstAfter(first.minusDays(1), wednesday)).isEqualTo(d(2026, 11, 23))
    }

    @Test
    fun aRuleStartedLaterHasItsCountReduced() {
        val text = "RRULE:FREQ=WEEKLY;COUNT=10"
        assertThat(startedFrom(text, wednesday, d(2026, 12, 16), london)).isEqualTo(d(2026, 12, 16) to "RRULE:FREQ=WEEKLY;COUNT=6")
        assertThat(startedFrom(text, wednesday, d(2026, 12, 17), london)).isEqualTo(d(2026, 12, 23) to "RRULE:FREQ=WEEKLY;COUNT=5")
        assertThat(startedFrom(text, wednesday, d(2027, 1, 20), london)).isEqualTo(d(2027, 1, 20) to null)
        assertThat(startedFrom(text, wednesday, d(2027, 1, 21), london)).isNull()
        assertThat(startedFrom(text, wednesday, d(2026, 11, 1), london)).isEqualTo(wednesday to text)
    }

    @Test
    fun aRuleStartedLaterKeepsUntilAndTheRest() {
        val text = "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231"
        assertThat(startedFrom(withSeriesStart(text, wednesday), wednesday, d(2026, 11, 24), london)).isEqualTo(d(2026, 11, 25) to text)
        assertThat(startedFrom(text, wednesday, d(2027, 1, 4), london)).isNull()
    }

    @Test
    fun aRuleItCannotWorkOutIsRefused() {
        val refused = listOf(
            "RRULE:FREQ=MONTHLY;BYDAY=MO,TU;BYSETPOS=1",
            "RRULE:FREQ=YEARLY;BYMONTH=3",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=1,15",
            "RRULE:FREQ=HOURLY",
            "RRULE:FREQ=DAILY;INTERVAL=0",
            "RRULE:FREQ=MONTHLY;BYDAY=5SA",
            "RRULE:FREQ=WEEKLY;WKST=SU",
            "RRULE:FREQ=DAILY;UNTIL=soon",
            "",
        )
        for (text in refused) {
            assertThat(RepeatRule.parse(text, london)).isNull()
            assertThat(startedFrom(text, wednesday, wednesday, london)).isNull()
        }
    }

    @Test
    fun aLenientParseIgnoresWhatItDoesNotKnow() {
        val lenient = checkNotNull(RepeatRule.parse("RRULE:FREQ=DAILY;INTERVAL=0;BYSETPOS=1;WKST=SU;COUNT=0", london, strict = false))
        assertThat(lenient.dates(wednesday).take(2).toList()).containsExactly(d(2026, 11, 18), d(2026, 11, 19)).inOrder()
        assertThat(RepeatRule.parse("RRULE:FREQ=DAILY;COUNT=0", london)).isNotNull()
    }

    @Test
    fun aRuleNeverPrintsItself() {
        assertThat(rule("RRULE:FREQ=WEEKLY;BYDAY=MO,WE").toString()).doesNotContain("MO")
    }
}
