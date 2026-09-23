package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class PersonResolutionTest {
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val people = listOf(Person.Family, alex, sam).associateBy { it.id }

    @Test
    fun taggedPersonWins() {
        assertThat(resolvePerson("sam", alex.id, people)).isEqualTo(sam)
    }

    @Test
    fun taggedFamilyWinsOverTheSourceMapping() {
        assertThat(resolvePerson("family", alex.id, people)).isEqualTo(Person.Family)
    }

    @Test
    fun unknownTagFallsBackToSourceMapping() {
        assertThat(resolvePerson("someone-removed", alex.id, people)).isEqualTo(alex)
    }

    @Test
    fun untaggedUsesSourceMapping() {
        assertThat(resolvePerson(null, sam.id, people)).isEqualTo(sam)
    }

    @Test
    fun sourceMappedToRemovedPersonShowsFamily() {
        assertThat(resolvePerson(null, PersonId("removed"), people)).isEqualTo(Person.Family)
    }
}
