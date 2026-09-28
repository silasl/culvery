package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class CalendarSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val googleDescriptor = ProviderDescriptor("calendar.google", "Google Calendar", "calendar_month", setOf(Feature.READ, Feature.WRITE))

    private fun row(health: ConnectionHealth, id: String = "g1") =
        CalendarRow(google.copy(id = id), "Google Calendar", "calendar_month", health, lastSyncMillis = now - 5 * 60_000)

    private fun show(
        rows: List<CalendarRow>,
        connectable: List<ProviderDescriptor> = emptyList(),
        onReconnect: (Connection) -> Unit = {},
        onConnect: (ProviderDescriptor) -> Unit = {},
    ) = compose.setContent { CulveryTheme(dark = true) { CalendarSettings(rows, connectable, now, onReconnect, onConnect) } }

    @Test
    fun aHealthyConnectionNamesItsAccountAndWhenItSynced() {
        show(listOf(row(ConnectionHealth.Ok)))
        compose.onNodeWithText("Calendars").assertExists()
        compose.onNodeWithText("Google Calendar · family@example.com").assertExists()
        compose.onNodeWithText("Synced 5 min ago").assertExists()
        compose.onNodeWithTag("settings_reconnect").assertDoesNotExist()
    }

    @Test
    fun eachHealthReadsInWords() {
        show(listOf(row(ConnectionHealth.Unreachable, "a"), row(ConnectionHealth.Error("quota"), "b"), row(ConnectionHealth.NeedsSignIn, "c")))
        compose.onNodeWithText("Can't reach Google Calendar").assertExists()
        compose.onNodeWithText("Something went wrong").assertExists()
        compose.onNodeWithText("Needs reconnecting").assertExists()
    }

    @Test
    fun aConnectionNeedingSignInOffersReconnect() {
        var reconnected: Connection? = null
        show(listOf(row(ConnectionHealth.NeedsSignIn)), onReconnect = { reconnected = it })
        compose.onNodeWithTag("settings_reconnect").performClick()
        assertThat(reconnected).isEqualTo(google)
    }

    @Test
    fun aProviderNotYetConnectedIsOffered() {
        var connected: ProviderDescriptor? = null
        show(emptyList(), listOf(googleDescriptor), onConnect = { connected = it })
        compose.onNodeWithText("Connect Google Calendar").performClick()
        assertThat(connected).isEqualTo(googleDescriptor)
    }
}
