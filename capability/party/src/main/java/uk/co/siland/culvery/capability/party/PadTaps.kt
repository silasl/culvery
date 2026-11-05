package uk.co.siland.culvery.capability.party

import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.sound.PREVIEW_PAD
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundPlayer

private const val COULDNT_PLAY_MS = 4_000L

/**
 * What a pad's tap does (18 §4.4) and the line under the pads' row (Ruling 6): the first pad shown carries the reason the
 * pads are greyed out, and a pad that couldn't play says so on its own line for [COULDNT_PLAY_MS].
 */
@Singleton
class PadTaps internal constructor(
    private val play: (pad: Int, sound: Sound, volume: Float) -> Boolean,
    private val stop: () -> Unit,
    /** The pad playing, or null. */
    val playing: StateFlow<Int?>,
    private val gate: PadGate,
    private val volume: () -> Float,
    private val scope: CoroutineScope,
    /** Where a play opens its sound, off the main thread. */
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(player: SoundPlayer, gate: PadGate, door: Optional<DoorQuiet>, @ApplicationScope scope: CoroutineScope) :
        this(player::playPad, player::stopPads, player.padPlaying, gate, { door.map { it.volume }.orElse(1f) }, scope, Dispatchers.IO)

    val blocked: StateFlow<PadBlock?> = gate.blocked.stateIn(scope, SharingStarted.Eagerly, null)

    private val failed = MutableStateFlow<Int?>(null)
    private var failedExpiry: Job? = null

    /** Stops [pad] while it plays, even while the pads are greyed out; else plays it unless they are. */
    suspend fun tap(pad: PadUi) {
        if (playing.value == pad.pad) {
            clearFailed(pad.pad)
            stop()
            return
        }
        if (gate.blockedNow() != null) return
        clearFailed(pad.pad)
        val volume = volume()
        val started = withContext(io) { play(pad.pad, pad.sound, volume) }
        // A ring that arrived while the sound opened shows as the Doorbell block, not as a failure.
        if (!started && gate.blockedNow() != PadBlock.DOORBELL) failedNow(pad.pad)
    }

    /** Plays [sound] for Settings at the doorbell's volume, whatever the time or mode. */
    suspend fun preview(sound: Sound) {
        val volume = volume()
        withContext(io) { play(PREVIEW_PAD, sound, volume) }
    }

    private fun clearFailed(pad: Int) {
        if (failed.value != pad) return
        failedExpiry?.cancel()
        failed.value = null
    }

    private fun failedNow(pad: Int) {
        failed.value = pad
        failedExpiry?.cancel()
        failedExpiry = scope.launch {
            delay(COULDNT_PLAY_MS)
            failed.update { if (it == pad) null else it }
        }
    }

    /** The line under [pad]; the [first] pad shown carries the block. */
    fun line(pad: Int, first: Boolean): Flow<String?> = combine(failed, blocked) { failedPad, block ->
        when {
            failedPad == pad -> ModesWords.COULDNT_PLAY
            first -> block?.line
            else -> null
        }
    }
}
