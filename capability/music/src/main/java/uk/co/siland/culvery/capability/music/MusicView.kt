package uk.co.siland.culvery.capability.music

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** What the screens show: the open system's groups and problem, and Home's pick (6 §2.2). */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicView @Inject constructor(speakers: Speakers, settings: MusicSettings, @ApplicationScope scope: CoroutineScope) {
    val groups: Flow<List<SpeakerGroup>> = speakers.system.flatMapLatest { it?.groups ?: flowOf(emptyList()) }
    val problem: Flow<MusicProblem?> = speakers.system.flatMapLatest { it?.problem ?: flowOf(null) }

    /** Held on the application scope, so a room paused from Home is remembered while the card is away (6 §2.2). */
    val home: StateFlow<HomePick?> = homePicks(groups, settings.prefs.map { it.usualRoomId }.distinctUntilChanged())
        .stateIn(scope, SharingStarted.Eagerly, null)
}
