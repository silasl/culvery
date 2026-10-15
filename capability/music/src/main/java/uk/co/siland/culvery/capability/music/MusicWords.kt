package uk.co.siland.culvery.capability.music

internal const val MUSIC = "Music"
internal const val CANT_REACH_SONOS = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi."
internal const val TABLET_OFFLINE = "The tablet is offline. Check the Wi-Fi."
internal const val OTHER_SONOS = "Culvery found a different Sonos system. Find Sonos again in Settings › Music."
internal const val SONOS_REFUSED = "Sonos didn’t do that. Try again."
internal const val CANT_PLAY_HERE = "Sonos couldn’t play this here."
internal const val NO_LIBRARY = "Sonos has no music library. Add it in the Sonos app."

/** The one line the Music tab, the Home card and Settings › Music show when something is wrong (6 §6). */
fun problemLine(problem: MusicProblem): String = when (problem) {
    MusicProblem.UNREACHABLE -> CANT_REACH_SONOS
    MusicProblem.OFFLINE -> TABLET_OFFLINE
    MusicProblem.OTHER_HOUSEHOLD -> OTHER_SONOS
}
