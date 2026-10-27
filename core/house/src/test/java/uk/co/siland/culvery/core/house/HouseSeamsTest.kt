package uk.co.siland.culvery.core.house

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HouseSeamsTest {
    /** 12 §6: fails if a plug's name can be read out of a log line. */
    @Test
    fun aPlugChangeHidesItsName() {
        assertThat(PlugChange("Lamp", true).toString()).doesNotContain("Lamp")
    }
}
