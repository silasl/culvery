package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 6 §4.6: each room of each playing group, remembered before it is set; groups in parallel; restore within 1 of what was
 * set, tried twice; a second ring; a group that can't be read. Kitchen plays alone at 50; Lounge (40) leads Dining room
 * (25), playing; Bedroom is paused.
 */
@RunWith(AndroidJUnit4::class)
class MusicQuieterTest {
    private val kitchen = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG, volume = 50)
    private val lounge = group("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = STATION, volume = 40)
    private val bedroom = group("b", "Bedroom", state = PlayState.PAUSED, nowPlaying = SONG, volume = 30)
    private val system = TestMusicSystem(listOf(kitchen, lounge, bedroom)).apply { roomVolumes["Dining room-id"] = 25 }
    private var share = 20

    private fun quieter(s: MusicSystem? = system) = MusicQuieter({ s }, { share })

    private fun sets() = system.calls.filter { it.startsWith("setRoomVolume") }

    @Test
    fun eachRoomOfEachPlayingGroupDropsToItsShare() = runTest {
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10", "setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5")
    }

    /** Guard: a group on an idle input still sounds if a track plays later, so its rooms are lowered like any playing group. */
    @Test
    fun aGroupPlayingAnInputIsStillLowered() = runTest {
        system.groups.value = listOf(group("o", "Office", state = PlayState.PLAYING, nowPlaying = NowPlaying.Input("Bluetooth"), volume = 50))
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Office-id 10")
    }

    /** round(v × share): 33 × 20 % is 6.6, so 7. */
    @Test
    fun theShareRounds() = runTest {
        system.roomVolumes["Kitchen-id"] = 33
        system.groups.value = listOf(kitchen)
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 7")
    }

    /** 6 §4.6: room by room keeps a group's balance, even from 0 % (a group volume of 0 would lose it). */
    @Test
    fun aTwoRoomGroupComesBackWithItsBalanceEvenFromNothing() = runTest {
        system.groups.value = listOf(lounge)
        share = 0
        val q = quieter()
        q.quiet()
        assertThat(sets()).containsExactly("setRoomVolume Lounge-id 0", "setRoomVolume Dining room-id 0")
        system.calls.clear()
        q.restore()
        assertThat(sets()).containsExactly("setRoomVolume Lounge-id 40", "setRoomVolume Dining room-id 25")
        share = 20
        system.calls.clear()
        q.quiet()
        q.restore()
        assertThat(sets()).containsExactly(
            "setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5", "setRoomVolume Lounge-id 40", "setRoomVolume Dining room-id 25",
        )
    }

    /** Fails if one group is lowered only after another finished. */
    @Test
    fun groupsAreLoweredInParallel() = runTest {
        val slow = CompletableDeferred<Unit>()
        system.gates["roomVolume Kitchen-id"] = slow
        val q = quieter()
        val quieting = launch { q.quiet() }
        runCurrent()
        assertThat(sets()).containsExactly("setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5")
        slow.complete(Unit)
        quieting.join()
        assertThat(sets()).contains("setRoomVolume Kitchen-id 10")
    }

    /** 6 §2.3: someone turned the Dining room up while the card showed; it stays as they set it. */
    @Test
    fun restorePutsBackOnlyRoomsNobodyChanged() = runTest {
        val q = quieter()
        q.quiet()
        system.roomVolumes["Dining room-id"] = 15
        system.calls.clear()
        q.restore()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 50", "setRoomVolume Lounge-id 40")
    }

    /** 6 §4.6: within 1 of what was set still restores (Sonos may round); 2 away is someone's change. */
    @Test
    fun aDriftOfOneStillRestores() = runTest {
        val q = quieter()
        q.quiet()
        system.roomVolumes["Kitchen-id"] = 11
        system.roomVolumes["Lounge-id"] = 10
        system.calls.clear()
        q.restore()
        assertThat(sets()).contains("setRoomVolume Kitchen-id 50")
        assertThat(sets()).doesNotContain("setRoomVolume Lounge-id 40")
    }

    /** 6 §4.6: the speaker took the volume but its answer was lost. Fails if a room is remembered only once its command returns. */
    @Test
    fun aCommandThatTookEffectButTimedOutIsStillRestored() = runTest {
        system.failures["setRoomVolume Kitchen-id 10"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        val q = quieter()
        q.quiet()
        system.failures.clear()
        system.calls.clear()
        q.restore()
        assertThat(sets()).contains("setRoomVolume Kitchen-id 50")
    }

    /** 6 §4.6: a failed restore is tried once more. */
    @Test
    fun aFailedRestoreIsTriedOnceMore() = runTest {
        val q = quieter()
        q.quiet()
        system.failOnce["roomVolume Kitchen-id"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        system.calls.clear()
        q.restore()
        assertThat(system.calls.count { it == "roomVolume Kitchen-id" }).isEqualTo(2)
        assertThat(sets()).contains("setRoomVolume Kitchen-id 50")
    }

    /** 6 §4.6: a second ring while lowered does nothing; after the restore the next ring lowers again. */
    @Test
    fun aSecondQuietWhileLoweredDoesNothing() = runTest {
        val q = quieter()
        q.quiet()
        q.quiet()
        assertThat(sets()).hasSize(3)
        q.restore()
        system.calls.clear()
        q.quiet()
        assertThat(sets()).hasSize(3)
    }

    /** A group one of whose rooms can't be read is left as it is (6 §6); the others still drop and come back. */
    @Test
    fun aGroupThatCantBeReadIsSkipped() = runTest {
        system.failures["roomVolume Dining room-id"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        val q = quieter()
        q.quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10")
        system.failures.clear()
        system.calls.clear()
        q.restore()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 50")
    }

    /** 6 §4.6: on the Calendar tab no checks run, so the last-known state says nothing plays. */
    @Test
    fun lowersWhatIsPlayingNowNotWhatTheLastCheckSaw() = runTest {
        system.groups.value = listOf(kitchen.copy(state = PlayState.STOPPED))
        system.playingNowAnswer = listOf(kitchen)
        quieter().quiet()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10")
    }

    /** 6 §4.6: a restore asked while the quiet is still lowering waits for it, then puts everything back. */
    @Test
    fun aRestoreWaitsForTheQuietItUndoes() = runTest {
        val slow = CompletableDeferred<Unit>()
        system.gates["setRoomVolume Kitchen-id 10"] = slow
        val q = quieter()
        val quieting = launch { q.quiet() }
        runCurrent()
        val restoring = launch { q.restore() }
        runCurrent()
        assertThat(sets()).containsExactly("setRoomVolume Kitchen-id 10", "setRoomVolume Lounge-id 8", "setRoomVolume Dining room-id 5")
        slow.complete(Unit)
        quieting.join()
        restoring.join()
        assertThat(sets()).containsAtLeast("setRoomVolume Kitchen-id 50", "setRoomVolume Lounge-id 40", "setRoomVolume Dining room-id 25")
    }

    /** Neither a missing system nor a failed read of what plays throws into the shell. */
    @Test
    fun withNoSystemOrNoAnswerNothingHappens() = runTest {
        quieter(s = null).quiet()
        system.failures["playingNow"] = IOException("no speaker")
        val q = quieter()
        q.quiet()
        q.restore()
        assertThat(sets()).isEmpty()
    }

    /** The answer the doorbell's "Music lowered" chip rests on. */
    @Test
    fun quietSaysWhetherItLoweredARoom() = runTest {
        assertThat(quieter(s = null).quiet()).isFalse()

        system.failures["playingNow"] = IOException("no speaker")
        assertThat(quieter().quiet()).isFalse()
        system.failures.clear()

        system.groups.value = listOf(bedroom)
        system.playingNowAnswer = emptyList()
        assertThat(quieter().quiet()).isFalse()
        system.playingNowAnswer = null

        val office = group("o", "Office", state = PlayState.PLAYING, nowPlaying = SONG, volume = 40)
        system.groups.value = listOf(office)
        share = 30
        val q = quieter()
        assertThat(q.quiet()).isTrue()
        assertThat(q.quiet()).isTrue()
        q.restore()

        system.failures["setRoomVolume Office-id 12"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(quieter().quiet()).isFalse()
        system.failures.clear()

        system.roomVolumes["Office-id"] = 40
        share = 100
        assertThat(quieter().quiet()).isFalse()
        assertThat(system.roomVolumes["Office-id"]).isEqualTo(40)
    }
}
