package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.weekTitle
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.LocalUserActivity
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

@RunWith(AndroidJUnit4::class)
class WeekViewTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val now = SampleUi.NOW

    private val activity = FakeUserActivity()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator, LocalUserActivity provides activity) {
            CulveryTheme(dark = true) { content() }
        }
    }

    private fun sync(
        agoMinutes: Long = 2,
        needsSignIn: List<String> = emptyList(),
        connections: List<String> = listOf("Google"),
    ) = SyncStatusUi(now - agoMinutes * 60_000, needsSignIn, connections, failingBeforeFirstSync = false)

    private fun state(sync: SyncStatusUi = sync()) = WeekViewState(SampleUi.week, SampleUi.TODAY, sync, now)

    @Test
    fun showsSevenDaysStartingTodayAsThisWeek() {
        show { WeekView(state()) }
        (0L..6L).forEach { compose.onNodeWithTag("week_day_${SampleUi.TODAY.plusDays(it)}").assertExists() }
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun subtitleNamesTheOnlyConnection() {
        show { WeekView(state()) }
        compose.onNodeWithText("Updated from Google 2 minutes ago").assertExists()
    }

    @Test
    fun staleSubtitleStillShowsTheAge() {
        show { WeekView(state(sync(agoMinutes = 45))) }
        compose.onNodeWithText("Out of date — updated from Google 45 minutes ago").assertExists()
    }

    @Test
    fun aStaleWeekShowsTheIconAndTheWordsAsWellAsTheColour() {
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("Out of date — updated from Google Calendar 45 minutes ago")
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFreshWeekHasNoIcon() {
        show { WeekView(state(sync(agoMinutes = 2, connections = listOf("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("Updated from Google Calendar 2 minutes ago")
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aChipNamesWhoseEventItIs() {
        show { WeekView(state()) }
        compose.onNodeWithText("09:30 · Alex").assertExists()
        compose.onAllNodesWithText("All day · Family").assertCountEquals(2)
    }

    @Test
    fun recurringChipsShowTheRepeatBadge() {
        show { WeekView(state()) }
        // Swimming, Bin day and Piano.
        compose.onAllNodesWithContentDescription(", repeats", substring = true).assertCountEquals(3)
    }

    @Test
    fun aChipSaysWhatWhoWhenAndItsBadgesAsOneItem() {
        show { WeekView(state()) }
        compose.onNodeWithContentDescription("Swimming, Mia, Wednesday 16:00, repeats").assert(isButton)
        compose.onNodeWithContentDescription("Dinner with Jo & Priya, Alex, Wednesday 19:30").assertExists()
        // One merged node holds the chip's texts: without the merge each Text is its own node and has no description.
        compose.onAllNodes(hasText("Dinner with Jo & Priya")).assertCountEquals(1)
        compose.onAllNodes(hasText("Dinner with Jo & Priya"))[0]
            .assertContentDescriptionEquals("Dinner with Jo & Priya, Alex, Wednesday 19:30")
        compose.onAllNodes(hasText("19:30 · Alex", substring = true)).assertCountEquals(1)
    }

    @Test
    fun legendNamesEveryoneAndFamily() {
        show { WeekView(state()) }
        compose.onNodeWithContentDescription("Colours: Alex, green; Sam, blue; Mia, pink; Family, amber").assertExists()
    }

    @Test
    fun theReconnectChipRunsTheReconnect() {
        var reconnects = 0
        show { WeekView(state(sync(agoMinutes = 0, needsSignIn = listOf("Google"))), onReconnect = { reconnects++ }) }
        compose.onNodeWithText("Google needs reconnecting").assert(isButton).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Google needs reconnecting").performClick()
        assertThat(reconnects).isEqualTo(1)
        assertThat(navigator.settingsOpened).isEqualTo(0)
    }

    @Test
    fun severalCalendarsNeedingSignInShareOneChip() {
        val status = sync(needsSignIn = listOf("Google", "School"), connections = listOf("Google", "School"))
        show { WeekView(state(status)) }
        compose.onNodeWithText("2 accounts need reconnecting").assertExists()
        compose.onNodeWithText("Google needs reconnecting").assertDoesNotExist()
    }

    @Test
    fun noReconnectChipWhenEveryConnectionIsHealthy() {
        show { WeekView(state()) }
        compose.onNodeWithText("needs reconnecting", substring = true).assertDoesNotExist()
        compose.onNodeWithText("need reconnecting", substring = true).assertDoesNotExist()
    }

    @Test
    fun legendStaysLevelWithTheSubtitleWhetherOrNotTheReconnectChipShows() {
        val currentSync = mutableStateOf(sync())
        show {
            val s by currentSync
            WeekView(state(s))
        }
        val withoutChip = compose.onNodeWithTag("week_legend").fetchSemanticsNode().boundsInRoot.top

        currentSync.value = sync(agoMinutes = 0, needsSignIn = listOf("Google"))
        compose.waitForIdle()
        val withChip = compose.onNodeWithTag("week_legend").fetchSemanticsNode().boundsInRoot.top

        assertThat(withChip).isEqualTo(withoutChip)
    }

    @Test
    fun insetDayFromTheSchoolFeedShowsTheLockBadge() {
        show { WeekView(state()) }
        compose.onAllNodesWithContentDescription(", can’t change here", substring = true).assertCountEquals(1)
    }

    @Test
    fun aSyncingChipShowsCloudUpload() {
        show { WeekView(WeekViewState(SampleUi.weekWithSyncing, SampleUi.TODAY, sync(), now)) }
        compose.onAllNodesWithContentDescription(", saving", substring = true).assertCountEquals(1)
    }

    @Test
    fun tappingAChipOpensItsEvent() {
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }) }
        compose.onNodeWithText("Parkrun").performClick()
        assertThat(opened).containsExactly(EventRef("sample", "family", "Parkrun"))
    }

    @Test
    fun addEventInTheHeaderAddsOnToday() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").assertHeightIsEqualTo(48.dp).performClick()
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    @Test
    fun aTapOnAColumnsAddHintAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        val friday = SampleUi.TODAY.plusDays(2)
        compose.onNodeWithTag("week_add_$friday", useUnmergedTree = true).performClick()
        assertThat(added).containsExactly(friday)
    }

    @Test
    fun aTapOnTheSpaceBelowAColumnsLastChipAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // Sunday has just the one chip ("Sunday lunch at Gran's"), leaving plenty of empty space below it.
        val sunday = SampleUi.TODAY.plusDays(6)
        val columnTop = compose.onNodeWithTag("week_day_$sunday").fetchSemanticsNode().boundsInRoot.top
        val chipBottom = compose.onNodeWithText("Sunday lunch at Gran's").fetchSemanticsNode().boundsInRoot.bottom
        val hintTop = compose.onNodeWithTag("week_add_$sunday", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val midY = (chipBottom + hintTop) / 2f
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { click(Offset(centerX, midY - columnTop)) }
        assertThat(added).containsExactly(sunday)
    }

    @Test
    fun aTapAnywhereInAnEmptyColumnAddsOnThatDay() {
        val added = mutableListOf<LocalDate>()
        // Today's column with no chips at all: there's no last chip to compare against (the `last == null` branch).
        val emptyToday = SampleUi.week.copy(days = SampleUi.week.days.map { if (it.date == SampleUi.TODAY) it.copy(events = emptyList()) else it })
        show { WeekView(WeekViewState(emptyToday, SampleUi.TODAY, sync(), now), onAdd = { added += it }) }
        compose.onNodeWithTag("week_day_${SampleUi.TODAY}").performTouchInput { click(Offset(centerX, centerY)) }
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    @Test
    fun aTapInTheGapBetweenTwoChipsAddsNothing() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // Saturday has three chips ("INSET day — no school", "Piano", "Book club"), so there's a gap to tap between the first two.
        val saturday = SampleUi.TODAY.plusDays(3)
        val columnTop = compose.onNodeWithTag("week_day_$saturday").fetchSemanticsNode().boundsInRoot.top
        val firstBottom = compose.onNodeWithText("INSET day — no school").fetchSemanticsNode().boundsInRoot.bottom
        val secondTop = compose.onNodeWithText("Piano").fetchSemanticsNode().boundsInRoot.top
        val midY = (firstBottom + secondTop) / 2f
        compose.onNodeWithTag("week_day_$saturday").performTouchInput { click(Offset(centerX, midY - columnTop)) }
        assertThat(added).isEmpty()
    }

    @Test
    fun aChipTapStillOpensItsEventAndAddsNothing() {
        val added = mutableListOf<LocalDate>()
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }, onAdd = { added += it }) }
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        assertThat(opened).hasSize(1)
        assertThat(added).isEmpty()
    }

    @Test
    fun eachColumnEndsWithAnAddHintAtLeast48DpTall() {
        show { WeekView(state(), onAdd = {}) }
        (0L..6L).forEach {
            compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(it)}", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun addEventAndThisWeekAre48DpButtons() {
        show { WeekView(state().copy(weeksAhead = 1), onAdd = {}) }
        listOf("week_add_event", "week_this_week").forEach { compose.onNodeWithTag(it).assert(isButton).assertHeightIsEqualTo(48.dp) }
    }

    @Test
    fun aTapOnAColumnsHeaderAddsNothing() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(state(), onAdd = { added += it }) }
        // A missed tap near the day's name is not a request for a new event: only the space below the chips adds.
        compose.onNodeWithTag("week_day_${SampleUi.TODAY}").performTouchInput { click(Offset(centerX, 10f)) }
        assertThat(added).isEmpty()
    }

    /** 4c §6.6: ‹ › step a week at a time; › stops at the furthest week, ‹ at this week; This week jumps back. */
    @Test
    fun theArrowsStepAWeekAndStopAtEachEnd() {
        val asked = mutableListOf<Int>()
        var weeks by mutableStateOf(0)
        show { WeekView(state().copy(weeksAhead = weeks), onWeeksAhead = { asked += it; weeks = it }) }
        compose.onNodeWithTag("week_earlier").assertIsNotEnabled()
        compose.onNodeWithTag("week_this_week").assertDoesNotExist()
        compose.onNodeWithTag("week_later").performClick()
        compose.onNodeWithText("Next week").assertExists()
        repeat(MAX_WEEKS_AHEAD) { compose.onNodeWithTag("week_later").performClick() }
        compose.onNodeWithText("In 3 weeks").assertExists()
        compose.onNodeWithTag("week_later").assertIsNotEnabled()
        compose.onNodeWithTag("week_earlier").assertIsEnabled()
        compose.onNodeWithTag("week_this_week").performClick()
        assertThat(asked).containsExactly(1, 2, 3, 0).inOrder()
        compose.onNodeWithText("This week").assertExists()
        compose.onNodeWithTag("week_this_week").assertDoesNotExist()
    }

    @Test
    fun eachWeekHasItsTitle() {
        assertThat((0..MAX_WEEKS_AHEAD).map(::weekTitle)).containsExactly("This week", "Next week", "In 2 weeks", "In 3 weeks").inOrder()
    }

    /** 4c §6.6: on a later week, + adds on its first day and a column on its own day; no column says Today. */
    @Test
    fun aLaterWeekAddsOnItsOwnDays() {
        val added = mutableListOf<LocalDate>()
        show { WeekView(WeekViewState(SampleUi.weekAhead(1), SampleUi.TODAY, sync(), now, weeksAhead = 1), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").performClick()
        compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(9)}").performClick()
        assertThat(added).containsExactly(SampleUi.TODAY.plusWeeks(1), SampleUi.TODAY.plusDays(9)).inOrder()
        compose.onAllNodesWithContentDescription("Today, ", substring = true).assertCountEquals(0)
    }

    /** With the clock paused, a state change from the test isn't seen by the composition until its writes are applied. */
    private fun applyStateChanges() = compose.runOnIdle { Snapshot.sendApplyNotifications() }

    private fun showShown(): () -> WeekShown {
        lateinit var shown: WeekShown
        show {
            shown = rememberWeekShown(SampleUi.TODAY)
            Text(weekTitle(shown.weeks))
        }
        return { shown }
    }

    /** 4c §6.6, 4d-2 §6.4: a later week goes back after 2 minutes without a touch anywhere; a touch starts the wait again. */
    @Test
    fun aLaterWeekGoesBackToThisWeekAfterTwoMinutesWithoutATouch() {
        compose.mainClock.autoAdvance = false
        val shown = showShown()
        compose.runOnIdle { shown().show(2) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 100)
        compose.onNodeWithText("In 2 weeks").assertExists()
        activity.touch()
        applyStateChanges()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 100)
        compose.onNodeWithText("In 2 weeks").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun withTalkBackOnALaterWeekStays() {
        compose.mainClock.autoAdvance = false
        activity.exploring.value = true
        val shown = showShown()
        compose.runOnIdle { shown().show(1) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(10 * 60_000L)
        compose.onNodeWithText("Next week").assertExists()
    }

    /** Switched off mid-way, the 2-minute wait starts from that moment. */
    @Test
    fun turningTouchExplorationOffStartsTheWaitAgain() {
        compose.mainClock.autoAdvance = false
        activity.exploring.value = true
        val shown = showShown()
        compose.runOnIdle { shown().show(1) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(10 * 60_000L)
        activity.exploring.value = false
        applyStateChanges()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(BACK_TO_THIS_WEEK_MS - 100)
        compose.onNodeWithText("Next week").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun aLongTimeToTakeActionHoldsALaterWeekLonger() {
        compose.mainClock.autoAdvance = false
        activity.timeouts = { base, _ -> if (base == BACK_TO_THIS_WEEK_MS) 300_000L else base }
        val shown = showShown()
        compose.runOnIdle { shown().show(1) }
        applyStateChanges()
        compose.mainClock.advanceTimeBy(299_900)
        compose.onNodeWithText("Next week").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun theWeeksTitleIsAPoliteLiveRegion() {
        show { WeekView(state()) }
        compose.onNodeWithTag("week_title").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** 4c §6.6: at midnight the view is back on this week, whatever it showed. */
    @Test
    fun aLaterWeekGoesBackToThisWeekAtMidnight() {
        var today by mutableStateOf(SampleUi.TODAY)
        lateinit var shown: WeekShown
        show {
            shown = rememberWeekShown(today)
            Text(weekTitle(shown.weeks))
        }
        compose.runOnIdle { shown.show(MAX_WEEKS_AHEAD + 1) }
        compose.onNodeWithText("In 3 weeks").assertExists()
        today = today.plusDays(1)
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun everyClickableInTheWeekHasARoleAndAName() {
        show { WeekView(state(sync(needsSignIn = listOf("Google"))).copy(weeksAhead = 1), onAdd = {}) }
        compose.assertAccessible()
        compose.onNodeWithContentDescription("Add event on Wednesday 23 September", useUnmergedTree = true).assert(isButton)
        val chip = compose.onNodeWithText("Parkrun")
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("open event details")
        val reconnect = compose.onNodeWithText("Google needs reconnecting")
        assertThat(reconnect.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("reconnect")
        assertThat(compose.onNodeWithTag("week_later").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
    }

    @Test
    fun eachColumnHeaderIsOneHeadingThatSaysItsDate() {
        show { WeekView(state()) }
        compose.onNodeWithContentDescription("Today, Wednesday 23 September").assert(isHeading()).onChildren().assertCountEquals(0)
        compose.onNodeWithContentDescription("Thursday 24 September").assert(isHeading())
        compose.onNodeWithText("This week").assert(isHeading())
    }
}
