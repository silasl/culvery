package uk.co.siland.culvery.core.setup

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE

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
}
