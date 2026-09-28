package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class DefaultMappingTest {
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val people = listOf(mia, sam)

    private fun mapped(name: String, shown: Boolean = true, primary: Boolean = false) =
        defaultMapping(CalendarSource(name, name, writable = false, shown = shown, primary = primary), people)

    @Test
    fun thePrimaryIsFamilyAndAlwaysVisibleWhateverItIsCalled() {
        assertThat(mapped("Mia", shown = false, primary = true)).isEqualTo(SourceMapping(PersonId.FAMILY, visible = true))
    }

    @Test
    fun aCalendarNamingOnePersonIsTheirs() {
        assertThat(mapped("Mia").person).isEqualTo(mia.id)
        assertThat(mapped("Mia's swimming").person).isEqualTo(mia.id)
        assertThat(mapped("Mia’s swimming").person).isEqualTo(mia.id)
        assertThat(mapped("swimming with SAM").person).isEqualTo(sam.id)
    }

    @Test
    fun twoPeopleOrNoneIsFamily() {
        assertThat(mapped("Mia & Sam football").person).isEqualTo(PersonId.FAMILY)
        assertThat(mapped("Bin days").person).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun aNameInsideAnotherWordDoesNotCount() {
        assertThat(mapped("Samantha's book club").person).isEqualTo(PersonId.FAMILY)
        assertThat(mapped("Miami trip").person).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun aPersonWithABlankNameNamesNoCalendar() {
        // An empty name would match between any two non-letters, here around the "&".
        val blank = Person(PersonId("blank"), "", 0xFF000000)
        assertThat(defaultMapping(CalendarSource("m", "Mia & friends", writable = false), listOf(mia, blank)).person).isEqualTo(mia.id)
        assertThat(defaultMapping(CalendarSource("b", "Bins & recycling", writable = false), listOf(blank)).person).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun visibilityFollowsTheTick() {
        assertThat(mapped("Mia", shown = false)).isEqualTo(SourceMapping(mia.id, visible = false))
        assertThat(mapped("Mia", shown = true)).isEqualTo(SourceMapping(mia.id, visible = true))
    }
}
