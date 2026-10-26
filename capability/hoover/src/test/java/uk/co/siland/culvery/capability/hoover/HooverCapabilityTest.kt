package uk.co.siland.culvery.capability.hoover

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.ui.Icons

class HooverCapabilityTest {
    private val capability = HooverCapability(emptySet())

    /** 11 §4.4: the Hoover is a tray button, not a tab. Fails if it has a tab or a Home card. */
    @Test
    fun itHasNoTabAndNoCards() = runTest {
        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
    }

    /** Fails if the id, label, icon or order differs. */
    @Test
    fun itIsHooverAtSeventy() {
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("hoover", "Hoover", "cleaning_services", 70).inOrder()
        assertThat(capability.icon).isEqualTo(Icons.CLEANING_SERVICES)
    }
}
