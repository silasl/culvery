package uk.co.siland.culvery.capability.music

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

const val MUSIC_ID = "music"

/** Sonos on the wall (6 §3.1): rail slot 30, open to everyone, no badge. */
@Singleton
class MusicCapability @Inject constructor(private val speakers: Speakers) : Capability {
    override val id = MUSIC_ID
    override val label = MUSIC
    override val icon = Icons.SPEAKER
    override val order = 30

    /** Once a connection is saved and its provider is in this build (6 §3.1). */
    override val hasTab: Flow<Boolean> = speakers.system.map { it != null }.distinctUntilChanged()

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit
}
