package uk.co.siland.culvery.capability.music

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.scan

/** What Home shows (6 §5.1): [nothingPlaying] when it shows [group] only because nothing plays. */
data class HomePick(val group: SpeakerGroup, val nothingPlaying: Boolean)

/**
 * 6 §2.2: the playing group; of several, the one with the usual room, else the one that started last. With none
 * playing, [keptId] while it is paused (the group Home last showed playing, so Play resumes it), else the usual room's
 * group, else the first Sonos lists.
 */
fun homePick(groups: List<SpeakerGroup>, usualRoomId: String?, keptId: String?): HomePick? {
    fun SpeakerGroup.hasUsualRoom() = rooms.any { it.id == usualRoomId }
    val playing = groups.filter { it.isPlaying }
    val shown = when (playing.size) {
        0 -> null
        1 -> playing.single()
        else -> playing.firstOrNull { it.hasUsualRoom() } ?: playing.maxByOrNull { it.startedAtMillis ?: Long.MIN_VALUE }
    }
    if (shown != null) return HomePick(shown, nothingPlaying = false)
    groups.firstOrNull { it.id == keptId && it.state == PlayState.PAUSED }?.let { return HomePick(it, nothingPlaying = false) }
    val fallback = groups.firstOrNull { it.hasUsualRoom() } ?: groups.firstOrNull() ?: return null
    return HomePick(fallback, nothingPlaying = true)
}

/** [homePick] over time, remembering what Home last showed playing. */
fun homePicks(groups: Flow<List<SpeakerGroup>>, usualRoomId: Flow<String?>): Flow<HomePick?> =
    combine(groups, usualRoomId, ::Pair)
        .scan<Pair<List<SpeakerGroup>, String?>, HomePick?>(null) { last, (g, usual) ->
            homePick(g, usual, keptId = last?.takeUnless { it.nothingPlaying }?.group?.id)
        }
        .drop(1)

/** The Music tab's order (6 §5.2): playing groups first, then the rest, each in Sonos's order. */
fun tabOrder(groups: List<SpeakerGroup>): List<SpeakerGroup> = groups.sortedBy { !it.isPlaying }
