package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
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
        colours.set("waste", "purple")
        assertThat(colours.inkFor("waste")?.id).isEqualTo("purple")
        assertThat(colours.inkFor("food")?.id).isEqualTo("green")
    }

    /** Grey was an ink until 2026-11-04; the neutral stripe now means unknown. */
    @Test
    fun aStoredGreyFallsBackToTheKindsDefault() = runTest {
        val colours = colours()
        colours.set("waste", "grey")
        colours.set("Glass", "grey")
        assertThat(colours.inkFor("waste")?.id).isEqualTo("black")
        assertThat(colours.inkFor("Glass")).isNull()
    }

    @Test
    fun rememberKeepsOnlyUnknownKinds() = runTest {
        val colours = colours()
        colours.remember(listOf("waste", "Glass"))
        colours.remember(listOf("Glass", "Textiles"))
        assertThat(colours.seen.first()).containsExactly("Glass", "Textiles")
        colours.forgetSeen()
        assertThat(colours.seen.first()).isEmpty()
    }

    @Test
    fun anUnknownKindHasNoInk() {
        assertThat(colours().inkFor("Glass")).isNull()
    }
}
