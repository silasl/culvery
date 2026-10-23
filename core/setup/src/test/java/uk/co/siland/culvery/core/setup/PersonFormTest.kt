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

    @Test
    fun refusalsDoNotCountAsEdits() {
        val form = PersonForm(sam, taken = emptyMap())
        form.refuse(PeopleOutcome.Refused(NEEDS_AN_ADMIN, PersonField.ROLE))
        assertThat(form.edited).isFalse()
        form.name = "Samuel"
        assertThat(form.refusal).isNull()
        assertThat(form.edited).isTrue()
        form.refuse(PeopleOutcome.Refused(COLOUR_TAKEN, PersonField.COLOUR))
        assertThat(form.edited).isTrue()
    }

    @Test
    fun aColourRefusalBlocksThatColourAndAnyChangeClearsTheRefusal() {
        val form = PersonForm(existing = null, taken = emptyMap())
        val chosen = form.color
        form.refuse(PeopleOutcome.Refused(COLOUR_TAKEN, PersonField.COLOUR))
        assertThat(form.taken.keys).containsExactly(chosen)
        assertThat(form.canSave).isFalse()
        form.color = PersonPalette.colors[2]
        assertThat(form.refusal).isNull()
        form.name = "Mia"
        assertThat(form.canSave).isTrue()
    }

    @Test
    fun everyRefusalCountsEvenAnIdenticalOne() {
        val form = PersonForm(sam, taken = emptyMap())
        val refused = PeopleOutcome.Refused(PIN_TAKEN, PersonField.PIN)
        form.refuse(refused)
        val first = form.refusals
        form.refuse(refused)
        assertThat(form.refusals).isEqualTo(first + 1)
    }

    @Test
    fun aNewPersonWithNoFreeColourStartsUnchosen() {
        val form = PersonForm(existing = null, taken = PersonPalette.colors.associateWith { "Someone" })
        assertThat(form.color).isNull()
        form.name = "Mia"
        assertThat(form.canSave).isFalse()
    }

    @Test
    fun aDraftNeverPrintsItsPin() {
        val draft = PersonDraft("Sam", PersonPalette.colors[1], Role.ADULT, newPin = "4821", removePin = false)
        assertThat(draft.toString()).doesNotContain("4821")
        assertThat(draft.toString()).contains("Sam")
    }
}
