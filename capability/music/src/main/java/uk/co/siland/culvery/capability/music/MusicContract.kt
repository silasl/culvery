package uk.co.siland.culvery.capability.music

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

enum class PlayState {
    PLAYING,
    PAUSED,
    STOPPED,
    TRANSITIONING;

    /** Sounding, or about to: a group starting a track passes through TRANSITIONING. */
    val isPlaying: Boolean get() = this == PLAYING || this == TRANSITIONING
}

data class SpeakerRoom(val id: String, val name: String, val volume: Int)

/** What a group plays: a track from its queue, a station, or an input with no track (6 §3.1). */
sealed interface NowPlaying {
    data class Track(val title: String, val artist: String?, val album: String?) : NowPlaying

    data class Radio(val station: String, val programme: String?) : NowPlaying

    /** Line-in or Bluetooth with no track: the speaker reports PLAYING whether or not anything sounds. */
    data class Input(val name: String) : NowPlaying
}

/**
 * One Sonos group as Sonos groups the rooms (6 §2.4): its rooms with the leader first, what it plays and its group volume.
 * [canSkip] is false for a stream; [startedAtMillis] is when it last started playing, for Home's choice (6 §2.2).
 */
data class SpeakerGroup(
    val id: String,
    val rooms: List<SpeakerRoom>,
    val coordinatorId: String,
    val state: PlayState,
    val nowPlaying: NowPlaying?,
    val artUrl: String?,
    val canSkip: Boolean,
    val volume: Int,
    val startedAtMillis: Long?,
    val fromInput: Boolean = false,
) {
    val isPlaying: Boolean get() = state.isPlaying

    /** Bluetooth or line-in is the source, whether or not it names a track: not coming through Sonos, so it never counts (Home and the Music tab). */
    val isInput: Boolean get() = fromInput || nowPlaying is NowPlaying.Input

    /** Playing something through Sonos. */
    val isSounding: Boolean get() = isPlaying && !isInput

    /** "Kitchen + Lounge" (6 §5.2). */
    val name: String get() = rooms.joinToString(" + ") { it.name }
}

enum class FavouriteKind { STREAM, QUEUED }

data class Favourite(val id: String, val name: String, val artUrl: String?, val kind: FavouriteKind)

/** What the status line says is wrong (6 §6). */
enum class MusicProblem { UNREACHABLE, OFFLINE, OTHER_HOUSEHOLD }

/** A speaker refused a command with a fault (6 §6); [code] is UPnP's when it gave one. */
class MusicRefusedException(val code: Int? = null) : Exception("A speaker refused the command (UPnP error ${code ?: "unknown"})")

/** No speaker answered even after looking again, or the tablet is offline, or the household changed: [problem] says which. */
class MusicUnreachableException(val problem: MusicProblem) : Exception("The speakers can't be reached (${problem.name})")

/** The library has no tracks: Shuffle everything stops before touching the queue (6 §4.4). */
class NoLibraryException : Exception("The music library is empty")

/** A saved household and the speaker address that last answered for it (6 §3.2). Neither is a secret. */
data class MusicLink(val householdId: String, val address: String)

/**
 * What a music provider implements (6 §3.1). [groups] and [problem] are kept by the system; the capability says when to
 * check (6 §4.2). Commands go to the group's leader, change [groups] at once, and check that group again; they throw
 * [MusicRefusedException], [MusicUnreachableException] or [NoLibraryException].
 */
interface MusicSystem {
    val groups: StateFlow<List<SpeakerGroup>>
    val problem: StateFlow<MusicProblem?>

    /** Rooms, groups and room volumes. */
    suspend fun checkLayout()

    /** Each group's leader: what plays and the group volume. */
    suspend fun checkPlayback()

    /** The groups playing right now, read afresh rather than from the last check (for the doorbell, 6 §4.6). */
    suspend fun playingNow(): List<SpeakerGroup>

    /** [room]'s own volume as its speaker holds it now (the lowering works room by room, 6 §4.6). */
    suspend fun roomVolume(room: SpeakerRoom): Int

    suspend fun play(group: SpeakerGroup)

    suspend fun pause(group: SpeakerGroup)

    suspend fun next(group: SpeakerGroup)

    suspend fun setGroupVolume(group: SpeakerGroup, percent: Int)

    suspend fun setRoomVolume(room: SpeakerRoom, percent: Int)

    suspend fun favourites(): List<Favourite>

    /** The household's own playlists, [FavouriteKind.QUEUED]; those already among the [favourites] last read are left out. */
    suspend fun playlists(): List<Favourite>

    suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite)

    suspend fun shuffleEverything(group: SpeakerGroup)

    /** The picture at [url], or null when it can't be had. */
    suspend fun art(url: String): ByteArray?

    /** Reads and commands go to the speaker at [address] from now on: one typed for this household (6 §4.1). */
    fun useAddress(address: String)
}

/** A way to the household's speakers (6 §3.2), for Settings › Music: find them, check an address, open the system. */
interface MusicProvider {
    val descriptor: ProviderDescriptor

    /** Looks for speakers on the home Wi-Fi (6 §4.1); null when none answered. Throws [MusicUnreachableException] offline. */
    suspend fun find(): MusicLink?

    /** Asks the speaker at [address] (four numbers) for its household; null when nothing there answers as a speaker. */
    suspend fun check(address: String): MusicLink?

    /** The household behind [link]; [onMoved] saves the address the system found it at after a rescan. */
    fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem
}
