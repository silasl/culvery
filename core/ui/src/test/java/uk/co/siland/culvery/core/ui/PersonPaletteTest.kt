package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.sqrt
import org.junit.Test

/** `:core:household`'s FAMILY_COLOR, which `:core:ui` can't see. */
internal const val FAMILY_VIOLET = 0xFF9C7CE3

/** The smallest RGB distance allowed between two person colours, or one and Family's; the closest pair is about 72. */
private const val MIN_DISTANCE = 60.0

class PersonPaletteTest {
    private fun channels(c: Long) = listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    private fun distance(a: Long, b: Long): Double =
        sqrt(channels(a).zip(channels(b)).sumOf { (x, y) -> ((x - y) * (x - y)).toDouble() })

    @Test
    fun sixDistinctColoursStartingWithTheHandOffsThree() {
        assertThat(PersonPalette.colors).hasSize(6)
        assertThat(PersonPalette.colors.toSet()).hasSize(6)
        assertThat(PersonPalette.colors.take(3)).containsExactly(0xFF4CB387, 0xFF5B9BE0, 0xFFE07BA8).inOrder()
    }

    @Test
    fun everyColourHasItsNameInOrderAndFamilyIsViolet() {
        assertThat(PersonPalette.colors.map { PersonPalette.nameOf(it) })
            .containsExactly("Green", "Blue", "Pink", "Lime", "Orange", "Yellow").inOrder()
        assertThat(PersonPalette.nameOf(0xFF123456)).isNull()
        assertThat(FAMILY_COLOUR_NAME).isEqualTo("Violet")
    }

    @Test
    fun everyColourIsFarFromEveryOtherAndFromFamily() {
        val all = PersonPalette.colors + FAMILY_VIOLET
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                assertWithMessage("%s and %s", all[i].toString(16), all[j].toString(16))
                    .that(distance(all[i], all[j]))
                    .isAtLeast(MIN_DISTANCE)
            }
        }
    }

    /** 4e: yellow for the grey slate, orange for the red; brown gone, and violet is Family's now. */
    @Test
    fun theUsersFourESwapsStay() {
        assertThat(PersonPalette.colors).containsAtLeast(0xFFCEC018, 0xFFE04A26)
        assertThat(PersonPalette.colors).containsNoneOf(0xFF8B96A8, 0xFFE06666, 0xFFA1785A, FAMILY_VIOLET)
    }

    @Test
    fun firstFreeSkipsTakenColoursAndIsNullOnceAllAreTaken() {
        assertThat(PersonPalette.firstFree(emptyList())).isEqualTo(0xFF4CB387)
        assertThat(PersonPalette.firstFree(PersonPalette.colors.take(2))).isEqualTo(0xFFE07BA8)
        assertThat(PersonPalette.firstFree(PersonPalette.colors)).isNull()
    }
}
