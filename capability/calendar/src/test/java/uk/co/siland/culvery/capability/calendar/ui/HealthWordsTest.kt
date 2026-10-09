package uk.co.siland.culvery.capability.calendar.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** 4d-2 §7.2: a row's health says what is wrong and what to do. */
class HealthWordsTest {
    private val now = 10 * 60_000L

    private fun words(health: ConnectionHealth) = healthWords(
        CalendarRow(Connection("g1", "calendar.google", "Google", emptyMap()), "Google Calendar", "cloud", health, now - 2 * 60_000L),
        now,
    )

    @Test
    fun eachHealthSaysWhatIsWrongAndWhatToDo() {
        assertThat(words(ConnectionHealth.Ok)).isEqualTo("Updated 2 minutes ago")
        assertThat(words(ConnectionHealth.Unreachable)).isEqualTo("Can’t reach Google Calendar — check the tablet’s Wi-Fi.")
        assertThat(words(ConnectionHealth.NeedsSignIn)).isEqualTo("Needs reconnecting")
        assertThat(words(ConnectionHealth.Error("boom"))).isEqualTo("Stopped updating — disconnect and connect again.")
    }
}
