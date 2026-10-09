package uk.co.siland.culvery.core.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.PersonPalette

@RunWith(AndroidJUnit4::class)
class PersonFormTest {
    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false)

    @Test
    fun aSheetIsEditedOnlyWhileSomethingDiffersFromHowItOpened() {
        val form = PersonForm(sam, taken = emptyMap())
        assertThat(form.edited).isFalse()
        form.name = "Samuel"
        assertThat(form.edited).isTrue()
        form.name = "Sam"
        assertThat(form.edited).isFalse()
        form.choosePin("2468")
        assertThat(form.edited).isTrue()
    }
}
