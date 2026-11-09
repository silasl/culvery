package uk.co.siland.culvery.capability.party

import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.plugin.WallClock

/** 18 §7: when the pads may play, and the reason shown when they may not. */
@OptIn(ExperimentalCoroutinesApi::class)
class PadGateTest {
    /** Fails if anything else is the reason while the ring card shows, or the reasons' words differ from the plan. */
    @Test
    fun aRingCardBlocksFirst() = runTest {
        val rig = GateRig()
        rig.door!!.ring.value = true
        rig.door.quiet = true
        rig.night = true
        rig.house.holiday.value = true

        assertThat(rig.gate.blockedNow()).isEqualTo(PadBlock.DOORBELL)
        assertThat(PadBlock.entries.map { it.line }).containsExactly("Doorbell", "Night", "Holiday on", "Quiet hours").inOrder()
    }

    /** Fails if a night not known yet lets the pads play, as the door counts it as night. */
    @Test
    fun unknownNightGreysThePads() = runTest {
        val rig = GateRig()
        rig.night = null
        rig.house.holiday.value = true

        assertThat(rig.gate.blockedNow()).isEqualTo(PadBlock.NIGHT)
    }

    @Test
    fun holidayBlocks() = runTest {
        val rig = GateRig()
        rig.house.holiday.value = true
        rig.door!!.quiet = true

        assertThat(rig.gate.blockedNow()).isEqualTo(PadBlock.HOLIDAY)
    }

    /** Fails if quiet hours block with no door, or not with one. */
    @Test
    fun quietHoursBlockOnlyWithADoor() = runTest {
        val rig = GateRig()
        rig.door!!.quiet = true

        assertThat(rig.gate.blockedNow()).isEqualTo(PadBlock.QUIET)
        assertThat(GateRig(door = null).gate.blockedNow()).isNull()
    }

    @Test
    fun aClearDayBlocksNothing() = runTest {
        assertThat(GateRig().gate.blockedNow()).isNull()
    }

    /** Fails if the reason doesn't follow the ring card and Holiday at once, and the clock within a minute. */
    @Test
    fun theReasonFollowsTheRingHolidayAndTheMinute() = runTest {
        val rig = GateRig()
        val seen = mutableListOf<PadBlock?>()
        backgroundScope.launch { rig.gate.blocked.toList(seen) }
        runCurrent()

        rig.door!!.ring.value = true
        runCurrent()
        rig.door.ring.value = false
        rig.house.holiday.value = true
        runCurrent()
        rig.house.holiday.value = false
        runCurrent()
        rig.night = true
        advanceTimeBy(60_001)

        assertThat(seen).containsExactly(null, PadBlock.DOORBELL, PadBlock.HOLIDAY, null, PadBlock.NIGHT).inOrder()
    }

    /** Fails if the gate is read each 60 s from the first read rather than on the clock's minute. */
    @Test
    fun theGateIsReadOnTheMinute() = runTest {
        val start = 22 * 3_600_000L - 30_000L
        val now = { start + testScheduler.currentTime }
        val gate = PadGate(Optional.empty(), FakeHoliday(), { now() >= 22 * 3_600_000L }, WallClock { now() })
        val seen = mutableListOf<Pair<Long, PadBlock?>>()
        backgroundScope.launch { gate.blocked.collect { seen += testScheduler.currentTime to it } }

        advanceTimeBy(30_001)

        assertThat(seen).containsExactly(0L to null, 30_000L to PadBlock.NIGHT).inOrder()
    }
}
