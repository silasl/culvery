package uk.co.siland.culvery.capability.door

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.door.ui.DoorPage
import uk.co.siland.culvery.capability.door.ui.DoorSettingsHost
import uk.co.siland.culvery.capability.door.ui.DoorTabHost
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.ui.Icons

const val DOOR_ID = "door"

/** The doorbell (5 §3.1): rail slot 50, reserved for Security; no Home card. */
@Singleton
class DoorCapability @Inject constructor(
    private val store: DoorStore,
    private val settings: DoorSettings,
    providers: Set<@JvmSuppressWildcards DoorProvider>,
    private val clock: HouseholdClock,
    private val zone: HouseholdZone,
    private val health: DoorHealth,
    private val chime: Chime,
    private val toaster: Toaster,
) : Capability {
    override val id = DOOR_ID
    override val label = "Door"
    override val icon = Icons.DOORBELL
    override val order = 50

    private val provider = providers.firstOrNull()

    /** Once Ring is connected (5 §3.1, §5.5). */
    override val hasTab: Flow<Boolean> = settings.prefs.map { provider != null && it.connected }.distinctUntilChanged()

    /** Counted over the days the tab shows, so the badge and the tab agree; moves on at midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val badge: Flow<RailBadge?> = combine(clock.today, zone.zone, ::startOfYesterday)
        .distinctUntilChanged()
        .flatMapLatest { store.unseen(it) }
        .map(::badgeOf)
        .distinctUntilChanged()

    private val pages: List<SettingsPage> = listOfNotNull(
        provider?.let { p -> DoorPage { DoorSettingsHost(settings, health, p, chime, clock, zone.zone, toaster) } },
    )

    override fun settingsPages(): List<SettingsPage> = pages

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = DoorTabHost(store, clock.today, zone.zone, status = health.status)
}
