package uk.co.siland.culvery.capability.party

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.sound.PadStart

/** 18 §4.4, Ruling 6: what a tap plays, and each pad's line under the row. */
@OptIn(ExperimentalCoroutinesApi::class)
class PadTapsTest {
    private val player = FakePlayer()

    private fun TestScope.taps(rig: GateRig = GateRig()) =
        PadTaps(player::play, player::stop, player.playing, rig.gate, { rig.door?.volume ?: 1f }, backgroundScope)

    private val pad1 = PadUi(1, TROMBONE)
    private val pad2 = PadUi(2, HONK)

    /** Fails if a tap doesn't play its pad at the doorbell's volume, or full volume with no door. */
    @Test
    fun aTapPlaysAtTheDoorsVolume() = runTest {
        taps().tap(pad2)
        taps(GateRig(door = null)).tap(pad1)

        assertThat(player.plays).containsExactly(2 to 0.6f, 1 to 1f).inOrder()
    }

    /** Fails if "Couldn’t play" isn't on the failed pad's own line, or stays past 4 s. */
    @Test
    fun aFailedPlaySaysCouldntPlayForFourSeconds() = runTest {
        val taps = taps()
        player.starts = false

        taps.tap(pad2)
        runCurrent()

        assertThat(taps.line(2, first = false).first()).isEqualTo("Couldn’t play")
        assertThat(taps.line(1, first = true).first()).isNull()
        advanceTimeBy(4_001)
        assertThat(taps.line(2, first = false).first()).isNull()
    }

    /** Controller ruling (Task 3): fails if a pad refused because a ring arrived says "Couldn’t play" rather than the Doorbell block. */
    @Test
    fun aRingWhileOpeningShowsTheDoorbellBlock() = runTest {
        val rig = GateRig()
        val taps = taps(rig)
        player.starts = false
        player.whilePlaying = { rig.door!!.ring.value = true }

        taps.tap(pad2)
        runCurrent()

        assertThat(taps.line(2, first = false).first()).isNull()
        assertThat(taps.line(1, first = true).first()).isEqualTo("Doorbell")
    }

    /** Fix round 1: fails if "Couldn’t play" stays under a pad that has since started. */
    @Test
    fun aPadThatPlaysAfterFailingLosesItsLine() = runTest {
        val taps = taps()
        player.starts = false
        taps.tap(pad2)
        player.starts = true

        taps.tap(pad2)

        assertThat(taps.line(2, first = false).first()).isNull()
        assertThat(player.playing.value).isEqualTo(2)
    }

    /** Fix round 1: fails if a block that starts while a pad plays keeps it from being stopped, or lets another play. */
    @Test
    fun thePlayingPadStopsEvenWhenBlocked() = runTest {
        val rig = GateRig()
        val taps = taps(rig)
        player.playing.value = 1
        rig.house.holiday.value = true

        taps.tap(pad1)
        taps.tap(pad2)

        assertThat(player.stops).isEqualTo(1)
        assertThat(player.plays).isEmpty()
    }

    /** Ruling 6: fails if a pad shows any line, even a failed play's, before the gate's first read. */
    @Test
    fun padsAreFadedWithNoLineUntilTheGateIsRead() = runTest {
        val rig = GateRig()
        val taps = taps(rig)
        player.starts = false
        taps.tap(pad2)

        assertThat(taps.gateState.value).isEqualTo(GateState(read = false, block = null))
        assertThat(taps.line(2, first = false).first()).isNull()

        rig.night = true
        runCurrent()

        assertThat(taps.gateState.value).isEqualTo(GateState(read = true, block = PadBlock.NIGHT))
        assertThat(taps.line(1, first = true).first()).isEqualTo("Night")
        assertThat(taps.line(2, first = false).first()).isEqualTo("Couldn’t play")
    }

    /** Fails if a refusal for the doorbell, or a stop, shows "Couldn’t play". */
    @Test
    fun onlyAFailedPlayShowsCouldntPlay() = runTest {
        val taps = taps()
        runCurrent()
        player.outcomes += listOf(PadStart.DOORBELL, PadStart.STOPPED)

        taps.tap(pad1)
        assertThat(taps.line(1, first = false).first()).isNull()
        taps.tap(pad2)
        assertThat(taps.line(2, first = false).first()).isNull()
    }

    /** Ruling 6: fails if a pad but the first shown carries the block, or the first doesn't. */
    @Test
    fun theFirstPadCarriesTheBlock() = runTest {
        val rig = GateRig()
        rig.night = true
        val taps = taps(rig)
        runCurrent()

        assertThat(taps.line(1, first = true).first()).isEqualTo("Night")
        assertThat(taps.line(2, first = false).first()).isNull()
    }

    /** Fails if a second tap on a pad that is still opening doesn't stop it, or the stopped first tap shows a failure. */
    @Test
    fun aSecondTapOnAnOpeningPadStopsIt() = runTest {
        val taps = taps()
        val release = CompletableDeferred<Unit>()
        player.starts = false
        player.whilePlaying = { if (player.plays.size == 1) release.await() }

        val first = launch { taps.tap(pad2) }
        runCurrent()
        taps.tap(pad2)
        release.complete(Unit)
        first.join()
        runCurrent()

        assertThat(taps.gateState.value.read).isTrue()
        assertThat(player.stops).isEqualTo(1)
        assertThat(player.plays).hasSize(1)
        assertThat(taps.line(2, first = false).first()).isNull()
    }
}
