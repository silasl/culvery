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

    private val film = StoredMode("modeaaaaaa", "Film night", "movie", showOnHome = true, needsPin = true)

    @Test
    fun holidaysSummary() {
        assertThat(ModesWords.holidaySummary(on = false, onHome = true)).isEqualTo("Built in · On Home · PIN · Lights, heating, hoover, plugs, door chime")
        assertThat(ModesWords.holidaySummary(on = true, onHome = false)).isEqualTo("On · Built in · Not on Home · PIN · Lights, heating, hoover, plugs, door chime")
    }

    @Test
    fun anOnModesSummary() {
        val mode = film.copy(
            music = MusicChoice("FV:2/2", "Sunday morning"), speakers = listOf(KITCHEN, LOUNGE, DINING),
            lights = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX),
        )

        assertThat(ModesWords.summary(mode, on = true, plugs = 1)).isEqualTo("On · On Home · PIN · Sunday morning on 3 speakers · 2 rooms · 1 plug")
        assertThat(ModesWords.summary(mode, on = false, plugs = 0).startsWith("On Home")).isTrue()
    }

    @Test
    fun everySpeaker() {
        val mode = film.copy(music = MusicChoice("FV:2/3", "Party mix"), showOnHome = false, needsPin = false)

        assertThat(ModesWords.summary(mode, on = false, plugs = 0)).isEqualTo("Not on Home · No PIN · Party mix on every speaker")
        assertThat(ModesWords.summary(mode.copy(speakers = listOf(KITCHEN)), on = false, plugs = 0)).endsWith("Party mix on 1 speaker")
    }

    @Test
    fun oneRoomOnePlug() {
        assertThat(ModesWords.summary(film.copy(lights = listOf(KITCHEN_BRIGHT)), on = false, plugs = 3)).isEqualTo("On Home · PIN · 1 room · 3 plugs")
        assertThat(ModesWords.summary(film, on = false, plugs = 1)).isEqualTo("On Home · PIN · 1 plug")
    }

    @Test
    fun nothingChosen() {
        assertThat(ModesWords.summary(film, on = false, plugs = 0)).isEqualTo("On Home · PIN · Nothing chosen yet")
    }

    /** Review Focus 4: the name is checked trimmed, against the other modes' and Holiday's. */
    @Test
    fun nameProblems() {
        val taken = listOf("Party mode")

        assertThat(ModesWords.nameProblem("   ", taken)).isEqualTo("Type a name for the mode.")
        assertThat(ModesWords.nameProblem("  party MODE ", taken)).isEqualTo("There’s already a mode called Party mode.")
        assertThat(ModesWords.nameProblem("Holiday Mode", taken)).isEqualTo("There’s already a mode called Holiday mode.")
        assertThat(ModesWords.nameProblem("a".repeat(24), taken)).isNull()
        assertThat(ModesWords.nameProblem("a".repeat(25), taken)).isEqualTo("Keep the name to 24 characters or fewer.")
        assertThat(ModesWords.nameProblem("Film\u001Fnight", taken)).isEqualTo("Use letters, numbers and spaces in the name.")
        assertThat(ModesWords.nameProblem("Film\nnight", taken)).isEqualTo("Use letters, numbers and spaces in the name.")
        assertThat(ModesWords.nameProblem(" Film night ", taken)).isNull()
    }
}
