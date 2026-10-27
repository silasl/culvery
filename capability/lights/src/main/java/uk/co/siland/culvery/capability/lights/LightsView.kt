package uk.co.siland.culvery.capability.lights

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** The room the choice names while Hue lists it, else Hue's first room (7 §5.1); null when Hue lists none. */
fun mainRoom(rooms: List<LightRoom>, chosenId: String?): LightRoom? = rooms.firstOrNull { it.id == chosenId } ?: rooms.firstOrNull()

/** Whether All lights off has a light to turn off: a room that is on and not left out (7 §2.5). */
fun allOffLit(rooms: List<LightRoom>, leftOut: Set<String>): Boolean = rooms.any { it.on && it.id !in leftOut }

/** What the screens show: the open system's rooms and problem, the main room and Holiday lights' record (7 §5). */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LightsView @Inject constructor(bridges: Bridges, settings: LightsSettings, @ApplicationScope scope: CoroutineScope) {
    val rooms: Flow<List<LightRoom>> = bridges.system.flatMapLatest { it?.rooms ?: flowOf(emptyList()) }
    val problem: Flow<LightsProblem?> = bridges.system.flatMapLatest { it?.problem ?: flowOf(null) }

    /** Held on the application scope, so Home has it the moment its card shows. */
    val main: StateFlow<LightRoom?> = combine(rooms, settings.prefs.map { it.mainRoomId }.distinctUntilChanged(), ::mainRoom)
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** True while All lights off would turn a room off; the button is lit then. */
    val allOffLit: Flow<Boolean> = combine(rooms, settings.prefs.map { it.leftOut }.distinctUntilChanged(), ::allOffLit)
        .distinctUntilChanged()

    val holidayOn: Flow<Boolean> = settings.prefs.map { it.holidayOn }.distinctUntilChanged()

    /** The room the Lights tab scrolls to, set by the main room button's › (7 §2.3). */
    val focus = MutableStateFlow<String?>(null)
}
