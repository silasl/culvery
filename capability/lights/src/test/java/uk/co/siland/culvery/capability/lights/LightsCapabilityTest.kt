package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class LightsCapabilityTest {
    @get:Rule val lights = LightsFixture()
    private val scope = lights.scope
    private val settings get() = lights.settings

    private fun capability() = LightsCapability(Bridges(setOf(TestLightsProvider()), settings, scope))

    /** 7 §3.1: rail slot 40, "Lights", the light bulb. Fails if the id, label, icon or slot differs. */
    @Test
    fun itIsLightsAtSlotForty() {
        val lights = capability()
        assertThat(listOf(lights.id, lights.label, lights.icon, lights.order)).containsExactly("lights", "Lights", Icons.LIGHTBULB, 40).inOrder()
    }

    /** 7 §3.1: the tab once a bridge is paired. Fails if the tab shows before Find Hue Bridge, or not after. */
    @Test
    fun theTabShowsOnceABridgeIsPaired() = runTest {
        val lights = capability()
        assertThat(lights.hasTab.first()).isFalse()
        settings.connect("lights.hue", link())
        soon { lights.hasTab.first { it } }
    }

    /** 7 §5.2: no rail badge. Fails if the capability marks its tab. */
    @Test
    fun noBadge() = runTest {
        assertThat(capability().badge.first()).isNull()
    }
}
