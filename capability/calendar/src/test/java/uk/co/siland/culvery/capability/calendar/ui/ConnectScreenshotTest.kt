package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The canvas the connecting card sits on, and the width Settings' content has there. */
private val CANVAS_W = 1280.dp
private val CANVAS_H = 800.dp
private val SETTINGS_W = 700.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val googleDescriptor = ProviderDescriptor("calendar.google", "Google Calendar", "calendar_month", setOf(Feature.READ, Feature.WRITE))

    private fun row(health: ConnectionHealth) = CalendarRow(google, "Google Calendar", "calendar_month", health, now - 2 * 60_000)

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { CulveryTheme(dark = dark) { content() } }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun connecting(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) {
            Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
            ConnectingCard("Google Calendar", onCancel = {})
        }
    }

    private fun settings(name: String, dark: Boolean, rows: List<CalendarRow>, connectable: List<ProviderDescriptor> = emptyList()) = snap(name, dark) {
        Box(Modifier.testTag("shot").size(SETTINGS_W, CANVAS_H / 2).background(Culvery.colors.bg).padding(16.dp)) {
            CalendarSettings(rows, connectable, now, onReconnect = {}, onConnect = {})
        }
    }

    @Test fun connectingDark() = connecting("connecting_dark", true)
    @Test fun connectingLight() = connecting("connecting_light", false)
    @Test fun settingsDark() = settings("settings_calendars_dark", true, listOf(row(ConnectionHealth.Ok)))
    @Test fun settingsLight() = settings("settings_calendars_light", false, listOf(row(ConnectionHealth.Ok)))
    @Test fun settingsReconnectDark() = settings("settings_calendars_reconnect_dark", true, listOf(row(ConnectionHealth.NeedsSignIn)))
    @Test fun settingsReconnectLight() = settings("settings_calendars_reconnect_light", false, listOf(row(ConnectionHealth.NeedsSignIn)))
    @Test fun settingsConnectDark() = settings("settings_calendars_connect_dark", true, emptyList(), listOf(googleDescriptor))
    @Test fun settingsConnectLight() = settings("settings_calendars_connect_light", false, emptyList(), listOf(googleDescriptor))
}
