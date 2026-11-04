package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BinInksTest {
    @Test
    fun sevenInksWithUniqueIds() {
        assertThat(BinInks.all.map { it.id })
            .containsExactly("blue", "black", "brown", "green", "grey", "yellow", "purple").inOrder()
    }

    @Test
    fun colourPicksNightOnADarkSet() {
        val black = BinInks.byId("black")!!
        assertThat(BinInks.colour(black, DarkColors)).isEqualTo(black.night)
        assertThat(BinInks.colour(black, LightColors)).isEqualTo(black.day)
    }

    @Test
    fun noInkIsMute() {
        assertThat(BinInks.colour(null, DarkColors)).isEqualTo(DarkColors.mute)
    }
}
