package uk.co.siland.culvery.capability.party

import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.sound.PREVIEW_PAD
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundPlayer

private const val COULDNT_PLAY_MS = 4_000L

/** What the pads know of the gate: nothing before its first read, then why they are blocked, if they are. */
sealed interface GateState {
    val block: PadBlock?
    val read: Boolean get() = this is Read

    data object Unread : GateState {
        override val block: PadBlock? = null
    }

    data class Read(override val block: PadBlock?) : GateState
}

/**
 * What a pad's tap does (18 §4.4) and the line under the pads' row (Ruling 6): the first pad shown carries the reason the
 * pads are greyed out, and a pad that couldn't play says so on its own line for [COULDNT_PLAY_MS].
 */
@Singleton
class PadTaps internal constructor(
    private val play: suspend (pad: Int, sound: Sound, volume: Float) -> PadStart,
    private val stop: () -> Unit,
    /** The pad playing, or null. */
    val playing: StateFlow<Int?>,
    private val gate: PadGate,
    private val volume: () -> Float,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(player: SoundPlayer, gate: PadGate, door: Optional<DoorQuiet>, @ApplicationScope scope: CoroutineScope) :
        this(player::playPad, player::stopPads, player.padPlaying, gate, { door.map { it.volume }.orElse(1f) }, scope)

    /** [GateState.Unread] until the gate's first read; the pads' blocked and faded looks both come from this one value. */
    val gateState: StateFlow<GateState> = gate.blocked.map<PadBlock?, GateState> { GateState.Read(it) }.stateIn(scope, SharingStarted.Eagerly, GateState.Unread)

    private val previewed = MutableStateFlow<String?>(null)

    /** The id of the sound Settings is previewing, or null. */
    val previewing: Flow<String?> = combine(playing, previewed) { pad, id -> id.takeIf { pad == PREVIEW_PAD } }

    private val failed = MutableStateFlow<Int?>(null)
    private var failedExpiry: Job? = null

    @Volatile private var opening: Int? = null

    /** Stops [pad] while it plays or opens, even while the pads are greyed out; else plays it unless they are. */
    suspend fun tap(pad: PadUi) {
        if (playing.value == pad.pad || opening == pad.pad) {
            opening = null
            clearFailed(pad.pad)
            stop()
            return
        }
        if (gate.blockedNow() != null) return
        clearFailed(pad.pad)
        val volume = volume()
        opening = pad.pad
        var stopped = false
        val result = try {
            play(pad.pad, pad.sound, volume)
        } finally {
            stopped = opening != pad.pad
            if (!stopped) opening = null
        }
        // A tap that stopped the opening, or a ring that arrived meanwhile, is not a failure.
        if (result == PadStart.FAILED && !stopped && gate.blockedNow() != PadBlock.DOORBELL) failedNow(pad.pad)
    }

    /** Plays [sound] for Settings at the doorbell's volume, whatever the time or mode; says whether it started. */
    suspend fun preview(sound: Sound): PadStart {
        val volume = volume()
        previewed.value = sound.id
        return play(PREVIEW_PAD, sound, volume)
    }

    /** Stops the sound [preview] started. */
    fun stopPreview() = stop()

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
    fun line(pad: Int, first: Boolean): Flow<String?> = combine(failed, gateState) { failedPad, gate ->
        when {
            !gate.read -> null
            failedPad == pad -> ModesWords.COULDNT_PLAY
            first -> gate.block?.line
            else -> null
        }
    }
}
