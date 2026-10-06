package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** The calendar's screens at large font scales (4d-1 §6). Real text metrics: these measure lines and ellipses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L

    private fun showDetail(mode: DetailMode, detail: EventDetailUi = SampleUi.detailLongTitle) = compose.setContent {
        CulveryTheme(dark = false) {
            WithFontScale(1.5f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }

    private fun showDetail(mode: DetailMode, title: String) =
        showDetail(mode, SampleUi.detailLongTitle.copy(event = SampleUi.detailLongTitle.event.copy(title = title)))

    @Test
    fun atFontScale15EditStaysOnScreenAndTheBodyScrollsToItsLastRow() {
        showDetail(DetailMode.Idle)
        compose.onNodeWithTag("detail_edit").assertIsDisplayed()
        compose.onNodeWithText("Family calendar").performScrollTo().assertIsDisplayed()
    }

    /** A long title mustn't push the confirm card's buttons off the sheet. */
    @Test
    fun atFontScale15TheDeleteConfirmationsButtonsStayOnScreen() {
        showDetail(DetailMode.ConfirmingDelete)
        compose.onNodeWithTag("detail_keep").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
        compose.onNodeWithTag("detail_confirm_delete").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
    }

    /** A title synced from a phone can be any length; the confirm card quotes at most three lines of it. */
    @Test
    fun atFontScale15AThousandCharacterTitleStillLeavesTheConfirmationsButtons() {
        showDetail(DetailMode.ConfirmingDelete, "Boiler service ".repeat(67))
        compose.onNodeWithTag("detail_keep").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
        compose.onNodeWithTag("detail_confirm_delete").assertIsDisplayed().assertHeightIsEqualTo(60.dp)
    }

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

    /** A long name on a chip in a 1116 dp week at 1.5. */
    @Test
    fun atFontScale15ALongNameIsCutBeforeTheTimeAndTheBadgeStays() {
        val max = Person(PersonId("max"), "Maximilian-Alexander", 0xFF9C7CE3)
        val swim = SampleUi.event("Swimming", "16:00–17:00", max, recurring = true)
        val week = SampleUi.week.copy(days = SampleUi.week.days.mapIndexed { i, d -> d.copy(events = if (i == 0) listOf(swim) else emptyList()) })
        val sync = SyncStatusUi(SampleUi.NOW, emptyList(), listOf("Google"), failingBeforeFirstSync = false)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = false) {
                    WithFontScale(1.5f) {
                        Box(Modifier.size(1116.dp, 724.dp)) { WeekView(WeekViewState(week, SampleUi.TODAY, sync, SampleUi.NOW)) }
                    }
                }
            }
        }
        val line = compose.onNodeWithText("16:00 · Maximilian-Alexander", useUnmergedTree = true).textLayout()
        assertThat(line.lineCount).isEqualTo(1)
        assertThat(line.isLineEllipsized(0)).isTrue()
        // An ellipsised line's visible end is where the "…" starts: after the whole time.
        assertThat(line.getLineEnd(0, visibleEnd = true)).isAtLeast("16:00".length)
        compose.onNodeWithContentDescription("Repeats", useUnmergedTree = true).assertIsDisplayed()
    }
}
