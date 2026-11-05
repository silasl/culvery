package uk.co.siland.culvery.capability.door

import uk.co.siland.culvery.core.sound.Sound

interface Chime {
    /** [volume] 0–1 scales the sound within the tablet's own notification volume. True when a sound started. */
    fun play(sound: Sound, volume: Float): Boolean

    /** Stops any sound pad that is playing, and keeps one still opening from starting. */
    fun hushPads()
}
