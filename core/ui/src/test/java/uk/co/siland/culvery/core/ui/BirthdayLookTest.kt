package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BirthdayLookTest {
    @Test
    fun everyPersonColourHasALook() {
        PersonPalette.colors.forEach { colour ->
            val look = birthdayLook(colour)
            assertThat(look).isNotNull()
            look!!
            assertThat(look.night.isLight).isFalse()
            assertThat(look.day.isLight).isTrue()
            listOf(look.night to DarkColors, look.day to LightColors).forEach { (set, base) ->
                assertThat(set.weather).isEqualTo(base.weather)
                assertThat(set.rain).isEqualTo(base.rain)
                assertThat(set.ink).isEqualTo(base.ink)
                assertThat(set.mute).isEqualTo(base.mute)
                assertThat(set.surf).isEqualTo(base.surf)
                assertThat(set.surf3).isEqualTo(base.surf3)
                assertThat(set.border).isEqualTo(base.border)
            }
        }
    }

    @Test
    fun aColourOutsideTheSixHasNoLook() {
        assertThat(birthdayLook(0xFF123456)).isNull()
    }

    @Test
    fun theLookHasNoCornerAndNoSound() {
        PersonPalette.colors.forEach { colour ->
            assertThat(birthdayLook(colour)!!.cardCorner).isNull()
            assertThat(birthdayLook(colour)!!.sound).isNull()
        }
    }

    @Test
    fun aBirthdayPrintsNoName() {
        assertThat(Birthday(PersonPalette.colors[0], listOf("Sam")).toString()).doesNotContain("Sam")
    }
}
