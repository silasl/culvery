package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.onClickableShowing

/** Native graphics: the sized tests check that rows fit the real card sizes, which needs real text metrics. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** 5 §A.2: Today's titles 20 sp and their time line 16 sp; other cards' rows keep their sizes. */
    @Test
    fun todaysRowsUseTheLargerSizes() {
        show { TodayCard(SampleUi.today) }
        assertThat(compose.onNodeWithText("School run", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(20.sp)
        assertThat(compose.onNodeWithText("07:45–08:30 · Sam", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(16.sp)
    }

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
    fun anEmptyTodayWithAddSaysWhatToDo() {
        show { TodayCard(emptyList(), onAdd = {}) }
        compose.onNodeWithText("Nothing on today. Tap + to add something.").assertExists()
    }

    @Test
    fun anEmptyTodayWithoutAddJustSaysSo() {
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
        compose.onNodeWithText("See the week").performClick()
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
    }

    @Test
    fun weekOnTodayIsA48DpButton() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("See the week").assert(isButton).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun todayShowsTheFourthRowInTheTallCard() {
        showSized(397, 572) { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Swimming").assertIsDisplayed()
    }

    @Test
    fun recurringTodayRowShowsTheRepeatBadge() {
        show { TodayCard(SampleUi.today) }
        compose.onAllNodesWithContentDescription(", repeats", substring = true).assertCountEquals(1)
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
        compose.onNodeWithText("+2 more in Calendar").assertIsDisplayed()
    }

    @Test
    fun comingUpEmptyDaySaysFree() {
        show { ComingUpCard(SampleUi.comingUpBusy) }
        compose.onNodeWithText("Nothing on").assertExists()
    }

    @Test
    fun withNothingToConnectTheCardSaysSoAndOffersNoButton() {
        show { ConnectCalendarCard(connectService = null, onConnect = {}) }
        compose.onNodeWithText("No calendar services are available on this tablet.").assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun connectCardOffersGoogle() {
        var connects = 0
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = { connects++ }) }
        compose.onNodeWithText("Connect Google Calendar").performClick()
        assertThat(connects).isEqualTo(1)
        assertThat(navigator.settingsOpened).isEqualTo(0)
    }

    @Test
    fun comingUpHasNoWeekLink() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Week ›").assertDoesNotExist()
    }

    @Test
    fun todayAndComingUpRowsAreEachOneSpokenItem() {
        show { TodayCard(SampleUi.todayWithBadges) }
        val name = "Dinner with Jo & Priya, Alex, 19:30 to 21:00, saving"
        compose.onNodeWithContentDescription(name).assert(isButton)
        // The row's texts show in one node under the row, kept out of what TalkBack reads, so the name is said once.
        compose.onAllNodes(hasText("19:30", substring = true)).assertCountEquals(1)
        compose.onAllNodes(hasText("19:30", substring = true))[0].onParent().assertContentDescriptionEquals(name)
        compose.assertNothingReadTwice()
    }

    @Test
    fun aComingUpRowSaysItsDayAndIsNotAButton() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithContentDescription("Office day, Alex, Thursday 09:00")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        compose.onAllNodes(hasText("Office day")).assertCountEquals(1)
        compose.onAllNodes(hasText("Office day"))[0].onParent().assertContentDescriptionEquals("Office day, Alex, Thursday 09:00")
        compose.assertNothingReadTwice()
    }

    @Test
    fun todayRowsAreButtonsThatOpenTheirEvent() {
        val opened = mutableListOf<EventRef>()
        show { TodayCard(SampleUi.today, onOpen = { opened += it }) }
        compose.onClickableShowing("School run").performClick()
        assertThat(opened).containsExactly(EventRef("sample", "family", "School run"))
    }

    // Counted on the merged tree, as a person hears them: each row's spoken name ends with its badges' words, so
    // a row counts once per badge kind. Don't switch these to useUnmergedTree.
    @Test
    fun todayRowsShowEachOfTheirBadges() {
        show { TodayCard(SampleUi.todayWithBadges) }
        compose.onAllNodesWithContentDescription(", saving", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription(", can’t change here", substring = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(", repeats", substring = true).assertCountEquals(1)
    }

    @Test
    fun thePlusOnTodayIsA48DpCircleThatAdds() {
        var added = 0
        show { TodayCard(SampleUi.today, onAdd = { added++ }) }
        compose.onNodeWithTag("today_add").assert(isButton).assertHeightIsEqualTo(48.dp).assertWidthIsEqualTo(48.dp).performClick()
        assertThat(added).isEqualTo(1)
        compose.onNodeWithText("See the week").assertExists()
    }

    @Test
    fun theConnectCardsButtonIsAButton() {
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
        compose.onNodeWithText("Connect Google Calendar").assert(isButton).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun comingUpRowsNameWhoseEventItIs() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("09:00–17:00 · Alex").assertExists()
        compose.onNodeWithText("12:30–13:30 · Sam").assertExists()
    }

    private fun clickLabel(node: androidx.compose.ui.test.SemanticsNodeInteraction): String? =
        node.fetchSemanticsNode().config[SemanticsActions.OnClick].label

    @Test
    fun todayRowsSayTheyOpenTheirEventAndThePlusIsNamedOnce() {
        show { TodayCard(SampleUi.todayWithBadges, onAdd = {}) }
        val row = compose.onClickableShowing("Dinner with Jo & Priya").assert(isButton)
        assertThat(clickLabel(row)).isEqualTo("open event details")
        assertThat(clickLabel(compose.onNodeWithTag("today_add").assert(isButton))).isNull()
        compose.assertAccessible()
    }

    @Test
    fun theOtherHomeCardsPassTheSharedCheck() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.assertAccessible()
    }

    @Test
    fun theConnectCardPassesTheSharedCheck() {
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
        compose.assertAccessible()
    }

    @Test
    fun todaysTitleIsAHeading() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Today").assert(isHeading())
    }

    @Test
    fun comingUpsDaysAreHeadingsThatSayTheDate() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Coming up").assert(isHeading())
        compose.onNodeWithContentDescription("Tomorrow, Thursday 24 September").assert(isHeading())
        compose.onNodeWithContentDescription("Friday 25 September").assert(isHeading())
    }

    @Test
    fun theConnectCardsTitleIsAHeading() {
        show { ConnectCalendarCard(connectService = "Google Calendar", onConnect = {}) }
        compose.onNodeWithText("Connect a calendar").assert(isHeading())
    }
}
