package uk.co.siland.culvery.provider.calendar_fake

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ConnectScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun connectButtonReportsASampleConnection() {
        var connected: Connection? = null
        compose.setContent {
            CulveryTheme(dark = true) {
                FakeCalendarProvider().ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {})
            }
        }
        compose.onNodeWithText("Connect sample calendar").performClick()
        assertThat(connected?.providerId).isEqualTo(FakeCalendarProvider.ID)
        assertThat(connected?.label).isEqualTo("Sample calendar")
        assertThat(connected?.id).isNotEmpty()
    }

    @Test
    fun reconnectKeepsTheExistingConnectionId() {
        val existing = Connection("debug-sample", FakeCalendarProvider.ID, "Sample calendar", emptyMap())
        var connected: Connection? = null
        compose.setContent {
            CulveryTheme(dark = true) {
                FakeCalendarProvider().ConnectScreen(existing = existing, onConnected = { connected = it }, onCancel = {})
            }
        }
        compose.onNodeWithText("Connect sample calendar").performClick()
        assertThat(connected?.id).isEqualTo("debug-sample")
    }

    @Test
    fun cancelReportsCancel() {
        var cancelled = false
        compose.setContent {
            CulveryTheme(dark = true) {
                FakeCalendarProvider().ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled = true })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertThat(cancelled).isTrue()
    }
}
