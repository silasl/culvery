package uk.co.siland.culvery.capability.door

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import javax.inject.Inject

fun interface Chime {
    /** [volume] 0–1 scales the sound within the tablet's own notification volume. */
    fun play(volume: Float)
}

/** A notification event, so the tablet's notification volume and Do Not Disturb still apply (5 §4.1). */
internal val CHIME_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Each play is its own player, held until it ends or fails: MediaPlayer's native side keeps only a weak reference to it. */
class MediaPlayerChime @Inject constructor(@ApplicationContext private val context: Context) : Chime {
    internal val live: MutableSet<MediaPlayer> = Collections.synchronizedSet(mutableSetOf())

    override fun play(volume: Float) {
        val player = MediaPlayer.create(context, R.raw.door_chime, CHIME_ATTRIBUTES, AudioManager.AUDIO_SESSION_ID_GENERATE)
        if (player == null) {
            Log.w(TAG, "The door chime couldn't be loaded")
            return
        }
        live += player
        player.setVolume(volume, volume)
        player.setOnCompletionListener { finish(it) }
        player.setOnErrorListener { mp, _, _ ->
            finish(mp)
            true
        }
        player.start()
    }

    private fun finish(player: MediaPlayer) {
        live -= player
        player.release()
    }

    private companion object {
        const val TAG = "MediaPlayerChime"
    }
}
