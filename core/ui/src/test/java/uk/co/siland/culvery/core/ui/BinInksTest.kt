package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BinInksTest {
    @Test
    fun sixInksWithUniqueIds() {
        assertThat(BinInks.all.map { it.id })
            .containsExactly("blue", "black", "brown", "green", "yellow", "purple").inOrder()
    }

    @Test
    fun onlyNightBlackHasAnEdge() {
        val black = BinInks.byId("black")!!
        assertThat(BinInks.edge(black, DarkColors)).isEqualTo(black.nightEdge)
        assertThat(BinInks.edge(black, LightColors)).isNull()
        assertThat(BinInks.all.filter { it.nightEdge != null }.map { it.id }).containsExactly("black")
        assertThat(BinInks.edge(null, DarkColors)).isNull()
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
