package uk.co.siland.culvery.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgoTest {
    private val now = 1_000_000_000_000L
    private fun minutesAgo(m: Long) = now - m * 60_000

    @Test
    fun underAMinuteIsJustNow() = assertThat(agoWords(now - 59_000, now)).isEqualTo("just now")

    @Test
    fun minutesThenHoursThenDays() {
        assertThat(agoWords(minutesAgo(1), now)).isEqualTo("1 minute ago")
        assertThat(agoWords(minutesAgo(59), now)).isEqualTo("59 minutes ago")
        assertThat(agoWords(minutesAgo(60), now)).isEqualTo("1 hour ago")
        assertThat(agoWords(minutesAgo(48 * 60 - 1), now)).isEqualTo("47 hours ago")
        assertThat(agoWords(minutesAgo(48 * 60), now)).isEqualTo("2 days ago")
    }

    @Test
    fun aClockThatWentBackwardsReadsJustNow() = assertThat(agoWords(now + 3_600_000, now)).isEqualTo("just now")
}
