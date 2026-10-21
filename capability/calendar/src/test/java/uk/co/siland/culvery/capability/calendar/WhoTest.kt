package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class WhoTest {
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val order = listOf(alex.id, sam.id, mia.id)

    private fun household(vararg people: Person) = (listOf(Person.Family) + people).associateBy { it.id }

    @Test
    fun aSingleIdReadsAsThatPerson() {
        assertThat(peopleIn("sam")).containsExactly(sam.id)
        assertThat(peopleIn(null)).isEmpty()
        assertThat(peopleIn("")).isEmpty()
    }

    @Test
    fun aListReadsInTheOrderWritten() {
        assertThat(peopleIn("sam,mia")).containsExactly(sam.id, mia.id).inOrder()
        assertThat(peopleIn(" sam , mia ,")).containsExactly(sam.id, mia.id).inOrder()
        assertThat(peopleIn("sam,sam")).containsExactly(sam.id)
    }

    @Test
    fun familyIsDroppedFromAMixedList() {
        assertThat(peopleIn("sam,family")).containsExactly(sam.id)
        assertThat(peopleIn("family")).containsExactly(PersonId.FAMILY)
    }

    @Test
    fun aTagIsInTheHouseholdsOrder() {
        assertThat(tagFor(listOf(mia.id, sam.id), order)).isEqualTo("sam,mia")
    }

    @Test
    fun aTagNamesEachPersonOnce() {
        assertThat(tagFor(listOf(mia.id, sam.id, mia.id), order)).isEqualTo("sam,mia")
    }

    @Test
    fun familyIsNeverMixed() {
        assertThrows(IllegalArgumentException::class.java) { tagFor(listOf(PersonId.FAMILY, sam.id), order) }
        assertThrows(IllegalArgumentException::class.java) { tagFor(emptyList(), order) }
        assertThat(tagFor(listOf(PersonId.FAMILY), order)).isEqualTo("family")
    }

    @Test
    fun aRemovedPersonIsDroppedAndOneLeftMakesItTheirs() {
        assertThat(resolvePeople("sam,mia", PersonId.FAMILY, household(alex, mia), order)).containsExactly(mia)
    }

    /** 9 §3.4: on a calendar mapped to Alex, the one left is still the event's person, not Alex. */
    @Test
    fun aSharedTagWithOneLeftIsTheirsWhateverTheCalendar() {
        assertThat(resolvePeople("sam,mia", alex.id, household(alex, mia), order)).containsExactly(mia)
    }

    /** 9 §3.3: a single id reads as today, so one naming someone removed is the calendar's person. */
    @Test
    fun aSingleIdNamingSomeoneRemovedIsTheCalendarsPerson() {
        assertThat(resolvePeople("sam", alex.id, household(alex, mia), order)).containsExactly(alex)
        assertThat(resolvePeople("sam", PersonId("removed"), household(alex, mia), order)).containsExactly(Person.Family)
    }

    @Test
    fun aBlankTagIsUntagged() {
        assertThat(resolvePeople(" ", alex.id, household(alex, sam), order)).containsExactly(alex)
        assertThat(resolvePeople(",", alex.id, household(alex, sam), order)).containsExactly(alex)
    }

    @Test
    fun aTagWithNobodyLeftIsFamily() {
        assertThat(resolvePeople("sam,mia", alex.id, household(alex), order)).containsExactly(Person.Family)
        assertThat(resolvePeople("x,y", alex.id, household(alex, sam, mia), order)).containsExactly(Person.Family)
    }

    /** Pin. */
    @Test
    fun anUntaggedEventIsTheCalendarsPerson() {
        assertThat(resolvePeople(null, alex.id, household(alex, sam, mia), order)).containsExactly(alex)
    }

    @Test
    fun resolvedPeopleFollowTheHouseholdOrder() {
        assertThat(resolvePeople("mia,alex", PersonId.FAMILY, household(alex, sam, mia), order)).containsExactly(alex, mia).inOrder()
    }

    @Test
    fun theWhoLine() {
        val anotherMia = mia.copy(id = PersonId("mia-2"))
        assertThat(whoLine(listOf(sam))).isEqualTo("Sam")
        assertThat(whoLine(listOf(sam, mia))).isEqualTo("Sam + Mia")
        assertThat(whoLine(listOf(alex, sam, mia))).isEqualTo("Alex, Sam +1")
        assertThat(whoLine(listOf(alex, sam, mia, anotherMia))).isEqualTo("Alex, Sam +2")
    }

    @Test
    fun theSpokenWho() {
        assertThat(spokenWho(listOf(sam))).isEqualTo("Sam")
        assertThat(spokenWho(listOf(sam, mia))).isEqualTo("Sam and Mia")
        assertThat(spokenWho(listOf(alex, sam, mia))).isEqualTo("Alex, Sam and Mia")
    }
}
