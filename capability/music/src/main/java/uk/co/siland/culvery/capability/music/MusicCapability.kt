package uk.co.siland.culvery.capability.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.music.ui.ArtCache
import uk.co.siland.culvery.capability.music.ui.LocalArtSource
import uk.co.siland.culvery.capability.music.ui.MusicCardHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.ui.Icons

const val MUSIC_ID = "music"

private const val MUSIC_CARD_ID = "music.now"

/** After Today (100), Coming up (50) and the forecast (40): row 2, column 3. */
private const val MUSIC_CARD_PRIORITY = 30

/** Sonos on the wall (6 §3.1): rail slot 30, open to everyone, no badge. */
@Singleton
class MusicCapability @Inject constructor(
    private val speakers: Speakers,
    private val view: MusicView,
    private val actions: MusicActions,
    private val checks: MusicChecks,
    private val art: ArtCache,
) : Capability {
    override val id = MUSIC_ID
    override val label = MUSIC
    override val icon = Icons.SPEAKER
    override val order = 30

    /** Once a connection is saved and its provider is in this build (6 §3.1). */
    override val hasTab: Flow<Boolean> = speakers.system.map { it != null }.distinctUntilChanged()

    private val card = HomeCard(MUSIC_CARD_ID, HomeCardSize.REGULAR, MUSIC_CARD_PRIORITY) {
        CompositionLocalProvider(LocalArtSource provides art) { MusicCardHost(view, actions, checks) }
    }

    /** Home's card once the speakers are connected (6 §5.1). */
    override fun cards(): Flow<List<HomeCard>> = hasTab.map { if (it) listOf(card) else emptyList() }

    @Composable
    override fun TabContent() = Unit
}
