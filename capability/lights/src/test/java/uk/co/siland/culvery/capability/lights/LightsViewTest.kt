package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.test.soon

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

    /** Fails if a left-out room's light lights the button, an included room's doesn't, or all-off rooms light it. */
    @Test
    fun allLightsOffIsLitOnlyByAnIncludedRoomThatIsOn() {
        val lounge = room("Lounge", on = true)
        val kitchen = room("Kitchen")
        assertThat(allOffLit(listOf(lounge, kitchen), emptySet())).isTrue()
        assertThat(allOffLit(listOf(lounge, kitchen), setOf("Kitchen-id"))).isTrue()
        assertThat(allOffLit(listOf(lounge, kitchen), setOf("Lounge-id"))).isFalse()
        assertThat(allOffLit(listOf(kitchen, room("Hall")), emptySet())).isFalse()
        assertThat(allOffLit(emptyList(), emptySet())).isFalse()
    }

    /** Fails if the view's flag ignores the left-out setting or the rooms' on state as either changes. */
    @Test
    fun theViewFollowsTheRoomsAndTheLeftOutList() = runTest {
        val settings = lights.settings
        settings.connect("lights.hue", link())
        val bridges = Bridges(setOf(hue), settings, lights.scope)
        val view = LightsView(bridges, settings, lights.scope)
        soon { bridges.system.first { it != null } }
        val system = hue.systems.single()
        system.rooms.value = listOf(room("Lounge", on = true), room("Kitchen"))
        soon { view.allOffLit.first { it } }
        settings.setLeftOut(setOf("Lounge-id"))
        soon { view.allOffLit.first { !it } }
        settings.setLeftOut(emptySet())
        soon { view.allOffLit.first { it } }
        system.rooms.value = listOf(room("Lounge"), room("Kitchen"))
        soon { view.allOffLit.first { !it } }
    }
}
