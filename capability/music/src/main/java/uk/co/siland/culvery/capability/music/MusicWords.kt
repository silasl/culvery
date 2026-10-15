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

internal const val NOTHING_PLAYING = "Nothing playing"
internal const val LOOKING_FOR_SPEAKERS = "Looking for the speakers…"
internal const val SHUFFLE_EVERYTHING = "Shuffle everything"

/** The two lines under a group's name: title and artist, or station and programme; "Nothing playing" (6 §5.1). */
fun nowPlayingLines(group: SpeakerGroup, nothingPlaying: Boolean): Pair<String, String?> = when (val n = group.nowPlaying.takeUnless { nothingPlaying }) {
    null -> NOTHING_PLAYING to null
    is NowPlaying.Track -> n.title to n.artist
    is NowPlaying.Radio -> n.station to n.programme
}

/** 6 §5.5: "Kitchen, playing Song by Artist", "Kitchen, paused", "Kitchen, nothing playing". */
fun groupSpoken(group: SpeakerGroup, nothingPlaying: Boolean = false): String {
    val n = group.nowPlaying.takeUnless { nothingPlaying }
    return when {
        n == null -> "${group.name}, nothing playing"
        group.isPlaying -> "${group.name}, playing ${playingWords(n)}"
        group.state == PlayState.PAUSED -> "${group.name}, paused"
        else -> "${group.name}, nothing playing"
    }
}

private fun playingWords(n: NowPlaying): String = when (n) {
    is NowPlaying.Track -> listOfNotNull(n.title, n.artist).joinToString(" by ")
    is NowPlaying.Radio -> listOfNotNull(n.station, n.programme).joinToString(", ")
}

fun playSpoken(name: String) = "Play $name"

fun pauseSpoken(name: String) = "Pause $name"

fun nextSpoken(name: String) = "Next track in $name"

fun shuffleSpoken(name: String) = "$SHUFFLE_EVERYTHING in $name"

fun volumeName(name: String) = "$name volume"

fun percentWords(percent: Int) = "$percent percent"

internal const val FAVOURITES_LABEL = "Favourites"
internal const val ROOMS = "Rooms"
internal const val NO_FAVOURITES = "No Sonos Favourites yet. Add them in the Sonos app."
internal const val GETTING_FAVOURITES = "Getting your Favourites…"

fun favouritesSpoken(name: String) = "$FAVOURITES_LABEL for $name"

/** What a tap on the Rooms chip does, for TalkBack. */
fun roomsAction(shown: Boolean) = if (shown) "hide each room’s volume" else "show each room’s volume"

fun playInAction(name: String) = "play in $name"

internal const val NOTHING_ANSWERED = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."
internal const val TYPE_FOUR_NUMBERS = "Type the address as four numbers, like 192.168.1.20."
internal const val NO_SPEAKER_THERE = "No Sonos speaker answered at that address. Check it in the Sonos app under About My System."
