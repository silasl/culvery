package uk.co.siland.culvery.capability.hoover

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.Icons

class HooverCapabilityTest {
    private fun capability(vararg systems: HooverSystem) =
        HooverCapability(systems.toSet(), HooverActions(TestAccess(Role.ADULT), systems.toSet()))

    private val capability = capability()

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

    /** 11 §4.4: the tray shows only when it has a button. Fails if a hoover with no state adds the row, or a chosen one doesn't. */
    @Test
    fun aChosenHooverAddsTheRow() = runTest {
        assertThat(capability(FakeHooverSystem()).trayButtons().first().map { it.id to it.order })
            .containsExactly("hoover.start" to 20)
        assertThat(capability(FakeHooverSystem(null)).trayButtons().first()).isEmpty()
        assertThat(capability().trayButtons().first()).isEmpty()
    }
}
