package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BinColoursTest {
    private fun colours(): BinColours = testBinColours()

    @Test
    fun defaultsAreTheUsersBins() {
        val colours = colours()
        assertThat(BinKind.entries.map { colours.inkFor(it.key)?.id })
            .containsExactly("green", "brown", "blue", "black").inOrder()
    }

    @Test
    fun aChoiceOverridesTheDefault() = runTest {
        val colours = colours()
        colours.set("waste", "grey")
        assertThat(colours.inkFor("waste")?.id).isEqualTo("grey")
        assertThat(colours.inkFor("food")?.id).isEqualTo("green")
    }

    @Test
    fun anUnknownKindHasNoInk() {
        assertThat(colours().inkFor("Glass")).isNull()
    }
}
