package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncLabelsTest {
    private val now = 1_000_000_000_000L
    private fun minutesAgo(m: Long) = now - m * 60_000

    @Test
    fun underAMinuteIsJustNow() = assertThat(syncedLabel(now - 59_000, now)).isEqualTo("synced just now")

    @Test
    fun minutesUnderAnHour() {
        assertThat(syncedLabel(minutesAgo(1), now)).isEqualTo("synced 1 min ago")
        assertThat(syncedLabel(minutesAgo(59), now)).isEqualTo("synced 59 min ago")
    }

    @Test
    fun hoursUnderFortyEightThenDays() {
        assertThat(syncedLabel(minutesAgo(60), now)).isEqualTo("synced 1 h ago")
        assertThat(syncedLabel(minutesAgo(150), now)).isEqualTo("synced 2 h ago")
        assertThat(syncedLabel(minutesAgo(48 * 60 - 1), now)).isEqualTo("synced 47 h ago")
        assertThat(syncedLabel(minutesAgo(48 * 60), now)).isEqualTo("synced 2 days ago")
    }

    @Test
    fun neverSyncedSaysSo() = assertThat(syncedLabel(null, now)).isEqualTo("not synced yet")

    @Test
    fun syncTimeInTheFutureReadsJustNow() = assertThat(syncedLabel(now + 3_600_000, now)).isEqualTo("synced just now")

    @Test
    fun namesTheConnectionWhenGivenOne() {
        assertThat(syncedLabel(minutesAgo(2), now, "Google")).isEqualTo("synced with Google 2 min ago")
        assertThat(syncedLabel(now, now, "Google")).isEqualTo("synced with Google just now")
    }

    @Test
    fun subtitleNamesTheConnectionOnlyWhenThereIsExactlyOne() {
        val one = SyncStatusUi(minutesAgo(2), emptyList(), listOf("Google"), failingBeforeFirstSync = false)
        assertThat(weekSubtitle(one, now)).isEqualTo("Family calendar · synced with Google 2 min ago")
        val two = one.copy(connectionLabels = listOf("Google", "School"))
        assertThat(weekSubtitle(two, now)).isEqualTo("Family calendar · synced 2 min ago")
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
        val status = SyncStatusUi(minutesAgo(1), emptyList(), listOf("Google", "School"), failingBeforeFirstSync = true)
        assertThat(status.isStaleAt(now)).isTrue()
        assertThat(status.copy(failingBeforeFirstSync = false).isStaleAt(now)).isFalse()
    }
}
