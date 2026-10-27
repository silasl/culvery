package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface Capability : HomeCardContributor {
    val id: String
    val label: String
    /** Material Symbols ligature name for the nav rail. */
    val icon: String
    /** Rail position: Calendar 10, Music 30, Lights 40, Door 50, Plugs 55, Weather 60, Hoover 70 (no tab). */
    val order: Int
    /** True when at least one connection provides this capability and it has a tab. */
    val hasTab: Flow<Boolean>

    @Composable
    fun TabContent()

    /** This capability's wizard steps (4a design D7), placed by [SetupStep.order] among the core ones. */
    fun setupSteps(): List<SetupStep> = emptyList()

    /** This capability's Settings pages (4a design D7), placed by [SettingsPage.order]. */
    fun settingsPages(): List<SettingsPage> = emptyList()

    /** Items on the right of Home's header, by [HeaderItem.order]: weather 10, later indoor climate 20. */
    fun headerItems(): Flow<List<HeaderItem>> = flowOf(emptyList())

    /** Rows this capability adds to Home's One-tap tray (11 §4.4), by [TrayButton.order]. */
    fun trayButtons(): Flow<List<TrayButton>> = flowOf(emptyList())

    /** The mark on this capability's rail tab (5 §3.3); none by default. */
    val badge: Flow<RailBadge?> get() = flowOf(null)
}
