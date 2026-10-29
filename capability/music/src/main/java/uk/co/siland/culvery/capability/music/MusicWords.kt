package uk.co.siland.culvery.capability.music

import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.plugin.TABLET_OFFLINE
import uk.co.siland.culvery.core.ui.plural

internal const val MUSIC = "Music"
internal const val CANT_REACH_SONOS = HouseLines.CANT_REACH_SONOS
internal const val OTHER_SONOS = "Culvery found a different Sonos system. Find Sonos again in Settings › Music."
internal const val SONOS_REFUSED = HouseLines.SONOS_REFUSED
internal const val CANT_PLAY_HERE = "Sonos couldn’t play this here."
internal const val NO_LIBRARY = "Sonos has no music library. Add it in the Sonos app."

/** The one line the Music tab, the Home card and Settings › Music show when something is wrong (6 §6). */
fun problemLine(problem: MusicProblem): String = when (problem) {
    MusicProblem.UNREACHABLE -> CANT_REACH_SONOS
    MusicProblem.OFFLINE -> TABLET_OFFLINE
    MusicProblem.OTHER_HOUSEHOLD -> OTHER_SONOS
}

/** The tab's subtitle: "4 rooms · 1 group · Sonos", "1 room · Sonos"; groups of one room aren't counted (9 §4.5). */
fun musicSummary(groups: List<SpeakerGroup>): String {
    val rooms = groups.sumOf { it.rooms.size }
    val grouped = groups.count { it.rooms.size > 1 }
    return listOfNotNull(plural(rooms.toLong(), "room"), if (grouped > 0) plural(grouped.toLong(), "group") else null, SONOS).joinToString(" · ")
}

internal const val NOTHING_PLAYING = "Nothing playing"
internal const val LOOKING_FOR_SPEAKERS = "Looking for the speakers…"
internal const val SHUFFLE_EVERYTHING = "Shuffle everything"

/** The two lines under a group's name: title and artist, or station and programme; "Nothing playing" (6 §5.1). */
fun nowPlayingLines(group: SpeakerGroup, nothingPlaying: Boolean): Pair<String, String?> = when (val n = group.nowPlaying.takeUnless { nothingPlaying }) {
    null -> NOTHING_PLAYING to null
    is NowPlaying.Track -> n.title to n.artist
    is NowPlaying.Radio -> n.station to n.programme
    is NowPlaying.Input -> n.name to null
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
    is NowPlaying.Input -> n.name
}

fun playSpoken(name: String) = "Play $name"

fun playLastSpoken(name: String) = "$PLAY_LAST in $name"

fun pauseSpoken(name: String) = "Pause $name"

fun nextSpoken(name: String) = "Next track in $name"

fun shuffleSpoken(name: String) = "$SHUFFLE_EVERYTHING in $name"

fun volumeName(name: String) = "$name volume"

fun percentWords(percent: Int) = "$percent percent"

internal const val FAVOURITES_LABEL = "Favourites"
internal const val FAVOURITES_SHEET_TITLE = "Favourites and playlists"
internal const val PLAYLISTS = "Playlists"
internal const val PLAY_LAST = "Play last"
internal const val NO_FAVOURITES = "No Sonos Favourites or playlists yet. Add them in the Sonos app."
internal const val GETTING_FAVOURITES = "Getting your Favourites…"

fun favouritesSpoken(name: String) = "$FAVOURITES_LABEL for $name"

/** The group chip: how many rooms the group holds (9 §4.5). */
fun roomsChip(count: Int): String = "$count rooms"

/** What a tap on the group chip does, for TalkBack. */
fun roomsAction(shown: Boolean) = if (shown) "hide each room’s volume" else "show each room’s volume"

fun playInAction(name: String) = "play in $name"

internal const val NOTHING_ANSWERED = "No Sonos speakers answered. Check they’re on, or enter a speaker’s address."
internal const val NO_SPEAKER_THERE = "No Sonos speaker answered at that address. Check it in the Sonos app under About My System."

internal const val SONOS = "Sonos"
internal const val FIND_SONOS = "Find Sonos"
internal const val LOOKING_FOR_SONOS = "Looking for Sonos…"
internal const val ENTER_ADDRESS = "Enter a speaker’s address"
internal const val NOT_CONNECTED = "Not connected yet. Find Sonos, or enter a speaker’s address."
internal const val CONNECTED = "Connected."
internal const val FOUND_SONOS = "Found Sonos."
internal const val CONNECTED_TO_SONOS = "Connected to Sonos."
internal const val ADDRESS_HINT = "Shown in the Sonos app under About My System."
internal const val SPEAKER_ADDRESS = "Speaker’s address"
internal const val ADDRESS_EXAMPLE = "192.168.1.20"
internal const val CONNECT_TO_SPEAKER = "Connect to this speaker"
internal const val USUAL_ROOM_LABEL = "Usual room"
internal const val ROOMS_AFTER_FIND = "The rooms appear here once Sonos is found."
internal const val DOORBELL = "Doorbell"
internal const val LOWER_MUSIC_TO = "When the doorbell rings, lower music to"

/** The doorbell's share, said as what it does: 0 % mutes (6 §5.4). */
fun lowerToWords(percent: Int): String = if (percent == 0) "0 % (muted)" else "$percent % of its volume"
