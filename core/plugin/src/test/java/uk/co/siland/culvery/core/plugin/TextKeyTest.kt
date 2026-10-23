package uk.co.siland.culvery.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextKeyTest {
    @Test
    fun trimsCollapsesDropsAccentsAndLowersCase() = assertThat(textKey("  Crème   Fraîche ")).isEqualTo("creme fraiche")

    @Test
    fun caseDoesNotMatter() = assertThat(textKey("MILK")).isEqualTo(textKey("milk"))

    @Test
    fun tabsAndNewlinesAreWhitespace() = assertThat(textKey("\tEggs\n")).isEqualTo("eggs")

    @Test
    fun blankTextHasAnEmptyKey() = assertThat(textKey("   ")).isEmpty()
}
