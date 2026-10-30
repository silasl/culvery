package uk.co.siland.culvery.capability.plugs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class PlugsCapabilityTest {
    /** 15a §4.5: fails if plugs get a tab of their own again. */
    @Test
    fun plugsHaveNoTab() = runTest {
        assertThat(PlugsCapability().hasTab.first()).isFalse()
    }

    /** Fails if the id, label, icon or order differs, or the capability adds a Home card. */
    @Test
    fun itKeepsItsPlaceInTheOrder() = runTest {
        val capability = PlugsCapability()
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("plugs", "Plugs", "power", 55).inOrder()
        assertThat(capability.icon).isEqualTo(Icons.POWER)
        assertThat(capability.cards().first()).isEmpty()
    }
}
