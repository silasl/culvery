package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.ui.reconnectLabel

class SyncLabelsTest {
    private val now = 1_000_000_000_000L
    private fun minutesAgo(m: Long) = now - m * 60_000

    @Test
    fun underAMinuteIsJustNow() = assertThat(updatedLabel(now - 59_000, now)).isEqualTo("Updated just now")

    @Test
    fun minutesInFullWordsSingularAtOne() {
        assertThat(updatedLabel(minutesAgo(1), now)).isEqualTo("Updated 1 minute ago")
        assertThat(updatedLabel(minutesAgo(59), now)).isEqualTo("Updated 59 minutes ago")
    }

    @Test
    fun hoursUnderFortyEightThenDays() {
        assertThat(updatedLabel(minutesAgo(60), now)).isEqualTo("Updated 1 hour ago")
        assertThat(updatedLabel(minutesAgo(150), now)).isEqualTo("Updated 2 hours ago")
        assertThat(updatedLabel(minutesAgo(48 * 60 - 1), now)).isEqualTo("Updated 47 hours ago")
        assertThat(updatedLabel(minutesAgo(48 * 60), now)).isEqualTo("Updated 2 days ago")
    }

    @Test
    fun neverUpdatedSaysSo() = assertThat(updatedLabel(null, now)).isEqualTo("Not updated yet")

    @Test
    fun aClockThatWentBackwardsReadsJustNow() = assertThat(updatedLabel(now + 3_600_000, now)).isEqualTo("Updated just now")

    @Test
    fun namesTheServiceWhenGivenOne() {
        assertThat(updatedLabel(minutesAgo(5), now, "Google Calendar")).isEqualTo("Updated from Google Calendar 5 minutes ago")
        assertThat(updatedLabel(null, now, "Google Calendar")).isEqualTo("Not updated yet")
    }

    @Test
    fun theSubtitleNamesTheServiceOnlyWhenEveryConnectionHasTheSameOne() {
        val one = SyncStatusUi(minutesAgo(5), emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false)
        assertThat(weekSubtitle(one, now)).isEqualTo("Updated from Google Calendar 5 minutes ago")
        assertThat(weekSubtitle(one.copy(serviceNames = listOf("Google Calendar", "Google Calendar")), now)).isEqualTo("Updated from Google Calendar 5 minutes ago")
        assertThat(weekSubtitle(one.copy(serviceNames = listOf("Google Calendar", "School")), now)).isEqualTo("Updated 5 minutes ago")
        assertThat(weekSubtitle(one.copy(lastSyncMillis = null), now)).isEqualTo("Not updated yet")
    }

    @Test
    fun aStaleWeekSaysOutOfDateFirst() {
        val stale = SyncStatusUi(minutesAgo(180), emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false)
        assertThat(weekLine(stale, now)).isEqualTo("Out of date — updated from Google Calendar 3 hours ago")
        assertThat(weekLine(stale.copy(serviceNames = listOf("Google Calendar", "School")), now)).isEqualTo("Out of date — updated 3 hours ago")
        assertThat(weekLine(stale.copy(lastSyncMillis = null, failingBeforeFirstSync = true), now)).isEqualTo("Out of date — not updated yet")
        assertThat(weekLine(stale.copy(lastSyncMillis = minutesAgo(5)), now)).isEqualTo("Updated from Google Calendar 5 minutes ago")
    }

    @Test
    fun theReconnectChipCountsAccounts() {
        assertThat(reconnectLabel(listOf("Google Calendar"))).isEqualTo("Google Calendar needs reconnecting")
        assertThat(reconnectLabel(listOf("Google Calendar", "Google Calendar"))).isEqualTo("2 accounts need reconnecting")
    }

    @Test
    fun staleOnlyAfterThirtyMinutes() {
        assertThat(isStale(minutesAgo(30), now)).isFalse()
        assertThat(isStale(minutesAgo(30) - 1, now)).isTrue()
    }

    @Test
    fun neverSyncedIsNotStale() = assertThat(isStale(null, now)).isFalse()

    @Test
    fun aConnectionFailingBeforeItsFirstSyncMakesTheStatusStale() {
        val status = SyncStatusUi(minutesAgo(1), emptyList(), listOf("Google Calendar", "School"), failingBeforeFirstSync = true)
        assertThat(status.isStaleAt(now)).isTrue()
        assertThat(status.copy(failingBeforeFirstSync = false).isStaleAt(now)).isFalse()
    }
}
