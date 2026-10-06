package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** The calendar's screens at large font scales (4d-1 §6). Real text metrics: these measure lines and ellipses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L

    /** A long account that needs reconnecting, on the Settings page's 720 dp column. */
    @Test
    fun atFontScale15ALongAccountKeepsReconnectAndDisconnectWhole() {
        val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "the.whole.family.shared.calendar@example.co.uk"))
        val row = CalendarRow(google, "Google Calendar", "calendar_month", ConnectionHealth.NeedsSignIn, lastSyncMillis = now)
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    Box(Modifier.width(720.dp)) {
                        ReviewCalendars(
                            title = "Calendars",
                            connections = listOf(ReviewConnection(row, emptyList())),
                            people = listOf(Person.Family),
                            nowMillis = now,
                            busy = false,
                            picking = null,
                            confirming = null,
                            connectable = emptyList(),
                            actions = ReviewActions(),
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText("Google Calendar · the.whole.family.shared.calendar@example.co.uk", useUnmergedTree = true).textLayout()
        assertThat(title.lineCount).isEqualTo(1)
        assertThat(title.isLineEllipsized(0)).isTrue()
        listOf("Reconnect", "Disconnect").forEach { label ->
            val text = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("settings_row_g1")), useUnmergedTree = true).textLayout()
            assertThat(text.isLineEllipsized(0)).isFalse()
        }
    }
}
