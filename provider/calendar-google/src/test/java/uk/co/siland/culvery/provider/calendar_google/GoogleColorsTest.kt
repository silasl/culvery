package uk.co.siland.culvery.provider.calendar_google

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoogleColorsTest {
    @Test
    fun eachOfGooglesColoursIsItsOwnNearest() {
        EVENT_COLORS.forEach { (id, rgb) -> assertThat(nearestColorId(0xFF000000L or rgb.toLong())).isEqualTo(id) }
    }

    @Test
    fun theSamplePeopleGetBasilBlueberryAndFlamingo() {
        assertThat(nearestColorId(0xFF4CB387)).isEqualTo("10")
        assertThat(nearestColorId(0xFF5B9BE0)).isEqualTo("9")
        assertThat(nearestColorId(0xFFE07BA8)).isEqualTo("4")
    }
}
