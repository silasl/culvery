package uk.co.siland.culvery.capability.door

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.sound.Sound

interface Chime {
    /** [volume] 0–1 scales the sound within the tablet's own notification volume. True when a sound started. */
    suspend fun play(sound: Sound, volume: Float): Boolean

    /** Stops any sound pad that is playing, and keeps one still opening from starting. */
    fun hushPads()

    /** The id of the sound Settings is previewing, or null. */
    val previewing: StateFlow<String?>

    /** Plays [sound] for Settings as a preview, stopped at the doorbell's cap; a ring stops it. True when it started. */
    suspend fun preview(sound: Sound, volume: Float): Boolean

    fun stopPreview()
}
