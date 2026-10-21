package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** The Day pane's dots (9 §3.1): today is Wednesday 23 September 2026 in London. */
class HeldTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val order = listOf(alex.id, sam.id, mia.id)
    private val colors = listOf(Person.Family, alex, sam, mia).associate { it.id to it.color }
    private val sat26 = LocalDate.of(2026, 9, 26)

    private fun event(title: String, day: LocalDate, vararg people: Person): HeldEvent {
        val start = LocalDateTime.of(day, LocalTime.of(10, 0)).atZone(london).toInstant()
        return HeldEvent(EventRef("c1", "s1", title), title, people.map { it.id }, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)))
    }

    private fun Held.dots(day: LocalDate) = dotsOn(day, colors, order, london)

    @Test
    fun dotsAreUpToThreeInHouseholdOrder() {
        val held = Held(
            listOf(event("Bins", sat26, Person.Family), event("Piano", sat26, mia), event("Swim", sat26, sam), event("Gym", sat26, alex)),
            lastShownDay(today), emptySet(), firstDay = today,
        )
        assertThat(held.dots(sat26)).containsExactly(alex.color, sam.color, mia.color).inOrder()
        val sun27 = sat26.plusDays(1)
        val withFamily = Held(listOf(event("Bins", sun27, Person.Family), event("Piano", sun27, mia)), lastShownDay(today), emptySet(), firstDay = today)
        assertThat(withFamily.dots(sun27)).containsExactly(mia.color, Person.Family.color).inOrder()
    }

    @Test
    fun aSharedEventGivesEachPersonADot() {
        val held = Held(listOf(event("Cinema", sat26, sam, mia)), lastShownDay(today), emptySet(), firstDay = today)
        assertThat(held.dots(sat26)).containsExactly(sam.color, mia.color).inOrder()
        assertThat(held.dots(sat26.plusDays(1))).isEmpty()
    }

    @Test
    fun noDotsPastTheHeldWindow() {
        val lastDay = lastShownDay(today)
        val held = Held(listOf(event("Piano", lastDay, mia), event("Piano", lastDay.plusDays(1), mia)), lastDay, emptySet(), firstDay = today)
        assertThat(held.dots(lastDay)).containsExactly(mia.color)
        assertThat(held.dots(lastDay.plusDays(1))).isEmpty()
    }
}
