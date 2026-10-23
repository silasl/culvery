package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.HouseholdClock

/** 10 §4.2, §4.9: the tab and the card exist while a list is connected, and the page exists while a provider is bound. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingCapabilityTest : ShoppingTestBase() {
    private fun TestScope.capability(rig: ShoppingRig, providers: Set<ShoppingProvider> = setOf(rig.provider)): ShoppingCapability {
        val checks = rig.checks()
        val actions = rig.actions(checks = checks)
        val connections = ConnectionsRig(rig, backgroundScope, providers = providers).connections
        return ShoppingCapability(
            store = rig.store,
            providers = providers,
            view = ShoppingView(rig.store, actions, providers, backgroundScope),
            actions = actions,
            learning = rig.learning,
            checks = checks,
            focus = ShoppingFocus(),
            connections = connections,
            clock = HouseholdClock(flowOf(ZoneOffset.UTC), rig.clock, backgroundScope, ticks = emptyFlow()),
        )
    }

    @Test
    fun noConnectionNoTabAndNoCard() = runTest {
        val capability = capability(rig())
        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
    }

    @Test
    fun aConnectionShowsTheTabAndTheUtilityCard() = runTest {
        val rig = rig()
        val capability = capability(rig)
        rig.connect()
        assertThat(capability.hasTab.first()).isTrue()
        val card = capability.cards().first().single()
        assertThat(card.id).isEqualTo("shopping.list")
        assertThat(card.size).isEqualTo(HomeCardSize.UTILITY)
        assertThat(card.priority).isEqualTo(40)
    }

    @Test
    fun aConnectionWhoseProviderIsNotBoundShowsNothing() = runTest {
        val rig = rig()
        val capability = capability(rig)
        rig.store.connect(Connection("c9", "shopping.gone", "Gone", emptyMap()))
        assertThat(capability.hasTab.first()).isFalse()
        assertThat(capability.cards().first()).isEmpty()
    }

    @Test
    fun noProviderBoundNoSettingsPage() = runTest {
        val rig = rig()
        assertThat(capability(rig, providers = emptySet()).settingsPages()).isEmpty()
        val page = capability(rig).settingsPages().single()
        assertThat(page.id).isEqualTo("shopping")
        assertThat(page.title).isEqualTo("Shopping")
        assertThat(page.order).isEqualTo(420)
    }

    @Test
    fun theTabSitsAfterCalendar() = runTest {
        val capability = capability(rig())
        assertThat(capability.order).isEqualTo(20)
        assertThat(capability.icon).isEqualTo("shopping_cart")
        assertThat(capability.label).isEqualTo("Shopping")
        assertThat(capability.id).isEqualTo(SHOPPING_ID)
    }
}
