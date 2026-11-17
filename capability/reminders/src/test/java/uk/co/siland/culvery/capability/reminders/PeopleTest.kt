package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class PeopleTest {
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)

    @Test
    fun aRemovedPersonDropsOff() {
        val household = listOf(alex, sam, mia)
        assertThat(forWho(listOf(mia.id, PersonId("gone"), alex.id), household)).containsExactly(alex, mia).inOrder()
    }

    @Test
    fun nobodyLeftShowsAsFamilys() {
        assertThat(forWho(listOf(PersonId("gone")), listOf(alex))).containsExactly(Person.Family)
        assertThat(forWho(listOf(PersonId.FAMILY), listOf(alex))).containsExactly(Person.Family)
        assertThat(forWho(listOf(PersonId.FAMILY, alex.id), listOf(alex))).containsExactly(Person.Family)
    }
}
