package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class PersonResolutionTest {
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val people = listOf(Person.Family, alex, sam).associateBy { it.id }
    private val order = listOf(alex.id, sam.id)

    @Test
    fun taggedPersonWins() {
        assertThat(resolvePeople("sam", alex.id, people, order)).containsExactly(sam)
    }

    @Test
    fun taggedFamilyWinsOverTheSourceMapping() {
        assertThat(resolvePeople("family", alex.id, people, order)).containsExactly(Person.Family)
    }

    /** 9 §3.3: a single id reads as it always has, so one naming someone removed is the calendar's person. */
    @Test
    fun unknownTagFallsBackToSourceMapping() {
        assertThat(resolvePeople("someone-removed", alex.id, people, order)).containsExactly(alex)
    }

    @Test
    fun untaggedUsesSourceMapping() {
        assertThat(resolvePeople(null, sam.id, people, order)).containsExactly(sam)
    }

    @Test
    fun sourceMappedToRemovedPersonShowsFamily() {
        assertThat(resolvePeople(null, PersonId("removed"), people, order)).containsExactly(Person.Family)
    }
}
