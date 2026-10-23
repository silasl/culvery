package uk.co.siland.culvery.core.setup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.ui.PersonPalette

/** 4d-2 §7.2, §7.3: a failure names what wasn't saved; a toast ends with a full stop. */
class SetupCopyTest {
    @Test
    fun aFailureNamesWhatWasntSaved() {
        assertThat(couldNotSaveDetails("Sam")).isEqualTo("Couldn’t save Sam’s details — try again.")
        assertThat(couldNotSaveDetails(null)).isEqualTo("Couldn’t save this person — try again.")
        assertThat(couldNotMove("Sam")).isEqualTo("Couldn’t move Sam — try again.")
        assertThat(YOUR_DETAILS_NOT_SAVED).isEqualTo("Couldn’t save your details — try again.")
        assertThat(HOME_NOT_SAVED).isEqualTo("Couldn’t save the home location — try again.")
        assertThat(COULD_NOT_SAVE).isEqualTo("Couldn’t save that change — try again.")
        assertThat(COLOUR_TAKEN).isEqualTo("That colour is taken — choose another.")
    }

    @Test
    fun someoneCalledSuggestsAnotherName() {
        assertThat(someoneCalled("Sam")).isEqualTo("Someone is already called Sam. Try “Sam B” or a nickname.")
        assertThat(noPinYet("Sam")).isEqualTo("no PIN yet, so Sam can only look")
        assertThat(WHO_ELSE_LINE).isEqualTo("Everyone gets a colour on the calendar. A PIN lets them add events.")
    }

    @Test
    fun toastsEndWithAFullStop() {
        assertThat(removed("Sam")).isEqualTo("Sam removed.")
        assertThat(movedUp("Sam")).isEqualTo("Sam moved up.")
        assertThat(movedDown("Sam")).isEqualTo("Sam moved down.")
    }

    private val green = PersonPalette.colors[0]
    private val blue = PersonPalette.colors[1]
    private val pink = PersonPalette.colors[2]
    private val lime = PersonPalette.colors[3]
    private val orange = PersonPalette.colors[4]

    @Test
    fun takenLineNamesColoursAndPeopleInPaletteOrder() {
        assertThat(takenLine(emptyMap())).isNull()
        assertThat(takenLine(mapOf(green to "Alex"))).isEqualTo("Green is taken by Alex.")
        assertThat(takenLine(mapOf(blue to "Sam", green to "Alex"))).isEqualTo("Green and blue are taken by Alex and Sam.")
        assertThat(takenLine(mapOf(pink to "Mia", green to "Alex", blue to "Sam"))).isEqualTo("Green, blue and pink are taken by Alex, Sam and Mia.")
        assertThat(takenLine(mapOf(green to "Sam", blue to "Alex"))).isEqualTo("Green and blue are taken by Sam and Alex.")
        assertThat(takenLine(mapOf(green to "A", blue to "B", pink to "C", orange to "E", lime to "D")))
            .isEqualTo("Green, blue, pink, lime and orange are taken by A, B, C, D and E.")
    }

    @Test
    fun eachRoleSaysWhatItCanDo() {
        assertThat(Role.entries.map(::roleWhat)).containsExactly(
            "Can change settings and people", "Can add and change any event", "Can add their own events",
        ).inOrder()
    }
}
