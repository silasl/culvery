package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.sqrt
import org.junit.Test

/** `:core:household`'s FAMILY_COLOR, which `:core:ui` can't see. */
internal const val FAMILY_AMBER = 0xFFE0A85B

/** The smallest RGB distance allowed between two person colours, or one and Family's; the closest pair is about 66. */
private const val MIN_DISTANCE = 60.0

class PersonPaletteTest {
    private fun channels(c: Long) = listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    private fun distance(a: Long, b: Long): Double =
        sqrt(channels(a).zip(channels(b)).sumOf { (x, y) -> ((x - y) * (x - y)).toDouble() })

    @Test
    fun eightDistinctColoursStartingWithTheHandOffsThree() {
        assertThat(PersonPalette.colors).hasSize(8)
        assertThat(PersonPalette.colors.toSet()).hasSize(8)
        assertThat(PersonPalette.colors.take(3)).containsExactly(0xFF4CB387, 0xFF5B9BE0, 0xFFE07BA8).inOrder()
    }

    @Test
    fun everyColourHasItsNameInOrderAndFamilyIsAmber() {
        assertThat(PersonPalette.colors.map { PersonPalette.nameOf(it) })
            .containsExactly("Green", "Blue", "Pink", "Violet", "Lime", "Red", "Yellow", "Brown").inOrder()
        assertThat(PersonPalette.nameOf(0xFF123456)).isNull()
        assertThat(FAMILY_COLOUR_NAME).isEqualTo("Amber")
    }

    @Test
    fun everyColourIsFarFromEveryOtherAndFromFamily() {
        val all = PersonPalette.colors + FAMILY_AMBER
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                assertWithMessage("%s and %s", all[i].toString(16), all[j].toString(16))
                    .that(distance(all[i], all[j]))
                    .isAtLeast(MIN_DISTANCE)
            }
        }
    }

    /** 4e: the user swapped the grey slate for a yellow; Family's amber stays the nearest colour to it. */
    @Test
    fun theSeventhColourIsYellowNotSlate() {
        assertThat(PersonPalette.colors[6]).isEqualTo(0xFFCEC018)
        assertThat(PersonPalette.colors).doesNotContain(0xFF8B96A8)
    }

    @Test
    fun firstFreeSkipsTakenColoursAndIsNullOnceAllAreTaken() {
        assertThat(PersonPalette.firstFree(emptyList())).isEqualTo(0xFF4CB387)
        assertThat(PersonPalette.firstFree(PersonPalette.colors.take(2))).isEqualTo(0xFFE07BA8)
        assertThat(PersonPalette.firstFree(PersonPalette.colors)).isNull()
    }
}
