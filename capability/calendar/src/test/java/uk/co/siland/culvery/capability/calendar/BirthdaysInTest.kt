package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId

class BirthdaysInTest {
    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.of(2026, 3, 12)
    private val alex = PersonId("alex")
    private val sam = PersonId("sam")
    private val mia = PersonId("mia")
    private val order = listOf(alex, sam, mia)
    private val family = PersonId.FAMILY

    private fun yearly(
        title: String,
        forPerson: String?,
        sourcePerson: PersonId = family,
        first: LocalDate = date,
        days: Long = 1,
        rule: String? = "RRULE:FREQ=YEARLY",
        recurring: Boolean = true,
    ) = StoredEvent(
        connectionId = "c1", sourceId = "s1", remoteId = "$title-$forPerson-$first",
        title = title,
        start = EventTime.AllDay(first), end = EventTime.AllDay(first.plusDays(days)),
        recurring = recurring, forPerson = forPerson, createdBy = null, sourcePerson = sourcePerson,
        startSort = 0, endSort = 0, recurrenceRule = rule,
    )

    private fun oneOff(title: String, forPerson: String?) = yearly(title, forPerson, rule = null, recurring = false)

    private fun timed(title: String, forPerson: String?): StoredEvent {
        val at = date.atTime(14, 0).atZone(zone).toInstant()
        return yearly(title, forPerson).copy(start = EventTime.Timed(at), end = EventTime.Timed(at.plusSeconds(3600)))
    }

    private fun on(vararg events: StoredEvent) = birthdaysIn(events.toList(), date, order, zone)

    @Test
    fun aYearlyBirthdayTaggedForSamIsSams() {
        assertThat(on(yearly("Sam’s birthday", "sam"))).containsExactly(sam)
    }

    @Test
    fun aOneOffBirthdayGreetsNobody() {
        assertThat(on(oneOff("Sam’s birthday", "sam"))).isEmpty()
    }

    @Test
    fun aMultiDayBirthdayGreetsNobody() {
        assertThat(on(yearly("Sam’s birthday", "sam", days = 2))).isEmpty()
        assertThat(on(yearly("Sam’s birthday", "sam", first = date.minusDays(1), days = 2))).isEmpty()
    }

    @Test
    fun onlyAYearlyRuleCounts() {
        assertThat(on(yearly("Sam’s birthday", "sam", rule = "RRULE:FREQ=WEEKLY"))).isEmpty()
        assertThat(on(yearly("Sam’s birthday", "sam", rule = "DTSTART:20200312\nRRULE:FREQ=YEARLY"))).containsExactly(sam)
        assertThat(on(yearly("Sam’s birthday", "sam", rule = null))).containsExactly(sam)
    }

    @Test
    fun aSeriesStartLineBeforeTheRuleStillCounts() {
        assertThat(on(yearly("Sam’s birthday", "sam", rule = "DTSTART:20200312\nRRULE:FREQ=YEARLY;INTERVAL=1"))).containsExactly(sam)
    }

    @Test
    fun onlyTheWholeWordCounts() {
        for (title in listOf("Birthday", "birthday!", "BIRTHDAYS")) {
            assertThat(on(yearly(title, "sam"))).containsExactly(sam)
        }
        for (title in listOf("bday", "Birthdayparty")) {
            assertThat(on(yearly(title, "sam"))).isEmpty()
        }
    }

    @Test
    fun theTagDecides() {
        assertThat(on(yearly("Sam’s birthday", null, sourcePerson = sam))).isEmpty()
        assertThat(on(yearly("Sam’s birthday", "family"))).isEmpty()
        assertThat(on(yearly("Sam’s birthday", "ghost,sam"))).containsExactly(sam)
        assertThat(on(yearly("Sam’s birthday", "sam,alex"))).containsExactly(alex, sam).inOrder()
    }

    @Test
    fun aTimedBirthdayPartyDoesntCount() {
        assertThat(on(timed("Sam’s birthday party", "sam"))).isEmpty()
    }

    @Test
    fun onlyTodayCounts() {
        assertThat(on(yearly("Sam’s birthday", "sam", first = date.minusDays(1)))).isEmpty()
        assertThat(on(yearly("Sam’s birthday", "sam", first = date.plusDays(1)))).isEmpty()
    }

    @Test
    fun twoEventsForSamAreOneBirthday() {
        assertThat(on(yearly("Sam’s birthday", "sam"), yearly("Birthday tea", "sam"))).containsExactly(sam)
    }

    /** Pin (16 §3.1): titles are never matched against names, so the tag's person is greeted whatever the title says. */
    @Test
    fun aBirthdayTitledForSomeoneElseGreetsTheTag() {
        assertThat(on(yearly("Leo’s birthday", "sam"))).containsExactly(sam)
    }
}
