package uk.co.siland.culvery.capability.hoover

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

/** The hoover's button in Home's tray (11 §4.4): no tab and no card. */
@Singleton
class HooverCapability @Inject constructor(
    @Suppress("unused") private val systems: Set<@JvmSuppressWildcards HooverSystem>,
) : Capability {
    override val id = "hoover"
    override val label = HOOVER
    override val icon = Icons.CLEANING_SERVICES
    override val order = 70
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit
}
