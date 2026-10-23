package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import kotlinx.coroutines.awaitCancellation
import org.junit.Test
import uk.co.siland.culvery.core.plugin.couldNotConnect

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

    /** Only a failure before anything left the tablet counts as never sent; a reply that never came may hide a write. */
    @Test
    fun aRetrySaysWhetherTheWriteMayHaveBeenSent() = kotlinx.coroutines.test.runTest {
        suspend fun sent(failure: Throwable) = (callWriter(coroutineContext, 1_000) { throw failure } as WriteOutcome.Retry).sent
        assertThat(sent(OfflineException())).isFalse()
        assertThat(sent(UnreachableException("Couldn't reach Google Calendar", UnknownHostException()))).isFalse()
        assertThat(sent(UnreachableException("Couldn't reach Google Calendar", ConnectException()))).isFalse()
        assertThat((callWriter(coroutineContext, 1_000) { awaitCancellation() } as WriteOutcome.Retry).sent).isTrue()
        assertThat(sent(UnreachableException("Google Calendar asked to try later (503)"))).isTrue()
        // OkHttp retried the POST on a fresh route after the first one failed mid-send.
        val retried = ConnectException().apply { addSuppressed(SocketException("Connection reset")) }
        assertThat(sent(UnreachableException("Couldn't reach Google Calendar", retried))).isTrue()
    }

    @Test
    fun theAddedLine() {
        val sat12Dec = java.time.LocalDate.of(2026, 12, 12)
        assertThat(addedLine("Guitar lesson", null, null)).isEqualTo("Added Guitar lesson.")
        assertThat(addedLine("Guitar lesson", "Every Saturday", null)).isEqualTo("Added Guitar lesson, every Saturday.")
        assertThat(addedLine("Guitar lesson", null, sat12Dec)).isEqualTo("Added Guitar lesson, Sat 12 Dec.")
        assertThat(addedLine("Guitar lesson", "Every Saturday", sat12Dec)).isEqualTo("Added Guitar lesson, every Saturday from Sat 12 Dec.")
    }

    @Test
    fun undoSaysWhatHappened() {
        assertThat(removed("Dinner")).isEqualTo("Removed Dinner.")
        assertThat(couldNotUndo(gc)).isEqualTo("Couldn’t undo — remove it in Google Calendar on your phone.")
    }
}
