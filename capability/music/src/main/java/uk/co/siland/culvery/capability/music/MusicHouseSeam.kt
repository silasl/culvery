package uk.co.siland.culvery.capability.music

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicPick
import uk.co.siland.culvery.core.house.MusicStart
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "MusicHouseSeam"

/** What Party was doing when it failed: the only thing the log names. */
private enum class Step { BEFORE, GROUP, PLAY, CHECK, PAUSE, REGROUP, OPTIONS }

/** Party mode's way to the speakers (13 §4.1, §4.2). */
class MusicHouseSeam internal constructor(
    private val system: () -> MusicSystem?,
    override val ready: Flow<Boolean>,
) : HouseMusic {
    @Inject
    constructor(speakers: Speakers) : this({ speakers.system.value }, speakers.system.map { it != null }.distinctUntilChanged())

    override suspend fun options(): MusicOptions {
        val s = system() ?: return MusicOptions(emptyList(), emptyList(), CANT_REACH_SONOS)
        return attempt({
            s.checkLayout()
            val picks = (s.favourites() + s.playlists()).map { MusicPick(it.id, it.name) }
            val rooms = s.groups.value.flatMap { it.rooms }.map { HouseRoom(it.id, it.name) }
            MusicOptions(picks, rooms, null)
        }) { e ->
            logFailure(Step.OPTIONS, e)
            MusicOptions(emptyList(), emptyList(), optionsLine(e))
        }
    }

    override suspend fun before(): List<GroupBefore> {
        val s = system() ?: throw MusicUnreachableException(MusicProblem.UNREACHABLE)
        try {
            s.checkLayout()
        } catch (e: Exception) {
            logFailure(Step.BEFORE, e)
            throw e
        }
        return s.groups.value.map { g -> GroupBefore(g.coordinatorId, g.rooms.map { it.id }) }
    }

    override suspend fun start(pickId: String, roomIds: Set<String>?, before: List<GroupBefore>): MusicStart {
        val s = system() ?: return failedStart()
        val prepared = attempt({ prepare(s, pickId, roomIds) }) { e ->
            logFailure(Step.GROUP, e)
            null
        } ?: return failedStart()
        val made = attempt({ s.group(prepared.coordinator, prepared.chosen) }) { e ->
            logFailure(Step.GROUP, e)
            regroup(s, before)
            return failedStart()
        }
        attempt({ s.playFavourite(made, prepared.pick) }) { e ->
            logFailure(Step.PLAY, e)
            regroup(s, before)
            return failedStart()
        }
        val joined = made.rooms.map { it.id }.toSet()
        val step = if (joined == prepared.chosen) HouseStep.Done else HouseStep.Failed(HouseLines.MUSIC_DIDNT_START)
        return MusicStart(PartyGroup(made.coordinatorId, joined), step)
    }

    override suspend fun stop(made: PartyGroup, before: List<GroupBefore>): HouseStep {
        val s = system() ?: return HouseStep.Failed(HouseLines.MUSIC_DIDNT_STOP)
        val playing = attempt({
            s.checkLayout()
            s.playingNow()
        }) { e ->
            logFailure(Step.CHECK, e)
            return HouseStep.Failed(HouseLines.MUSIC_DIDNT_STOP)
        }
        val ours = playing.firstOrNull { stillParty(listOf(it), made) }
        if (ours == null) {
            Log.i(TAG, "Party music: left as changed by hand")
            return HouseStep.Done
        }
        attempt({ s.pause(ours) }) { e ->
            logFailure(Step.PAUSE, e)
            return HouseStep.Failed(HouseLines.MUSIC_DIDNT_STOP)
        }
        return regroup(s, before)
    }

    override suspend fun putBack(before: List<GroupBefore>): HouseStep {
        val s = system() ?: return HouseStep.Failed(HouseLines.SPEAKERS_NOT_BACK)
        return regroup(s, before)
    }

    private class Prepared(val chosen: Set<String>, val coordinator: String, val pick: Favourite)

    /** Nothing is sent: the rooms still there, the leader and the pick are found first (13 §2.13). Null when there's nothing to play or nowhere to play it. */
    private suspend fun prepare(s: MusicSystem, pickId: String, roomIds: Set<String>?): Prepared? {
        s.checkLayout()
        val layout = s.groups.value
        val inLayout = layout.flatMap { it.rooms }.map { it.id }.toSet()
        val chosen = roomIds?.intersect(inLayout) ?: inLayout
        val pick = (s.favourites() + s.playlists()).firstOrNull { it.id == pickId }
        val coordinator = partyCoordinator(layout, chosen)
        return if (pick == null || coordinator == null) null else Prepared(chosen, coordinator, pick)
    }

    private suspend fun regroup(s: MusicSystem, before: List<GroupBefore>): HouseStep = attempt({
        s.regroup(before.map { RoomGroup(it.coordinatorId, it.roomIds) })
        HouseStep.Done
    }) { e ->
        logFailure(Step.REGROUP, e)
        HouseStep.Failed(HouseLines.SPEAKERS_NOT_BACK)
    }

    private fun failedStart() = MusicStart(null, HouseStep.Failed(HouseLines.MUSIC_DIDNT_START))

    private fun optionsLine(e: Exception): String = when (e) {
        is MusicUnreachableException -> problemLine(e.problem)
        is MusicRefusedException -> SONOS_REFUSED
        else -> CANT_REACH_SONOS
    }

    private fun logFailure(step: Step, e: Exception) {
        Log.w(TAG, "Party music: ${step.name} failed (${e::class.simpleName})")
    }
}
