package uk.co.siland.culvery.capability.taekwondo

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.capability.taekwondo.ui.TaekwondoTile
import uk.co.siland.culvery.capability.taekwondo.ui.TaekwondoWords
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.Icons

/**
 * A throwaway practice screen for the son's Choong-Moo (15b §8): one half-width button after the modes in Home's tray, no tab
 * and no card. To delete it: remove `capability/taekwondo/`, its `include` in settings.gradle.kts, the app's
 * `implementation` line, and its `capability/taekwondo` lines in core/ui/src/test/resources/copy-scan-allow.txt.
 */
@Singleton
class TaekwondoCapability @Inject constructor(
    private val settings: TaekwondoSettings,
    private val speaker: Provider<Speaker>,
) : Capability {
    override val id = "taekwondo"
    override val label = TaekwondoWords.TITLE
    override val icon = Icons.SPORTS_MARTIAL_ARTS
    override val order = 95
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit

    override fun trayButtons(): Flow<List<TrayButton>> =
        flowOf(listOf(TrayButton("taekwondo", 90, TrayWidth.HALF) { TaekwondoTile(settings, speaker) }))
}
