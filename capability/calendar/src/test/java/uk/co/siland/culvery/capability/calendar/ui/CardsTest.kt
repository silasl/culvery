package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Native graphics: the sized tests check that rows fit the real card sizes, which needs real text metrics. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { content() }
        }
    }

    /** The card at the size the Home grid gives it on the 1280×800 canvas. */
    private fun showSized(width: Int, height: Int, content: @Composable () -> Unit) = show {
        Box(Modifier.size(width.dp, height.dp)) { content() }
    }

    @Test
    fun todayRowShowsTimeAndPerson() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("School run").assertExists()
        compose.onNodeWithText("07:45–08:30 · Sam").assertExists()
    }

    @Test
    fun emptyTodaySaysNothingOnToday() {
        show { TodayCard(emptyList()) }
        compose.onNodeWithText("Nothing on today").assertExists()
    }

    @Test
    fun loadingTodayShowsNoEmptyMessage() {
        show { TodayCard(null) }
        compose.onNodeWithText("Nothing on today").assertDoesNotExist()
    }

    @Test
    fun weekChipOnTodayOpensTheCalendarTab() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Week").performClick()
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
    }

    @Test
    fun weekChipOnTodayIsAFullSizePill() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Week").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun todayShowsTheFourthRowInTheTallCard() {
        showSized(397, 572) { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Swimming").assertIsDisplayed()
    }

    @Test
    fun recurringTodayRowShowsTheRepeatBadge() {
        show { TodayCard(SampleUi.today) }
        compose.onAllNodesWithContentDescription("Repeats").assertCountEquals(1)
    }

    @Test
    fun comingUpLabelsTomorrowThenWeekdays() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Tomorrow").assertExists()
        compose.onNodeWithText("Friday").assertExists()
        compose.onNodeWithText("Saturday").assertExists()
    }

    @Test
    fun comingUpShowsTwoRowsAndAVisibleMoreInTheWideCard() {
        showSized(705, 279) { ComingUpCard(SampleUi.comingUpBusy) }
        compose.onNodeWithText("Swim club").assertIsDisplayed()
        compose.onNodeWithText("Football").assertDoesNotExist()
        compose.onNodeWithText("+2 more").assertIsDisplayed()
    }

    @Test
    fun comingUpEmptyDaySaysFree() {
        show { ComingUpCard(SampleUi.comingUpBusy) }
        compose.onNodeWithText("Free").assertExists()
    }

    @Test
    fun connectCardOpensSettings() {
        show { ConnectCalendarCard() }
        compose.onNodeWithText("Connect a calendar").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
    }

    @Test
    fun comingUpHasNoWeekLink() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Week ›").assertDoesNotExist()
    }

    @Test
    fun todayRowsAreButtonsThatOpenTheirEvent() {
        val opened = mutableListOf<EventRef>()
        show { TodayCard(SampleUi.today, onOpen = { opened += it }) }
        compose.onNodeWithText("School run").assert(hasClickAction())
        compose.onNodeWithText("School run").performClick()
        assertThat(opened).containsExactly(EventRef("sample", "family", "School run"))
    }

    // Counted on the merged tree, as a person hears them: each row merges its badges' descriptions, so a row
    // counts once per badge kind. Don't switch these to useUnmergedTree.
    @Test
    fun todayRowsShowEachOfTheirBadges() {
        show { TodayCard(SampleUi.todayWithBadges) }
        compose.onAllNodesWithContentDescription("Syncing").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Read-only calendar").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Repeats").assertCountEquals(1)
    }

    @Test
    fun thePlusOnTodayIsA44DpCircleThatAdds() {
        var added = 0
        show { TodayCard(SampleUi.today, onAdd = { added++ }) }
        compose.onNodeWithTag("today_add").assertHeightIsEqualTo(44.dp).assertWidthIsEqualTo(44.dp).performClick()
        assertThat(added).isEqualTo(1)
        compose.onNodeWithText("Week").assertExists()
    }
}
