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
import uk.co.siland.culvery.core.ui.Icons

@RunWith(AndroidJUnit4::class)
class LightsCapabilityTest {
    @get:Rule val lights = LightsFixture()
    private val scope = lights.scope
    private val settings get() = lights.settings

    private fun capability(providers: Set<LightsProvider> = setOf(TestLightsProvider())): LightsCapability {
        val bridges = Bridges(providers, settings, scope)
        val house = LightsHouse(bridges, settings)
        return LightsCapability(
            bridges, LightsView(bridges, settings, scope),
            LightsActions({ bridges.system.value }, scope, System::currentTimeMillis, house, TestAccess(null)),
            house, LightsFollow(bridges.system, MutableStateFlow(true), scope),
            settings, LightsConnect(bridges.providers.firstOrNull(), settings::connect), TestAccess(null), RecordingToaster(),
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

    /** 7 §5.1: with every card wanting to show, the forecast gives way to Lights. Fails if Lights ranks below Music or the forecast, so it is not placed. */
    @Test
    fun theCardTakesTheForecastsPlaceBesideTodayComingUpAndMusic() = runTest {
        val lights = capability()
        settings.connect("lights.hue", link())
        val card = soon { lights.cards().first { it.isNotEmpty() } }.single()
        val others = listOf(
            HomeCard("calendar.today", HomeCardSize.TALL, 100) {}, HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) {},
            HomeCard("weather.forecast", HomeCardSize.REGULAR, 25) {}, HomeCard("music.now", HomeCardSize.REGULAR, 30) {},
        )
        val placed = HomeCardPlacer.place(others + card).map { it.card.id }
        assertThat(placed).containsExactly("calendar.today", "calendar.comingUp", "lights.home", "music.now")
        assertThat(placed).doesNotContain("weather.forecast")
    }
}
