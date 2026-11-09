package uk.co.siland.culvery.core.sound

import uk.co.siland.culvery.core.plugin.Toaster

// The line under a tablet sound's row, wherever a sound is chosen (18 §5).
const val ON_THIS_TABLET = "On this tablet"
const val NOT_ON_THIS_TABLET = "Not on this tablet any more"

/** The line under a sound's row: null for a built-in, "On this tablet", and for a theme's file "On this tablet · Halloween". */
val Sound.tabletLine: String?
    get() = when {
        !onTablet -> null
        theme != null -> "$ON_THIS_TABLET · ${theme.label}"
        else -> ON_THIS_TABLET
    }

const val DOORBELL_PLAYING = "The doorbell is playing. Try again when it stops."

/** Says in a toast why a Settings Play of [sound] didn't start; nothing for a stop or a newer tap. */
fun Toaster.sayWhyNotPlayed(sound: Sound, start: PadStart) {
    when (start) {
        PadStart.FAILED -> show(couldntPlay(sound), urgent = true)
        PadStart.DOORBELL -> show(DOORBELL_PLAYING, urgent = true)
        PadStart.STARTED, PadStart.STOPPED -> Unit
    }
}

/** What a Play in Settings says when [sound] won't play, and what to do about it. */
fun couldntPlay(sound: Sound): String =
    if (sound.onTablet) "Couldn’t play ${sound.name}. Copy the file to the tablet’s sounds folder again."
    else "Couldn’t play ${sound.name}. Restart Culvery and try again."
