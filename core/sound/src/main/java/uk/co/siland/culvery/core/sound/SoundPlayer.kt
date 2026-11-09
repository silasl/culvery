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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Tablet sounds stop at a cap; built-in sounds play to their end. A sound opens on [io], never on the caller's thread.
 */
@Singleton
class SoundPlayer internal constructor(
    private val open: (Sound) -> MediaPlayer?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    @Inject constructor(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope) : this({ sound ->
        when (val source = sound.source) {
            is SoundSource.BuiltIn ->
                MediaPlayer.create(context, source.sound.res, CHIME_ATTRIBUTES, AudioManager.AUDIO_SESSION_ID_GENERATE)
            is SoundSource.OnTablet -> openFile(source.file.path)
        }
    }, scope, Dispatchers.IO)

    private class Play(val player: MediaPlayer, val doorbell: Boolean) {
        var cap: Job? = null
    }

    internal val live: MutableSet<MediaPlayer> = Collections.synchronizedSet(mutableSetOf())
    // Guards live, pad, padState, previewState and the counters: caps, listeners and rings reach them from different threads.
    private val lock = Any()
    private var pad: Play? = null
    private var rings = 0L

    // Counts rings and stops, so a pad whose open finishes after either doesn't start.
    private var padEpoch = 0L
    private var doorbellOpening = 0
    private var doorbellPlaying = 0
    private val padState = MutableStateFlow<Int?>(null)
    private val previewState = MutableStateFlow<String?>(null)

    val padPlaying: StateFlow<Int?> = padState.asStateFlow()

    /** The id of the sound Settings is previewing, or null. */
    val previewing: StateFlow<String?> = previewState.asStateFlow()

    /**
     * Stops any pad sound first. [volume] 0–1 scales the sound within the tablet's own notification volume.
     * A sound that won't open or start gives way to the classic chime. True when a sound started.
     */
    suspend fun playDoorbell(sound: Sound, volume: Float): Boolean {
        synchronized(lock) {
            doorbellOpening++
            ringStarted()
        }
        try {
            if (start(sound, volume, DOORBELL_CAP_MS, null, null)) return true
            if (sound.id == BuiltInSound.CLASSIC.id) return false
            return start(CLASSIC, volume, DOORBELL_CAP_MS, null, null)
        } finally {
            synchronized(lock) { doorbellOpening-- }
        }
    }

    /**
     * Stops the current pad sound first; a tablet sound stops at [capMs]. False when it won't open or start, the
     * doorbell's sound is opening or playing, or a ring or stop came while it opened.
     */
    suspend fun playPad(pad: Int, sound: Sound, volume: Float, capMs: Long = PAD_CAP_MS): Boolean {
        val epochBefore = synchronized(lock) {
            if (doorbellOpening > 0 || doorbellPlaying > 0) return false
            stopPads()
            padEpoch
        }
        return start(sound, volume, capMs, pad, epochBefore)
    }

    /** Stops any pad sound, and any pad still opening won't start. */
    fun ringStarted() {
        synchronized(lock) {
            rings++
            stopPads()
        }
    }

    /** Stops the pad sound; a pad still opening won't start. */
    fun stopPads() {
        synchronized(lock) {
            padEpoch++
            pad?.let(::finish)
        }
    }

    /** Opens and starts on [io] as one step a cancelled caller can't interrupt, so an opened player is always tracked. */
    private suspend fun start(sound: Sound, volume: Float, capMs: Long, padNumber: Int?, epochBefore: Long?): Boolean =
        withContext(io + NonCancellable) { startNow(sound, volume, capMs, padNumber, epochBefore) }

    private fun startNow(sound: Sound, volume: Float, capMs: Long, padNumber: Int?, epochBefore: Long?): Boolean {
        val player = open(sound)
        if (player == null) {
            logFailed(sound)
            return false
        }
        val play = Play(player, doorbell = padNumber == null)
        synchronized(lock) {
            if (epochBefore != null && padEpoch != epochBefore) {
                player.release()
                return false
            }
            stopPads()
            val ringsAtStart = rings
            live += player
            if (play.doorbell) doorbellPlaying++
            try {
                player.setVolume(volume, volume)
                player.setOnCompletionListener { finish(play) }
                player.setOnErrorListener { _, _, _ ->
                    finish(play)
                    if (play.doorbell && sound.id != CLASSIC.id) chimeAfterError(volume, ringsAtStart)
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
                previewState.value = sound.id.takeIf { padNumber == PREVIEW_PAD }
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

    /** The classic chime in place of a doorbell sound that errored after it started, unless a newer ring came. */
    private fun chimeAfterError(volume: Float, ringsAtStart: Long) {
        synchronized(lock) {
            if (rings != ringsAtStart) return
            doorbellOpening++
        }
        scope.launch {
            try {
                start(CLASSIC, volume, DOORBELL_CAP_MS, null, null)
            } finally {
                synchronized(lock) { doorbellOpening-- }
            }
        }
    }

    private fun finish(play: Play) {
        synchronized(lock) {
            if (!live.remove(play.player)) return
            if (play.doorbell) doorbellPlaying--
            play.cap?.cancel()
            play.player.release()
            if (pad === play) {
                pad = null
                padState.value = null
                previewState.value = null
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
        private val CLASSIC = BuiltInSound.CLASSIC.asSound()

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
