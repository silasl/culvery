package uk.co.siland.culvery.capability.door

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import javax.inject.Inject

enum class DoorSound(@RawRes internal val res: Int) {
    CLASSIC(R.raw.door_chime),
    ORGAN(R.raw.door_organ),
}

fun interface Chime {
    /** [volume] 0–1 scales the sound within the tablet's own notification volume. True when a sound started. */
    fun play(sound: DoorSound, volume: Float): Boolean
}

/** A notification event, so the tablet's notification volume and Do Not Disturb still apply (5 §4.1). */
internal val CHIME_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * Each play is its own player, held until it ends or fails: MediaPlayer's native side keeps only a weak reference to it.
 * A sound other than the classic chime that won't load or start gives way to the classic chime.
 */
class MediaPlayerChime internal constructor(private val open: (DoorSound) -> MediaPlayer?) : Chime {
    @Inject constructor(@ApplicationContext context: Context) : this({ sound ->
        MediaPlayer.create(context, sound.res, CHIME_ATTRIBUTES, AudioManager.AUDIO_SESSION_ID_GENERATE)
    })

    internal val live: MutableSet<MediaPlayer> = Collections.synchronizedSet(mutableSetOf())

    override fun play(sound: DoorSound, volume: Float): Boolean {
        if (start(sound, volume)) return true
        if (sound == DoorSound.CLASSIC) return false
        Log.w(TAG, "The door chime couldn't play ${sound.name}; playing the classic chime")
        return start(DoorSound.CLASSIC, volume)
    }

    private fun start(sound: DoorSound, volume: Float): Boolean {
        val player = open(sound)
        if (player == null) {
            Log.w(TAG, "The door chime couldn't be loaded")
            return false
        }
        live += player
        try {
            player.setVolume(volume, volume)
            player.setOnCompletionListener { finish(it) }
            player.setOnErrorListener { mp, _, _ ->
                finish(mp)
                true
            }
            player.start()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "The door chime couldn't start (${e::class.simpleName})")
            finish(player)
            return false
        }
        return true
    }

    private fun finish(player: MediaPlayer) {
        live -= player
        player.release()
    }

    private companion object {
        const val TAG = "MediaPlayerChime"
    }
}
