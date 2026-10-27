package uk.co.siland.culvery.capability.plugs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class PlugsCapabilityTest {
    private fun capability(system: FakePlugSystem?): PlugsCapability {
        val plugs = PlugsRig()
        val actions = PlugActions(TestAccess(Role.ADULT), plugs.settings, system, RecordingToaster(), CoroutineScope(Job()))
        return PlugsCapability(setOfNotNull<PlugSystem>(system), actions, plugs.settings, MODES)
    }

    /** 12 §4.3: fails if the tab shows with no Home Assistant link, or hides with a link and no plugs. */
    @Test
    fun theTabShowsOnceLinked() = runTest {
        val system = FakePlugSystem()
        system.added.value = null
        val capability = capability(system)
        assertThat(capability.hasTab.first()).isFalse()

        system.added.value = emptyList()
        assertThat(capability.hasTab.first()).isTrue()
    }

    /** 12 §4.3: fails if a build with no plug system has a tab. */
    @Test
    fun noSystemNoTab() = runTest {
        assertThat(capability(null).hasTab.first()).isFalse()
    }

    /** Fails if the id, label, icon or order differs, or the capability adds a Home card. */
    @Test
    fun itSitsAfterTheDoor() = runTest {
        val capability = capability(FakePlugSystem())
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("plugs", "Plugs", "power", 55).inOrder()
        assertThat(capability.icon).isEqualTo(Icons.POWER)
        assertThat(capability.cards().first()).isEmpty()
    }
}
