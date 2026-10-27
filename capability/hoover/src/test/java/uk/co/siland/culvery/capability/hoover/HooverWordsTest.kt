package uk.co.siland.culvery.capability.hoover

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HooverWordsTest {
    private fun status(activity: HooverActivity?, battery: Int?, problem: String? = null) = hooverStatus(HooverState(activity, battery, problem))

    private fun spoken(activity: HooverActivity?, battery: Int?, problem: String? = null) = hooverSpoken(HooverState(activity, battery, problem))

    /** 11 §4.4 Copy: fails if a status word, the dot or the percent sign differs. */
    @Test
    fun eachStatusIsInCopy() {
        assertThat(status(HooverActivity.DOCKED, 86)).isEqualTo("Docked · 86%")
        assertThat(status(HooverActivity.CLEANING, 60)).isEqualTo("Cleaning · 60%")
        assertThat(status(HooverActivity.RETURNING, 40)).isEqualTo("Going home · 40%")
        assertThat(status(HooverActivity.PAUSED, 55)).isEqualTo("Paused · 55%")
        assertThat(status(HooverActivity.IDLE, 55)).isEqualTo("Idle · 55%")
    }

    /** Fails if a missing battery leaves a dangling dot or a made-up percentage. */
    @Test
    fun noBatteryIsTheWordAlone() {
        assertThat(status(HooverActivity.DOCKED, null)).isEqualTo("Docked")
    }

    /** Fails if an unknown state isn't "Checking…", or a stuck hoover shows its battery. */
    @Test
    fun unknownAndStuck() {
        assertThat(status(null, null)).isEqualTo("Checking…")
        assertThat(status(HooverActivity.ERROR, 30)).isEqualTo("Stuck. Check the Eufy app.")
    }

    /** Fails if a problem doesn't replace the status. */
    @Test
    fun aProblemReplacesTheStatus() {
        assertThat(status(HooverActivity.DOCKED, 86, "Can’t reach Home Assistant. Check the NAS is on."))
            .isEqualTo("Can’t reach Home Assistant. Check the NAS is on.")
    }

    /** 11 §4.4 TalkBack: fails if a spoken line differs from Copy's. */
    @Test
    fun theSpokenLines() {
        assertThat(spoken(HooverActivity.DOCKED, 86)).isEqualTo("Hoover, docked, battery 86 percent.")
        assertThat(spoken(HooverActivity.CLEANING, 60)).isEqualTo("Hoover, cleaning, battery 60 percent.")
        assertThat(spoken(HooverActivity.DOCKED, null)).isEqualTo("Hoover, docked.")
        assertThat(spoken(HooverActivity.DOCKED, 86, "Can’t reach Home Assistant. Check the NAS is on."))
            .isEqualTo("Hoover. Can’t reach Home Assistant. Check the NAS is on.")
    }

    /** Fails if an unknown or stuck hoover is spoken as a state it isn't in. */
    @Test
    fun theSpokenLinesForUnknownAndStuck() {
        assertThat(spoken(null, null)).isEqualTo("Hoover, checking.")
        assertThat(spoken(HooverActivity.ERROR, 30)).isEqualTo("Hoover. Stuck. Check the Eufy app.")
    }

    /** Fails if a refused start has no line, or a started one has. */
    @Test
    fun onlyAFailureHasALine() {
        assertThat(resultLine(HooverResult.NotStarted)).isEqualTo(NOT_STARTED)
        assertThat(resultLine(HooverResult.Failed("Line."))).isEqualTo("Line.")
        assertThat(resultLine(HooverResult.Started)).isNull()
        assertThat(resultLine(HooverResult.Busy)).isNull()
    }

    /** Fails if a hoover that was already cleaning leaves a line on the row. */
    @Test
    fun alreadyRunningHasNoLine() {
        assertThat(resultLine(HooverResult.AlreadyRunning)).isNull()
    }

    /** Fails if a button or notice word differs from Copy. */
    @Test
    fun theWordsAreCopys() {
        assertThat(listOf(HOOVER, START, START_SPOKEN, STARTING, NOT_STARTED, STUCK, NO_HOOVER_CHOSEN)).containsExactly(
            "Hoover", "Start", "Start cleaning", "Starting…", "The hoover didn’t start. Check the Eufy app.",
            "Stuck. Check the Eufy app.", "No hoover is chosen. Choose one in Settings › Other integrations.",
        ).inOrder()
    }

    /** Fails if the startable set drifts from docked, idle and paused (11 §4.4). */
    @Test
    fun startableWhileDockedIdleOrPaused() {
        assertThat(STARTABLE).containsExactly(HooverActivity.DOCKED, HooverActivity.IDLE, HooverActivity.PAUSED)
    }
}
