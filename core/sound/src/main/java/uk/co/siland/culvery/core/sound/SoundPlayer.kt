package uk.co.siland.culvery.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** A notification event, so the tablet's notification volume and Do Not Disturb still apply (5 §4.1). */
val CHIME_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** The pad Settings' Play buttons use. */
const val PREVIEW_PAD = 0

/**
 * Plays the doorbell and the sound pads. Each play is its own player, held until it ends or fails: MediaPlayer's
 * native side keeps only a weak reference to it. A ring stops the pad sound; a pad stops the pad sound before it.
 * Tablet sounds stop at a cap; built-in sounds play to their end.
 */
@Singleton
class SoundPlayer internal constructor(
    private val open: (Sound) -> MediaPlayer?,
    private val scope: CoroutineScope,
) {
    @Inject constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) : this({ sound ->
        when (val source = sound.source) {
            is SoundSource.BuiltIn ->
                MediaPlayer.create(context, source.sound.res, CHIME_ATTRIBUTES, AudioManager.AUDIO_SESSION_ID_GENERATE)
            is SoundSource.OnTablet -> openFile(source.file.path)
        }
    }, scope)

    private class Play(val player: MediaPlayer) {
        var cap: Job? = null
    }

    internal val live: MutableSet<MediaPlayer> = Collections.synchronizedSet(mutableSetOf())
    // Guards live membership, listeners, cap setup, pad, padState and rings: caps finish on the scope's thread, listeners on main, rings off main.
    private val lock = Any()
    private var pad: Play? = null
    private var rings = 0L
    private val padState = MutableStateFlow<Int?>(null)

    val padPlaying: StateFlow<Int?> = padState.asStateFlow()

    /**
     * Stops any pad sound first. [volume] 0–1 scales the sound within the tablet's own notification volume.
     * A sound that won't open or start gives way to the classic chime. True when a sound started.
     */
    fun playDoorbell(sound: Sound, volume: Float): Boolean {
        ringStarted()
        if (start(sound, volume, DOORBELL_CAP_MS, null, 0)) return true
        if (sound.id == BuiltInSound.CLASSIC.id) return false
        return start(CLASSIC, volume, DOORBELL_CAP_MS, null, 0)
    }

    /** Stops the current pad sound first. False when it won't open or start, or a ring began while it opened. */
    fun playPad(pad: Int, sound: Sound, volume: Float): Boolean {
        val ringsBefore = synchronized(lock) {
            stopPads()
            rings
        }
        return start(sound, volume, PAD_CAP_MS, pad, ringsBefore)
    }

    /** Stops any pad sound, and any pad still opening won't start. */
    fun ringStarted() {
        synchronized(lock) {
            rings++
            stopPads()
        }
    }

    fun stopPads() {
        synchronized(lock) { pad?.let(::finish) }
    }

    private fun start(sound: Sound, volume: Float, capMs: Long, padNumber: Int?, ringsBefore: Long): Boolean {
        val player = open(sound)
        if (player == null) {
            logFailed(sound)
            return false
        }
        val play = Play(player)
        synchronized(lock) {
            if (padNumber != null) {
                if (rings != ringsBefore) {
                    player.release()
                    return false
                }
                stopPads()
            }
            live += player
            try {
                player.setVolume(volume, volume)
                player.setOnCompletionListener { finish(play) }
                player.setOnErrorListener { _, _, _ ->
                    finish(play)
                    true
                }
                player.start()
            } catch (_: IllegalStateException) {
                logFailed(sound)
                finish(play)
                return false
            }
            if (padNumber != null) {
                pad = play
                padState.value = padNumber
            }
            if (sound.source is SoundSource.OnTablet) {
                play.cap = scope.launch {
                    delay(capMs)
                    finish(play)
                }
            }
        }
        return true
    }

    private fun finish(play: Play) {
        synchronized(lock) {
            if (!live.remove(play.player)) return
            play.cap?.cancel()
            play.player.release()
            if (pad === play) {
                pad = null
                padState.value = null
            }
        }
    }

    private fun logFailed(sound: Sound) {
        val source = sound.source
        if (source is SoundSource.BuiltIn) Log.w(TAG, "The ${source.sound.key} sound couldn't play")
        else Log.w(TAG, "A tablet sound couldn't play")
    }

    companion object {
        const val PAD_CAP_MS = 8_000L
        const val DOORBELL_CAP_MS = 10_000L
        private const val TAG = "SoundPlayer"
        private val CLASSIC = BuiltInSound.CLASSIC.let { Sound(it.id, it.label, SoundSource.BuiltIn(it), it.theme) }

        private fun openFile(path: String): MediaPlayer? {
            val player = MediaPlayer()
            return try {
                player.setAudioAttributes(CHIME_ATTRIBUTES)
                player.setDataSource(path)
                player.prepare()
                player
            } catch (_: Exception) {
                player.release()
                null
            }
        }
    }
}
