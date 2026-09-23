package uk.co.siland.culvery.core.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeTest {
    // Android only varies a resource font when the Font carries settings; the weight alone is just a matching key.
    @OptIn(ExperimentalTextApi::class)
    @Test
    fun eachDmSansWeightSetsTheMatchingWghtAxis() {
        val fonts = (DmSans as FontListFontFamily).fonts.map { it as ResourceFont }
        fonts.forEach { font ->
            val wght = font.variationSettings.settings.filter { it.axisName == "wght" }.map { it.toVariationValue(null) }
            assertEquals("wght for ${font.weight}", listOf(font.weight.weight.toFloat()), wght)
        }
        assertEquals(listOf(400, 500, 600, 700), fonts.map { it.weight.weight })
    }
}
