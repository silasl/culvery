package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** Europe/London; today is Wednesday 23 September 2026; Mia's Piano is Thursday 1 October 15:30–16:15; checks are 45 minutes (6i). */
class ClashTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val people = listOf(Person.Family, alex, sam, mia).associateBy { it.id }
    private val order = listOf(alex.id, sam.id, mia.id)
    private val thu1 = LocalDate.of(2026, 10, 1)
    private val sat26 = LocalDate.of(2026, 9, 26)
    private val fortyFive = Duration.ofMinutes(45)

    private fun at(day: LocalDate, time: String) = LocalDateTime.of(day, LocalTime.parse(time)).atZone(london).toInstant()

    private fun timed(
        title: String,
        day: LocalDate,
        from: String,
        to: String,
        forPerson: String? = mia.id.value,
        sourcePerson: PersonId = PersonId.FAMILY,
        endDay: LocalDate = day,
    ): HeldEvent {
        val start = at(day, from)
        val end = at(endDay, to)
        return StoredEvent(
            "c1", "s1", title, title, EventTime.Timed(start), EventTime.Timed(end), recurring = false,
            forPerson = forPerson, createdBy = null, sourcePerson = sourcePerson,
            startSort = start.toEpochMilli(), endSort = end.toEpochMilli(),
        ).toHeld(people, listOf(PersonId.FAMILY) + order)
    }

    private val piano = timed("Piano", thu1, "15:30", "16:15")

    private fun held(vararg events: HeldEvent, lastDay: LocalDate = lastShownDay(today), unhealthy: Set<PersonId> = emptySet()) =
        Held(events.toList(), lastDay, unhealthy, firstDay = today)

    private fun Held.check(people: List<Person>, day: LocalDate, start: String, length: Duration = fortyFive, except: EventRef? = null) =
        check(people.map { it.id }, day, LocalTime.parse(start), length, london, except, order)

    private fun Held.busy(people: List<Person>, day: LocalDate, except: EventRef? = null) = busyHours(people.map { it.id }, day, london, except)

    @Test
    fun anOverlapIsABusyHour() {
        assertThat(held(piano).busy(listOf(mia), thu1)).containsExactly(15, 16)
    }

    @Test
    fun allDayAndFamilyEventsMakeNobodyBusy() {
        val start = thu1
        val allDay = StoredEvent(
            "c1", "s1", "Inset day", "Inset day", EventTime.AllDay(start), EventTime.AllDay(start.plusDays(1)), recurring = false,
            forPerson = mia.id.value, createdBy = null, sourcePerson = PersonId.FAMILY,
            startSort = at(start, "00:00").toEpochMilli(), endSort = at(start.plusDays(1), "00:00").toEpochMilli(),
        ).toHeld(people, order)
        val family = timed("Boiler service", thu1, "15:00", "16:00", forPerson = PersonId.FAMILY.value)
        val h = held(allDay, family)
        assertThat(h.busy(listOf(mia), thu1)).isEmpty()
        assertThat(h.check(listOf(mia), thu1, "15:00")).isEqualTo(FreeCheck.Free(listOf(mia.id)))
    }

    @Test
    fun aSharedEventMakesEachOfItsPeopleBusy() {
        val cinema = timed("Cinema", sat26, "19:00", "21:00", forPerson = "${sam.id.value},${mia.id.value}")
        val h = held(cinema)
        assertThat(h.busy(listOf(sam), sat26)).containsExactly(19, 20)
        assertThat(h.check(listOf(sam), sat26, "19:30")).isEqualTo(FreeCheck.Busy(sam.id, cinema, listOf(LocalTime.of(18, 15), LocalTime.of(21, 0))))
    }

    @Test
    fun aCalendarsPersonIsBusyWithItsUntaggedEvents() {
        val swim = timed("Swimming", sat26, "10:00", "11:00", forPerson = null, sourcePerson = mia.id)
        val h = held(swim)
        assertThat(h.busy(listOf(mia), sat26)).containsExactly(10)
        assertThat((h.check(listOf(mia), sat26, "10:00") as FreeCheck.Busy).event).isEqualTo(swim)
    }

    @Test
    fun theClashNamesTheFirstOverlap() {
        val tea = timed("Tea", thu1, "15:35", "15:50")
        val check = held(tea, piano).check(listOf(mia), thu1, "15:00")
        assertThat(check).isInstanceOf(FreeCheck.Busy::class.java)
        check as FreeCheck.Busy
        assertThat(check.person).isEqualTo(mia.id)
        assertThat(check.event).isEqualTo(piano)
    }

    @Test
    fun theFreeStartsAreTheNearestBeforeAndAfter() {
        val check = held(piano).check(listOf(mia), thu1, "15:00") as FreeCheck.Busy
        assertThat(check.freeStarts).containsExactly(LocalTime.of(14, 45), LocalTime.of(16, 15)).inOrder()
    }

    @Test
    fun aMultiDayTimedEventMakesTheDaysBetweenBusy() {
        val fri = LocalDate.of(2026, 10, 2)
        val camp = timed("Camp", fri, "18:00", "16:00", endDay = fri.plusDays(2))
        assertThat(held(camp).busy(listOf(mia), fri.plusDays(1))).isEqualTo((0..23).toSet())
    }

    @Test
    fun theCheckStopsAtMidnight() {
        val early = timed("Ferry", thu1.plusDays(1), "00:15", "01:00")
        assertThat(held(early).check(listOf(mia), thu1, "22:30", Duration.ofHours(2))).isEqualTo(FreeCheck.Free(listOf(mia.id)))
        val late = timed("Film", thu1, "23:45", "23:59")
        assertThat(held(late).check(listOf(mia), thu1, "22:30", Duration.ofHours(2))).isInstanceOf(FreeCheck.Busy::class.java)
    }

    @Test
    fun theClocksChangeKeepsLocalHours() {
        val sun25 = LocalDate.of(2026, 10, 25)
        val brunch = timed("Brunch", sun25, "10:00", "11:00")
        assertThat(held(brunch, lastDay = sun25).busy(listOf(mia), sun25)).containsExactly(10)
    }

    @Test
    fun noFreeStartBeforeSevenOrPastEleven() {
        val allDayLong = timed("Camp", sat26, "07:00", "23:00")
        assertThat((held(allDayLong).check(listOf(mia), sat26, "12:00") as FreeCheck.Busy).freeStarts).isEmpty()
        val toHalfNine = timed("Camp", sat26, "07:00", "21:30")
        assertThat((held(toHalfNine).check(listOf(mia), sat26, "12:00", Duration.ofHours(2)) as FreeCheck.Busy).freeStarts).isEmpty()
        // The control: 45 minutes from 21:30 ends by 23:00.
        assertThat((held(toHalfNine).check(listOf(mia), sat26, "12:00") as FreeCheck.Busy).freeStarts).containsExactly(LocalTime.of(21, 30))
    }

    @Test
    fun severalPeopleNameTheBusyOneInHouseholdOrder() {
        val dentist = timed("Dentist", thu1, "12:30", "13:00", forPerson = sam.id.value)
        val lunch = timed("Lunch", thu1, "12:00", "13:00")
        val check = held(lunch, dentist).check(listOf(mia, sam), thu1, "12:00") as FreeCheck.Busy
        assertThat(check.person).isEqualTo(sam.id)
        assertThat(check.event).isEqualTo(dentist)
    }

    @Test
    fun everyoneChecksNobody() {
        val family = timed("Boiler service", thu1, "15:00", "16:00", forPerson = PersonId.FAMILY.value)
        val h = held(piano, family)
        assertThat(h.check(listOf(Person.Family), thu1, "15:00")).isEqualTo(FreeCheck.None)
        assertThat(h.busy(listOf(Person.Family), thu1)).isEmpty()
        assertThat(h.check(emptyList(), thu1, "15:00")).isEqualTo(FreeCheck.None)
    }

    @Test
    fun aDayPastTheHeldWindowHasNoLine() {
        val lastDay = lastShownDay(today)
        val onLast = timed("Piano", lastDay, "15:30", "16:15")
        val after = timed("Piano next", lastDay.plusDays(1), "15:30", "16:15")
        val h = held(onLast, after)
        assertThat(h.check(listOf(mia), lastDay.plusDays(1), "15:00")).isEqualTo(FreeCheck.None)
        assertThat(h.busy(listOf(mia), lastDay.plusDays(1))).isEmpty()
        assertThat(h.check(listOf(mia), lastDay, "15:00")).isInstanceOf(FreeCheck.Busy::class.java)
    }

    @Test
    fun aDayBeforeTheHeldWindowHasNoLine() {
        val yesterday = timed("Piano", today.minusDays(1), "15:30", "16:15")
        val h = held(yesterday)
        assertThat(h.check(listOf(mia), today.minusDays(1), "15:00")).isEqualTo(FreeCheck.None)
        assertThat(h.busy(listOf(mia), today.minusDays(1))).isEmpty()
        assertThat(h.check(listOf(mia), today, "15:00")).isEqualTo(FreeCheck.Free(listOf(mia.id)))
    }

    @Test
    fun theEventBeingChangedDoesNotClashWithItself() {
        val h = held(piano)
        assertThat(h.check(listOf(mia), thu1, "15:45", except = piano.ref)).isEqualTo(FreeCheck.Free(listOf(mia.id)))
        assertThat(h.busy(listOf(mia), thu1, except = piano.ref)).isEmpty()
    }

    @Test
    fun aPersonWhoseCalendarNeedsSignInGetsNoLine() {
        val h = held(unhealthy = setOf(mia.id))
        assertThat(h.check(listOf(mia), sat26, "10:00")).isEqualTo(FreeCheck.None)
        assertThat(h.check(listOf(sam, mia), sat26, "10:00")).isEqualTo(FreeCheck.None)
        assertThat(h.check(listOf(sam), sat26, "10:00")).isEqualTo(FreeCheck.Free(listOf(sam.id)))
    }
}
