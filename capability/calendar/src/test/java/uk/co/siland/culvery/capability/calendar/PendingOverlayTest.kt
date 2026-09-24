package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId

class PendingOverlayTest {
    private val london = ZoneId.of("Europe/London")
    private val family = StoredSource("c1", CalendarSource("s1", "Family calendar", writable = true), SourceMapping.Default, isMaster = true)
    private val hidden = StoredSource("c1", CalendarSource("s2", "Hidden", writable = true), SourceMapping(PersonId.FAMILY, visible = false))
    private val sources = mapOf("s1" to family, "s2" to hidden)

    private fun at(hour: Int) = EventTime.Timed(LocalDateTime.of(2026, 9, 23, hour, 0).atZone(london).toInstant())

    private fun stored(id: String, hour: Int) = StoredEvent(
        "c1", "s1", id, id, at(hour), at(hour + 1), recurring = false, forPerson = null, createdBy = null,
        sourcePerson = PersonId.FAMILY, startSort = at(hour).instant.toEpochMilli(), endSort = at(hour + 1).instant.toEpochMilli(),
    )

    private fun change(id: Long, kind: ChangeKind, remoteId: String?, draft: EventDraft?, sourceId: String = "s1") =
        PendingChange(id, "c1", sourceId, remoteId, kind, draft, attempts = 0, nextAttemptMillis = 0, createdMillis = 0)

    private fun overlay(events: List<StoredEvent>, pending: List<PendingChange>) =
        overlayPending(events, pending, { _, s -> sources[s] }, london, Long.MIN_VALUE, Long.MAX_VALUE)

    @Test
    fun withNothingQueuedTheMirrorShowsAsItIs() {
        assertThat(overlay(listOf(stored("a", 9)), emptyList()).map { it.event.remoteId to it.syncing }).containsExactly("a" to false)
    }

    @Test
    fun aQueuedDeleteHidesTheEvent() {
        val shown = overlay(listOf(stored("a", 9), stored("b", 10)), listOf(change(1, ChangeKind.DELETE, "a", null)))
        assertThat(shown.map { it.event.remoteId }).containsExactly("b")
    }

    @Test
    fun aQueuedUpdateShowsTheDraftAsSyncingAndMovesItsSortTimes() {
        val draft = EventDraft("Moved", at(14), at(15), forPerson = "sam", createdBy = null)
        val shown = overlay(listOf(stored("a", 9)), listOf(change(1, ChangeKind.UPDATE, "a", draft))).single()
        assertThat(shown.syncing).isTrue()
        assertThat(listOf(shown.event.title, shown.event.forPerson)).containsExactly("Moved", "sam").inOrder()
        assertThat(shown.event.startSort).isEqualTo(at(14).instant.toEpochMilli())
    }

    @Test
    fun aQueuedAssignChangesOnlyThePerson() {
        // Queued before the event was renamed and moved on a phone; the mirror has the new title and time.
        val stale = EventDraft("Old title", at(7), at(8), forPerson = "sam", createdBy = null)
        val shown = overlay(listOf(stored("a", 9)), listOf(change(1, ChangeKind.ASSIGN, "a", stale))).single()
        assertThat(shown.syncing).isTrue()
        assertThat(listOf(shown.event.title, shown.event.forPerson)).containsExactly("a", "sam").inOrder()
        assertThat(shown.event.startSort).isEqualTo(at(9).instant.toEpochMilli())
    }

    @Test
    fun laterChangesWinInQueueOrder() {
        val first = EventDraft("a", at(9), at(10), forPerson = "sam", createdBy = null)
        val second = first.copy(forPerson = "mia")
        val shown = overlay(
            listOf(stored("a", 9)),
            listOf(change(1, ChangeKind.UPDATE, "a", first), change(2, ChangeKind.UPDATE, "a", second)),
        )
        assertThat(shown.single().event.forPerson).isEqualTo("mia")
        val deleted = overlay(listOf(stored("a", 9)), listOf(change(1, ChangeKind.UPDATE, "a", first), change(2, ChangeKind.DELETE, "a", null)))
        assertThat(deleted).isEmpty()
    }

    @Test
    fun aQueuedCreateAppearsAsSyncingUnlessItsSourceIsHidden() {
        val draft = EventDraft("Sleepover", at(18), at(19), forPerson = "mia", createdBy = "mia")
        val shown = overlay(emptyList(), listOf(change(7, ChangeKind.CREATE, null, draft), change(8, ChangeKind.CREATE, null, draft, sourceId = "s2")))
        assertThat(shown.map { it.event.remoteId to it.syncing }).containsExactly("pending-7" to true)
    }

    @Test
    fun anUpdateForAnEventNotShownIsIgnored() {
        val draft = EventDraft("Ghost", at(9), at(10), null, null)
        assertThat(overlay(emptyList(), listOf(change(1, ChangeKind.UPDATE, "gone", draft)))).isEmpty()
    }

    @Test
    fun theWindowLimitsWhatIsShown() {
        val shown = overlayPending(
            listOf(stored("a", 9), stored("b", 20)), emptyList(), { _, s -> sources[s] }, london,
            at(8).instant.toEpochMilli(), at(12).instant.toEpochMilli(),
        )
        assertThat(shown.map { it.event.remoteId }).containsExactly("a")
    }
}
