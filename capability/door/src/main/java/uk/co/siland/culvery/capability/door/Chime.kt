package uk.co.siland.culvery.capability.door

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
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

/** Each play is its own player, released when it ends. */
class MediaPlayerChime @Inject constructor(@ApplicationContext private val context: Context) : Chime {
    override fun play(volume: Float) {
        val player = MediaPlayer.create(context, R.raw.door_chime, CHIME_ATTRIBUTES, AudioManager.AUDIO_SESSION_ID_GENERATE)
        if (player == null) {
            Log.w(TAG, "The door chime couldn't be loaded")
            return
        }
        player.setVolume(volume, volume)
        player.setOnCompletionListener { it.release() }
        player.start()
    }

    private companion object {
        const val TAG = "MediaPlayerChime"
    }
}
