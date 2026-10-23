package uk.co.siland.culvery.capability.shopping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.shopping.ui.ShoppingCardHost
import uk.co.siland.culvery.capability.shopping.ui.ShoppingPage
import uk.co.siland.culvery.capability.shopping.ui.ShoppingSettingsHost
import uk.co.siland.culvery.capability.shopping.ui.ShoppingTabHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.Icons

const val SHOPPING_CARD_ID = "shopping.list"

/** The shopping list (10 §4): a rail tab after Calendar and a utility card on Home, once a list is connected. */
@Singleton
class ShoppingCapability @Inject constructor(
    private val store: ShoppingStore,
    private val providers: Set<@JvmSuppressWildcards ShoppingProvider>,
    private val view: ShoppingView,
    private val actions: ShoppingActions,
    private val learning: ShoppingLearning,
    private val checks: ShoppingChecks,
    private val focus: ShoppingFocus,
    private val connections: ShoppingConnections,
    private val clock: HouseholdClock,
) : Capability {
    override val id = SHOPPING_ID
    override val label = "Shopping"
    override val icon = Icons.SHOPPING_CART
    override val order = 20

    /** A stored connection whose provider is bound; one whose provider is gone from the build has nothing to show. */
    override val hasTab: Flow<Boolean> = store.connection
        .map { stored -> stored != null && providers.any { it.descriptor.id == stored.connection.providerId } }
        .distinctUntilChanged()

    private val pages: List<SettingsPage> = if (providers.isEmpty()) {
        emptyList()
    } else {
        listOf(ShoppingPage { ShoppingSettingsHost(connections, store, clock) })
    }

    override fun settingsPages(): List<SettingsPage> = pages

    override fun cards(): Flow<List<HomeCard>> = hasTab.map { on ->
        if (on) listOf(HomeCard(SHOPPING_CARD_ID, HomeCardSize.UTILITY, 40) { ShoppingCardHost(view, actions, checks, focus) }) else emptyList()
    }

    @Composable
    override fun TabContent() {
        val scope = rememberCoroutineScope()
        ShoppingTabHost(
            view = view,
            actions = actions,
            learning = learning,
            checks = checks,
            focus = focus,
            onForget = { item -> scope.launch { connections.forget(item) } },
            nowMillis = rememberNowMillis(clock),
        )
    }
}
