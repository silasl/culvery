package uk.co.siland.culvery.capability.music

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope

private const val TAG = "MusicActions"

/** What the household asked of the speakers, the only thing the logs name (6 §6). */
enum class MusicCommand { PLAY, PAUSE, NEXT, GROUP_VOLUME, ROOM_VOLUME, SHUFFLE, FAVOURITES, FAVOURITE }

sealed interface FavouritesLoad {
    data class Ready(val favourites: List<Favourite>) : FavouritesLoad

    data class Failed(val line: String) : FavouritesLoad
}

/**
 * The screens' commands (6 §4.3): each runs on the application scope, so leaving the screen doesn't cancel it. When Sonos
 * refuses one, or there's no library, its group's row says so until that group's next command; no speaker answering is
 * the status line's to say.
 */
@Singleton
class MusicActions internal constructor(private val system: () -> MusicSystem?, private val scope: CoroutineScope) {
    @Inject
    constructor(speakers: Speakers, @ApplicationScope scope: CoroutineScope) : this({ speakers.system.value }, scope)

    private val _lines = MutableStateFlow<Map<String, String>>(emptyMap())

    /** A group's line after its last command went wrong (6 §6), by group id. */
    val lines: StateFlow<Map<String, String>> = _lines.asStateFlow()

    fun play(group: SpeakerGroup) = send(group, MusicCommand.PLAY) { it.play(group) }

    fun pause(group: SpeakerGroup) = send(group, MusicCommand.PAUSE) { it.pause(group) }

    fun next(group: SpeakerGroup) = send(group, MusicCommand.NEXT) { it.next(group) }

    fun setGroupVolume(group: SpeakerGroup, percent: Int) = send(group, MusicCommand.GROUP_VOLUME) { it.setGroupVolume(group, percent) }

    fun setRoomVolume(group: SpeakerGroup, room: SpeakerRoom, percent: Int) = send(group, MusicCommand.ROOM_VOLUME) { it.setRoomVolume(room, percent) }

    fun shuffleEverything(group: SpeakerGroup) = send(group, MusicCommand.SHUFFLE) { it.shuffleEverything(group) }

    /** Read each time the sheet opens (6 §4.5). */
    suspend fun favourites(): FavouritesLoad {
        val s = system() ?: return FavouritesLoad.Failed(CANT_REACH_SONOS)
        return try {
            FavouritesLoad.Ready(s.favourites())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(MusicCommand.FAVOURITES, e)
            FavouritesLoad.Failed(sheetLine(e, SONOS_REFUSED))
        }
    }

    /** Plays [favourite] in [group]: null when it played, else the line the sheet shows as it stays open (6 §5.3, §6). */
    suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite): String? {
        val s = system() ?: return CANT_REACH_SONOS
        return try {
            s.playFavourite(group, favourite)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(MusicCommand.FAVOURITE, e)
            sheetLine(e, CANT_PLAY_HERE)
        }
    }

    private fun send(group: SpeakerGroup, command: MusicCommand, step: suspend (MusicSystem) -> Unit) {
        _lines.update { it - group.id }
        val s = system() ?: return
        scope.launch {
            try {
                step(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure(command, e)
                rowLine(e)?.let { line -> _lines.update { it + (group.id to line) } }
            }
        }
    }

    private fun rowLine(e: Exception): String? = when (e) {
        is MusicRefusedException -> SONOS_REFUSED
        is NoLibraryException -> NO_LIBRARY
        else -> null
    }

    private fun sheetLine(e: Exception, refused: String): String = when (e) {
        is MusicUnreachableException -> problemLine(e.problem)
        is MusicRefusedException -> refused
        else -> CANT_REACH_SONOS
    }

    private fun logFailure(command: MusicCommand, e: Exception) {
        Log.w(TAG, "${command.name} failed (${e::class.simpleName})")
    }
}
