package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 4d-2 §7.2: each failure says what went wrong, by its cause, and what to do. */
class WriteCopyTest {
    private val gc = "Google Calendar"

    @Test
    fun aSaveSaysWhyByCause() {
        assertThat(couldNotSave(gc, WriteProblem.Tablet)).isEqualTo("Couldn’t save — try again.")
        assertThat(couldNotSave(gc, WriteProblem.Refused(null))).isEqualTo("Google Calendar wouldn’t accept this change.")
        assertThat(couldNotSave(gc, WriteProblem.Refused(EVENT_GONE))).isEqualTo("Couldn’t save to Google Calendar — the event has been deleted.")
    }

    @Test
    fun aDeleteNeverSaysSave() {
        assertThat(couldNotDelete(gc, WriteProblem.Tablet)).isEqualTo("Couldn’t delete — try again.")
        assertThat(couldNotDelete(gc, WriteProblem.Refused(null))).isEqualTo("Google Calendar wouldn’t accept this change.")
        assertThat(couldNotDelete(gc, WriteProblem.Refused("this calendar can’t be changed from the tablet")))
            .isEqualTo("Couldn’t delete — this calendar can’t be changed from the tablet.")
    }

    @Test
    fun theFailureCardsBodySaysWhatToDoNext() {
        assertThat(saveFailedBody(gc, WriteProblem.Tablet)).isEqualTo("Nothing was saved. Your details are still here — try again.")
        assertThat(saveFailedBody(gc, WriteProblem.Refused("x")))
            .isEqualTo("Nothing was saved. Your details are still here to add in Google Calendar on your phone.")
    }

    @Test
    fun theDrainSaysWhatWasLost() {
        assertThat(changesLost(gc, listOf(EVENT_GONE))).isEqualTo("A change couldn’t be saved to Google Calendar and was lost — the event has been deleted.")
        assertThat(changesLost(gc, listOf(null))).isEqualTo("A change couldn’t be saved to Google Calendar and was lost.")
        assertThat(changesLost(gc, listOf(null, "x", null))).isEqualTo("3 changes couldn’t be saved to Google Calendar and were lost.")
    }

    @Test
    fun connectingSaysWhetherTheServiceCouldBeReached() {
        assertThat(couldNotConnect(gc, unreachable = true)).isEqualTo("Couldn’t reach Google Calendar — check the tablet’s Wi-Fi, then try again.")
        assertThat(couldNotConnect(gc, unreachable = false)).isEqualTo("Couldn’t connect to Google Calendar — try again.")
    }

    @Test
    fun aRefusalWithNoReasonOfItsOwnHasNone() = kotlinx.coroutines.test.runTest {
        val outcome = callWriter(coroutineContext, 1_000) { throw WriteRejectedException() }
        assertThat(outcome).isEqualTo(WriteOutcome.Rejected(null))
    }

    @Test
    fun lengthsAreInFullWords() {
        assertThat(listOf(1L, 30L, 60L, 90L, 120L, 135L).map { lengthLabel(java.time.Duration.ofMinutes(it)) })
            .containsExactly("1 minute", "30 minutes", "1 hour", "1 hour 30 minutes", "2 hours", "2 hours 15 minutes").inOrder()
    }
}
