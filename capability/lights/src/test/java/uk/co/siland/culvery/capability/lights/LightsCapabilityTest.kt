package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.LightsTabSection
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.soon
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class LightsCapabilityTest {
    @get:Rule val lights = LightsFixture()
    private val scope = lights.scope
    private val settings get() = lights.settings

    private fun capability(
        providers: Set<LightsProvider> = setOf(TestLightsProvider()),
        sections: Set<LightsTabSection> = emptySet(),
    ): LightsCapability {
        val bridges = Bridges(providers, settings, scope)
        val house = LightsHouse(bridges, settings)
        return LightsCapability(
            bridges, LightsView(bridges, settings, scope),
            LightsActions({ bridges.system.value }, scope, System::currentTimeMillis, house, TestAccess(null)),
            house, LightsFollow(bridges.system, MutableStateFlow(true), scope),
            settings, LightsConnect(bridges.connectable, settings::connect), RecordingToaster(), sections,
        )
    }

    /** 7 §3.1: rail slot 40, "Lights", the light bulb. Fails if the id, label, icon or slot differs. */
    @Test
    fun itIsLightsAtSlotForty() {
        val lights = capability()
        assertThat(listOf(lights.id, lights.label, lights.icon, lights.order)).containsExactly("lights", "Lights", Icons.LIGHTBULB, 40).inOrder()
    }

    /** 7 §5.3: one Lights page at 470, after Music (450). Fails if the id, title or order differs. */
    @Test
    fun thePageSitsAfterMusic() {
        val page = capability().settingsPages().single()
        assertThat(listOf(page.id, page.title, page.order)).containsExactly("lights", "Lights", 470).inOrder()
        assertThat(page.order).isGreaterThan(450)
    }

    /** 7 §3.1: with no provider in the build there is nothing to connect. Fails if the page shows anyway. */
    @Test
    fun noProviderNoPage() {
        assertThat(capability(emptySet()).settingsPages()).isEmpty()
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

    /** 7 §5.1: one REGULAR card "lights.home" at priority 35, only once a bridge is paired. Fails if the id, size or priority differs, or it shows before pairing. */
    @Test
    fun theCardIsARegularCardOnceABridgeIsPaired() = runTest {
        val lights = capability()
        assertThat(lights.cards().first()).isEmpty()
        settings.connect("lights.hue", link())
        val card = soon { lights.cards().first { it.isNotEmpty() } }.single()
        assertThat(listOf(card.id, card.size, card.priority)).containsExactly("lights.home", HomeCardSize.REGULAR, 35).inOrder()
    }

    /** 9 §6.1: with every card wanting to show, Lights sits above Music and Coming up gives way. Fails if Lights ranks below Music or is not placed. */
    @Test
    fun theCardSitsAboveMusicBesideToday() = runTest {
        val lights = capability()
        settings.connect("lights.hue", link())
        val card = soon { lights.cards().first { it.isNotEmpty() } }.single()
        val others = listOf(
            HomeCard("calendar.today", HomeCardSize.TALL, 100) {}, HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) {},
            HomeCard("music.now", HomeCardSize.REGULAR, 30) {},
        )
        val layout = HomeCardPlacer.place(others + card)
        assertThat(layout.left?.id).isEqualTo("calendar.today")
        assertThat(layout.right.map { it.id }).containsExactly("lights.home", "music.now").inOrder()
    }

    /** 15a §4.5, Ruling 6: with no bridge and a section shown the tab is Plugs, with the power icon and no Home card. Fails if the rail still says Lights, the tab hides, or a card shows. */
    @Test
    fun plugsOnlyShowsTheTabAsPlugs() = runTest {
        val section = TestLightsSection()
        val lights = capability(sections = setOf(section))
        soon { lights.hasTab.first { it } }
        assertThat(lights.railLabel.first()).isEqualTo("Plugs")
        assertThat(lights.railIcon.first()).isEqualTo(Icons.POWER)
        assertThat(lights.cards().first()).isEmpty()
    }

    /** 15a §4.5. Fails if a house with a bridge and plugs relabels its rail tab. */
    @Test
    fun lightsAndPlugsKeepTheRailLabelLights() = runTest {
        val lights = capability(sections = setOf(TestLightsSection()))
        settings.connect("lights.hue", link())
        soon { lights.cards().first { it.isNotEmpty() } }
        assertThat(lights.hasTab.first()).isTrue()
        assertThat(lights.railLabel.first()).isEqualTo("Lights")
        assertThat(lights.railIcon.first()).isEqualTo(Icons.LIGHTBULB)
    }

    /** 15a §4.5. Fails if the tab shows with no bridge and no section shown, or a hidden section brings it back. */
    @Test
    fun neitherHidesTheTab() = runTest {
        assertThat(capability().hasTab.first()).isFalse()
        assertThat(capability(sections = setOf(TestLightsSection(shown = false))).hasTab.first()).isFalse()
    }
}
