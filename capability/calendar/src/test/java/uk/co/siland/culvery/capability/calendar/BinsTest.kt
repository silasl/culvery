package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BinsTest {
    @Test
    fun titleOrdersKnownThenUnknown() {
        assertThat(binsTitle(listOf("waste", "Clinical Waste", "food"))).isEqualTo("Bins: Food, Waste, Clinical Waste")
    }

    @Test
    fun unknownNamesSortWhateverTheirCase() {
        assertThat(orderedBins(listOf("glass", "Clinical Waste", "batteries"))).containsExactly("batteries", "Clinical Waste", "glass").inOrder()
    }

    @Test
    fun eachBinIsListedOnce() {
        assertThat(orderedBins(listOf("waste", "Glass", "waste", "Glass", "food"))).containsExactly("food", "waste", "Glass").inOrder()
    }

    @Test
    fun oneBinStillSaysBins() {
        assertThat(binsTitle(listOf("garden"))).isEqualTo("Bins: Garden")
    }
}
