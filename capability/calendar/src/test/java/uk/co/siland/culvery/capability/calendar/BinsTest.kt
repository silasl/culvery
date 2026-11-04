package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BinsTest {
    @Test
    fun titleOrdersKnownThenUnknown() {
        assertThat(binsTitle(listOf("waste", "Clinical Waste", "food"))).isEqualTo("Bins: Food, Waste, Clinical Waste")
    }

    @Test
    fun oneBinStillSaysBins() {
        assertThat(binsTitle(listOf("garden"))).isEqualTo("Bins: Garden")
    }
}
