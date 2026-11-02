package uk.co.siland.culvery.capability.party

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModesWordsTest {
    /** 15b §7: fails if the nothing-to-do line differs from the copy. */
    @Test
    fun nothingToDo() {
        assertThat(ModesWords.NOTHING_TO_DO).isEqualTo("Nothing to do yet. Choose music, lights or plugs in Settings › Modes.")
    }

    /** Ruling 2: the PIN pad names the action with the mode. */
    @Test
    fun turnLabels() {
        assertThat(ModesWords.turnLabel("Film night", on = true)).isEqualTo("Turn Film night on")
        assertThat(ModesWords.turnLabel("Film night", on = false)).isEqualTo("Turn Film night off")
        assertThat(ModesWords.turnOffWithPin("Film night")).isEqualTo("Turn Film night off with a PIN.")
    }

    @Test
    fun spoken() {
        assertThat(ModesWords.tileSpoken("Film night", false)).isEqualTo("Film night, off.")
        assertThat(ModesWords.tileSpoken("Film night", true)).isEqualTo("Film night, on.")
        assertThat(ModesWords.runningSpoken("Film night", PartyRun.TURNING_ON)).isEqualTo("Film night, turning on.")
        assertThat(ModesWords.runningSpoken("Film night", PartyRun.TURNING_OFF)).isEqualTo("Film night, turning off.")
    }
}
