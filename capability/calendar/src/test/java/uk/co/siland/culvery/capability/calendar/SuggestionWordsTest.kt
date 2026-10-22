package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class SuggestionWordsTest {
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF111111)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF222222)
    private val mia = Person(PersonId("mia"), "Mia", 0xFF333333)
    private val household = listOf(alex, sam, mia)

    private fun day(month: Int, d: Int, year: Int = 2026) = LocalDate.of(year, month, d)

    private val weekly = "RRULE:FREQ=WEEKLY;INTERVAL=1"

    private fun guitar(forPerson: String? = "mia", rule: String? = weekly, remembered: LocalDate = day(9, 12)) = Suggestion(
        "Guitar lesson", forPerson, false, LocalTime.of(10, 0), Duration.ofMinutes(45), null, rule, day(9, 5).takeIf { rule != null }, remembered,
    )

    private fun oneOff(forPerson: String? = "family", start: LocalTime = LocalTime.of(10, 30), minutes: Long = 90) =
        Suggestion("Dentist", forPerson, false, start, Duration.ofMinutes(minutes), null, null, null, day(7, 11))

    private fun allDay(days: Int, forPerson: String? = "sam") =
        Suggestion("Half term", forPerson, true, null, null, days, null, null, day(7, 11))

    private fun series(rule: String?, remembered: LocalDate, first: LocalDate? = null) =
        Suggestion("Club", "family", false, LocalTime.of(10, 0), Duration.ofMinutes(60), null, rule, first, remembered)

    // muted lines

    @Test
    fun aWeeklySeriesNamesItsWeekdayAndTimeAndWhenItLastHappened() {
        assertThat(patternLine(guitar(), household, today)).isEqualTo("Mia · Saturdays 10:00–10:45 · last on Sat 12 Sep")
    }

    @Test
    fun aSeriesRememberedAfterTodayReadsNext() {
        assertThat(patternLine(guitar(remembered = day(10, 3)), household, today)).isEqualTo("Mia · Saturdays 10:00–10:45 · next on Sat 3 Oct")
    }

    @Test
    fun aSeriesRememberedTodayReadsLast() {
        assertThat(patternLine(guitar(remembered = today), household, today)).endsWith("last on Wed 23 Sep")
    }

    @Test
    fun anotherSeriesGivesItsWordingThenTheTime() {
        val fortnightly = "RRULE:FREQ=WEEKLY;INTERVAL=2"
        assertThat(patternLine(series(fortnightly, day(9, 12), first = day(9, 5)), household, today))
            .isEqualTo("Family · Every 2 weeks, on Saturday, 10:00–11:00 · last on Sat 12 Sep")
        assertThat(patternLine(series("RRULE:FREQ=DAILY;INTERVAL=1", day(9, 22)), household, today))
            .isEqualTo("Family · Every day, 10:00–11:00 · last on Tue 22 Sep")
    }

    @Test
    fun aOneOffIsItsDateAndStart() {
        assertThat(patternLine(oneOff(), household, today)).isEqualTo("Family · Sat 11 Jul, 10:30")
        assertThat(patternLine(allDay(1), household, today)).isEqualTo("Sam · Sat 11 Jul, all day")
        assertThat(patternLine(allDay(3), household, today)).isEqualTo("Sam · Sat 11 – Mon 13 Jul, all day")
    }

    @Test
    fun aSetNamesEveryoneInWhoLine() {
        assertThat(patternLine(oneOff("alex,mia,sam"), household, today)).startsWith("Alex, Sam +1 · ")
        assertThat(patternLine(oneOff("mia,sam"), household, today)).startsWith("Sam + Mia · ")
    }

    @Test
    fun whoLeftOutOfTheHouseholdIsDropped() {
        assertThat(patternLine(oneOff("sam,gone"), household, today)).startsWith("Sam · ")
    }

    @Test
    fun nobodyLeftReadsFamilyHereAndEveryoneInTheFillLine() {
        assertThat(patternLine(oneOff("gone"), household, today)).startsWith("Family · ")
        assertThat(fillLine(oneOff("gone"), household, childOnly = false)).isEqualTo("Fills in Everyone and 10:30–12:00")
        assertThat(patternLine(oneOff(null), household, today)).startsWith("Family · ")
    }

    // fill lines

    @Test
    fun theFillLineListsWhoTimeAndTheRepeat() {
        assertThat(fillLine(guitar(), household, childOnly = false)).isEqualTo("Fills in Mia, 10:00–10:45 and every Saturday")
        assertThat(fillLine(oneOff(), household, childOnly = false)).isEqualTo("Fills in Everyone and 10:30–12:00")
        assertThat(fillLine(allDay(3), household, childOnly = false)).isEqualTo("Fills in Sam and all day for 3 days")
        assertThat(fillLine(allDay(1), household, childOnly = false)).isEqualTo("Fills in Sam and all day")
    }

    @Test
    fun theFillLineWordsOtherRepeatsLowered() {
        val monthly = "RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA"
        assertThat(fillLine(series(monthly, day(9, 26), first = day(9, 26)), household, childOnly = false))
            .isEqualTo("Fills in Everyone, 10:00–11:00 and every month, on the 4th Saturday")
    }

    @Test
    fun aRepeatLongerThanItsGapIsNotFilled() {
        val daily = "RRULE:FREQ=DAILY;INTERVAL=1"
        val twoDays = Suggestion("Camp", "mia", true, null, null, 2, daily, day(9, 1), day(9, 2))
        assertThat(fillLine(twoDays, household, childOnly = false)).isEqualTo("Fills in Mia and all day for 2 days")
    }

    @Test
    fun aTimedEventPastMidnightGivesItsStartAndHowLong() {
        val sleepover = Suggestion("Sleepover", "mia", false, LocalTime.of(18, 0), Duration.ofHours(18), null, null, null, day(7, 11))
        assertThat(fillLine(sleepover, household, childOnly = false)).isEqualTo("Fills in Mia and 18:00 for 18 hours")
    }

    @Test
    fun aChildsFillLineLeavesOutWhoAndTheRepeat() {
        assertThat(fillLine(guitar(), household, childOnly = true)).isEqualTo("Fills in 10:00–10:45")
        assertThat(spokenSuggestion(guitar(), household, childOnly = true)).isEqualTo("Guitar lesson. Fills in 10:00 to 10:45")
        assertThat(fillLine(allDay(3), household, childOnly = true)).isEqualTo("Fills in all day for 3 days")
    }

    // spoken

    @Test
    fun theSpokenFormReadsToInsteadOfADash() {
        assertThat(spokenSuggestion(guitar(), household, childOnly = false))
            .isEqualTo("Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday")
    }

    // banners

    @Test
    fun theBannerNamesWhatWasCopied() {
        val all = setOf(CopiedRow.Who, CopiedRow.Starts, CopiedRow.Repeats)
        assertThat(bannerLine(guitar(), all, today)).isEqualTo(
            "Copied from Guitar lesson (last on Sat 12 Sep): who, time and repeats. Tap any row to change it. The date is never copied.",
        )
        assertThat(bannerLine(guitar(), setOf(CopiedRow.Starts), today))
            .isEqualTo("Copied from Guitar lesson (last on Sat 12 Sep): time. Tap any row to change it. The date is never copied.")
        assertThat(bannerLine(guitar(), setOf(CopiedRow.Who, CopiedRow.Starts), today)).contains(": who and time.")
    }

    @Test
    fun theBannerSaysNextForADateAfterToday() {
        assertThat(bannerLine(guitar(remembered = day(10, 3)), setOf(CopiedRow.Starts), today)).contains("(next on Sat 3 Oct)")
    }

    // quick picks (today is Wed 23 Sep 2026)

    @Test
    fun quickPicks() {
        fun picks(s: Suggestion) = quickPicksFor(s, today)
        assertThat(picks(guitar())).containsExactly(day(9, 26), day(10, 3), day(10, 10)).inOrder()
        assertThat(picks(series("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA", day(9, 26), first = day(9, 26))))
            .containsExactly(day(9, 26), day(10, 24), day(11, 28)).inOrder()
        assertThat(picks(series("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH", day(10, 29), first = day(10, 29))))
            .containsExactly(day(9, 24), day(10, 29), day(11, 26)).inOrder()
        val fortnightly = "RRULE:FREQ=WEEKLY;INTERVAL=2"
        assertThat(picks(series(fortnightly, day(9, 19), first = day(9, 19))))
            .containsExactly(day(10, 3), day(10, 17), day(10, 31)).inOrder()
        assertThat(picks(series(fortnightly, day(9, 19)))).containsExactly(day(10, 3), day(10, 17), day(10, 31)).inOrder()
        assertThat(picks(series("RRULE:FREQ=YEARLY;INTERVAL=1", day(2, 29, 2024)))).containsExactly(day(2, 29, 2028))
        assertThat(picks(series("RRULE:FREQ=DAILY;INTERVAL=1", day(9, 22)))).isEmpty()
        assertThat(picks(series(null, day(9, 22)))).isEmpty()
    }

    @Test
    fun aWeeklyPickIncludesToday() {
        assertThat(quickPicksFor(guitar(remembered = day(9, 16)).copy(seriesStart = day(9, 2)), today).first()).isEqualTo(today)
    }
}
