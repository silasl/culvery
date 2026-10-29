package uk.co.siland.culvery.capability.party

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.house.PlugChange

class PartyWordsTest {
    private val plugs = listOf(PlugChange("Lamp", false), PlugChange("Desk", true))

    /** 13 §5: fails if a line differs from the copy or the lines run in another order. */
    @Test
    fun willLinesInOrder() {
        val plan = PartyPlan(plugs, PARTY_MIX, listOf(KITCHEN_BRIGHT))

        assertThat(PartyWords.willLines(plan)).containsExactly(
            "Switch Lamp off.",
            "Switch Desk on.",
            "Plugs go back as they were when you turn it off.",
            "Play Party mix on every speaker.",
            "Bright in Kitchen.",
        ).inOrder()
    }

    @Test
    fun someSpeakersAreNamed() {
        val plan = PartyPlan(emptyList(), PARTY_MIX, emptyList(), rooms = listOf(KITCHEN, LOUNGE))

        assertThat(PartyWords.willLines(plan)).containsExactly("Play Party mix on Kitchen and Lounge.")
    }

    @Test
    fun nothingToDo() {
        val plan = PartyPlan(emptyList(), null, emptyList())

        assertThat(plan.nothing).isTrue()
        assertThat(PartyWords.willLines(plan))
            .containsExactly("Party mode has nothing to do yet. Choose plugs, music and lights in Settings › Party mode.")
    }

    @Test
    fun backLines() {
        val record = PartyRecord(on = true, made = MADE, lights = LIGHTS_BEFORE)

        assertThat(PartyWords.backLines(record, plugsListed = true)).containsExactly(
            "Stop the music and put the speakers back in their groups.",
            "Put the plugs back as they were.",
            "Put the lights back in Kitchen and Lounge.",
            "Anything changed by hand during the party stays as it is.",
        ).inOrder()
        assertThat(PartyWords.backLines(PartyRecord(on = true), plugsListed = false))
            .containsExactly("Party mode changed nothing, so there’s nothing to put back.")
    }

    @Test
    fun spoken() {
        assertThat(PartyWords.tileSpoken(false)).isEqualTo("Party mode, off.")
        assertThat(PartyWords.tileSpoken(true)).isEqualTo("Party mode, on.")
        assertThat(PartyWords.runningSpoken(PartyRun.TURNING_ON)).isEqualTo("Party mode, turning on.")
        assertThat(PartyWords.runningSpoken(PartyRun.TURNING_OFF)).isEqualTo("Party mode, turning off.")
    }

    /** 13 §2.14: fails if the plan can be read out of a log line. */
    @Test
    fun aPlanHidesItsChoices() {
        assertThat(PartyPlan(plugs, PARTY_MIX, listOf(KITCHEN_BRIGHT)).toString()).doesNotContain("Lamp")
    }
}
