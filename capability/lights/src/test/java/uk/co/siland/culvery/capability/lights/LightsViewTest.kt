package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightsViewTest {
    @get:Rule val lights = LightsFixture()
    private val hue = TestLightsProvider()

    /** Fails if main ignores a changed choice, or the rooms the system lists. */
    @Test
    fun theMainRoomFollowsTheSetting() = runTest {
        val settings = lights.settings
        settings.connect("lights.hue", link())
        val bridges = Bridges(setOf(hue), settings, lights.scope)
        val view = LightsView(bridges, settings, lights.scope)
        soon { bridges.system.first { it != null } }
        val system = hue.systems.single()
        system.rooms.value = listOf(room("Lounge"), room("Kitchen"))
        assertThat(soon { view.main.first { it?.id == "Lounge-id" } }?.name).isEqualTo("Lounge")
        settings.setMainRoom("Kitchen-id")
        assertThat(soon { view.main.first { it?.id == "Kitchen-id" } }?.name).isEqualTo("Kitchen")
        system.rooms.value = listOf(room("Lounge"), room("Kitchen", on = true))
        assertThat(soon { view.main.first { it?.on == true } }?.id).isEqualTo("Kitchen-id")
        system.rooms.value = listOf(room("Lounge"))
        assertThat(soon { view.main.first { it?.id == "Lounge-id" } }?.name).isEqualTo("Lounge")
    }
}
