package uk.co.siland.culvery.capability.music

import android.util.Log
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.plugin.AlertQuieter
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "MusicQuieter"

/** Sonos may round a volume it was given; a room this close to what was set is still ours to put back (6 §4.6). */
internal const val RESTORE_DRIFT = 1

/**
 * Music's [AlertQuieter] (6 §4.6): each room of each group playing as the bell rings drops to [share] percent of its
 * volume, and goes back afterwards unless someone changed it meanwhile. Room by room, so a group keeps its balance. One
 * lock keeps a restore behind the quiet it undoes. Held in memory only: a restart while lowered leaves those rooms low.
 */
@Singleton
class MusicQuieter internal constructor(private val system: () -> MusicSystem?, private val share: () -> Int) : AlertQuieter {
    @Inject
    constructor(speakers: Speakers, settings: MusicSettings) : this({ speakers.system.value }, { settings.current.value.lowerTo })

    private class Lowered(val room: SpeakerRoom, val was: Int, val set: Int)

    private val lock = Mutex()
    private var lowered: Pair<MusicSystem, List<Lowered>>? = null

    override suspend fun quiet() = lock.withLock {
        if (lowered != null) return@withLock
        val s = system() ?: return@withLock
        val percent = share()
        val playing = tried { s.playingNow() } ?: return@withLock
        val rooms: MutableList<Lowered> = Collections.synchronizedList(mutableListOf())
        lowered = s to rooms
        coroutineScope { playing.forEach { group -> launch { lowerGroup(s, group, percent, rooms) } } }
    }

    override suspend fun restore() = lock.withLock {
        val (s, rooms) = lowered ?: return@withLock
        lowered = null
        coroutineScope {
            rooms.toList().forEach { room -> launch { tried { putBack(s, room) } ?: tried { putBack(s, room) } } }
        }
    }

    /** All the group's rooms are read first, at once; a group that can't be read is left as it is (6 §4.6). */
    private suspend fun lowerGroup(s: MusicSystem, group: SpeakerGroup, percent: Int, rooms: MutableList<Lowered>) {
        val read = tried { coroutineScope { group.rooms.map { async { it to s.roomVolume(it) } }.awaitAll() } } ?: return
        val mine = read.map { (room, was) -> Lowered(room, was, (was * percent / 100.0).roundToInt()) }
        // Remembered before the commands go, so one that took effect but timed out is still put back.
        rooms += mine
        coroutineScope { mine.forEach { launch { tried { s.setRoomVolume(it.room, it.set) } } } }
    }

    private suspend fun putBack(s: MusicSystem, room: Lowered) {
        if (abs(s.roomVolume(room.room) - room.set) <= RESTORE_DRIFT) s.setRoomVolume(room.room, room.was)
    }

    private inline fun <T> tried(step: () -> T): T? = attempt(step) { e ->
        Log.w(TAG, "Couldn't change a room's volume for the doorbell (${e::class.simpleName})")
        null
    }
}
