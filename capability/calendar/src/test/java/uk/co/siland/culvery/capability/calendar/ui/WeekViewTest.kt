package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import uk.co.siland.culvery.capability.calendar.DAY_AND_DATE
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.SyncProblem
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.weekTitle
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.LocalUserActivity
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.onClickableShowing

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
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · updated from Google 2 minutes ago")
    }

    @Test
    fun subtitleNamesTheOnlyConnection() {
        show { WeekView(state()) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · updated from Google 2 minutes ago")
    }

    @Test
    fun staleSubtitleStillShowsTheAge() {
        show { WeekView(state(sync(agoMinutes = 45))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · out of date — updated from Google 45 minutes ago")
    }

    /** 9 §3.2. */
    @Test
    fun theTitleIsTheMonthWithTheYearMuted() {
        var mute = Color.Unspecified
        show {
            mute = Culvery.colors.mute
            WeekView(state())
        }
        val title = compose.onNodeWithTag("week_title")
        title.assertContentDescriptionEquals("September 2026")
        title.assert(isHeading())
        compose.onNodeWithText("September", useUnmergedTree = true).assertExists()
        val year = compose.onNodeWithText("2026", useUnmergedTree = true).textLayout().layoutInput.style.color
        assertThat(year).isEqualTo(mute)
    }

    private fun twoMonthsAt(scale: Float, week: WeekUi = SampleUi.weekAhead(1), weeksAhead: Int = 1) {
        show { WithFontScale(scale) { WeekView(WeekViewState(week, SampleUi.TODAY, sync(), now, weeksAhead = weeksAhead)) } }
    }

    /** The label sits above the 1st's date, inside its column, and takes no header height from it. */
    private fun assertLabelAbove(label: String, first: LocalDate, otherChip: String, firstChip: String) {
        val labels = compose.onAllNodesWithTag("week_month_label", useUnmergedTree = true)
        labels.assertCountEquals(1)
        labels[0].assertTextEquals(label)
        // Not boundsInRoot: that is clipped to the card, which would hide a label cut off by its top edge.
        val node = labels[0].fetchSemanticsNode()
        val box = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
        val column = compose.onNodeWithTag("week_day_$first").fetchSemanticsNode().boundsInRoot
        val date = compose.onNode(hasText("1") and hasAnyAncestor(hasTestTag("week_day_$first")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertThat(box.top).isAtLeast(column.top)
        assertThat(box.left).isAtLeast(column.left)
        assertThat(box.right).isAtMost(column.right)
        assertThat(box.top).isLessThan(date.top)
        val chip = compose.onNodeWithText(firstChip).fetchSemanticsNode().boundsInRoot.top
        val other = compose.onNodeWithText(otherChip).fetchSemanticsNode().boundsInRoot.top
        assertThat(chip).isWithin(0.5f).of(other)
    }

    @Test
    fun aNewYearsDayWeekLabelsJan() {
        val start = LocalDate.of(2026, 12, 30)
        val week = SampleUi.week.copy(start = start, days = SampleUi.week.days.mapIndexed { i, d -> d.copy(date = start.plusDays(i.toLong())) })
        twoMonthsAt(1f, week, weeksAhead = 1)
        compose.onNodeWithTag("week_title").assertContentDescriptionEquals("December 2026 to January 2027")
        assertLabelAbove("Jan", LocalDate.of(2027, 1, 1), otherChip = "Bin day", firstChip = "Office day")
    }

    @Test
    fun aWeekAcrossTwoMonthsLabelsTheFirst() {
        twoMonthsAt(1f)
        compose.onNodeWithTag("week_title").assertContentDescriptionEquals("September to October 2026")
        assertLabelAbove("Oct", LocalDate.of(2026, 10, 1), otherChip = "Bin day", firstChip = "Office day")
    }

    /** Pin: only a week that crosses a month has the label. */
    @Test
    fun aWeekWithinOneMonthHasNoMonthLabel() {
        show { WeekView(state()) }
        compose.onAllNodesWithTag("week_month_label", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aStaleWeekShowsTheIconAndTheWordsAsWellAsTheColour() {
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · out of date — updated from Google Calendar 45 minutes ago")
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFreshWeekHasNoIcon() {
        show { WeekView(state(sync(agoMinutes = 2, connections = listOf("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · updated from Google Calendar 2 minutes ago")
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertDoesNotExist()
    }

    /** 4e: on the tablet the line went red and nothing could be done about it. */
    @Test
    fun aStaleWeekOffersUpdateNowWhichAsksForASyncAndSaysUpdating() {
        var asked = 0
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar"))), onUpdateNow = { asked++ }) }
        compose.onNodeWithTag("week_update_now").assert(isButton).performClick()
        assertThat(asked).isEqualTo(1)
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · Updating…")
        compose.onNodeWithTag("week_update_now").assertDoesNotExist()
    }

    @Test
    fun updatingReplacesOnlyTheSyncPart() {
        show { WeekView(state(sync(agoMinutes = 45)), onUpdateNow = {}) }
        compose.onNodeWithTag("week_update_now").performClick()
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · Updating…")
    }

    @Test
    fun aFreshWeekHasNoUpdateNow() {
        show { WeekView(state(sync(agoMinutes = 2)), onUpdateNow = {}) }
        compose.onNodeWithTag("week_update_now").assertDoesNotExist()
    }

    /** A sync that fails leaves the age unchanged: after a while the line says so again and the button comes back. */
    @Test
    fun aFailedUpdateGoesBackToTheStaleLine() {
        compose.mainClock.autoAdvance = false
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar"))), onUpdateNow = {}) }
        compose.onNodeWithTag("week_update_now").performClick()
        compose.mainClock.advanceTimeBy(UPDATING_SHOWN_MS + 100)
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · out of date — updated from Google Calendar 45 minutes ago")
        compose.onNodeWithTag("week_update_now").assertExists()
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
        // The chip's texts show under it, kept out of what TalkBack reads, so the chip is said once.
        compose.onAllNodes(hasText("Dinner with Jo & Priya")).assertCountEquals(1)
        compose.onAllNodes(hasText("Dinner with Jo & Priya"))[0].onParent()
            .assertContentDescriptionEquals("Dinner with Jo & Priya, Alex, Wednesday 19:30")
        compose.onAllNodes(hasText("19:30 · Alex", substring = true)).assertCountEquals(1)
        compose.assertNothingReadTwice()
    }

    @Test
    fun thereIsNoLegend() {
        show { WeekView(state(), onAdd = {}) }
        compose.onAllNodesWithTag("week_legend").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Colours:", substring = true).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theArrowsShareOnePillAndThisWeekSitsBetween() {
        show { WeekView(state().copy(weeksAhead = 1), onAdd = {}) }
        val pill = compose.onNodeWithTag("week_nav_pill").fetchSemanticsNode().boundsInRoot
        listOf("week_earlier", "week_later").forEach {
            compose.onNodeWithTag(it).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            val arrow = compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot
            assertThat(arrow.left).isAtLeast(pill.left)
            assertThat(arrow.right).isAtMost(pill.right)
        }
        val thisWeek = compose.onNodeWithTag("week_this_week").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("week_add_event").fetchSemanticsNode().boundsInRoot
        assertThat(thisWeek.left).isAtLeast(pill.right)
        assertThat(thisWeek.right).isAtMost(add.left)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: an arrow at its end is the shared disabled button, not a faded one. */
    @Test
    fun aDisabledArrowIsTheSharedDisabledLook() {
        show { WeekView(state()) }
        compose.onNodeWithTag("week_earlier").assertIsNotEnabled()
        compose.onNodeWithTag("week_later").assertIsEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSyncFailureShowsUnderTheNewTitle() {
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar")).copy(problem = SyncProblem.Offline))) }
        val title = compose.onNodeWithTag("week_title").fetchSemanticsNode().boundsInRoot
        val line = compose.onNodeWithTag("week_subtitle").fetchSemanticsNode().boundsInRoot
        val update = compose.onNodeWithTag("week_update_now").fetchSemanticsNode().boundsInRoot
        assertThat(line.top).isAtLeast(title.bottom)
        assertThat(update.top).isAtLeast(title.bottom)
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun chipTitleLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnUiThread {
            compose.onAllNodes(hasText("Dinner with Jo & Priya"))[0].fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        }
        return layouts.single()
    }

    @Test
    fun aChipReadsTimeAndPersonWithADotAndTwoLines() {
        show { WeekView(state()) }
        compose.onAllNodes(hasText("19:30 · Alex")).assertCountEquals(1)
        assertThat(compose.onAllNodesWithTag("chip_dot", useUnmergedTree = true).fetchSemanticsNodes()).isNotEmpty()
        assertThat(chipTitleLayout().layoutInput.maxLines).isEqualTo(2)
        compose.onNodeWithContentDescription("Dinner with Jo & Priya, Alex, Wednesday 19:30").assert(isButton)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    private val friday: LocalDate = SampleUi.TODAY.plusDays(2)
    private val cinemaName = "Cinema, Sam and Mia, Friday 17:00"

    @Test
    fun aChipWithNoOpenIsNotClickable() {
        show { EventChip(SampleUi.cinema, friday, onOpen = null) }
        compose.onNodeWithContentDescription(cinemaName).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun aSharedChipsMetaHasOverlappingDotsAndTheWhoLine() {
        show { EventChip(SampleUi.cinema, friday, onOpen = {}) }
        val chip = compose.onNodeWithContentDescription(cinemaName).fetchSemanticsNode().boundsInRoot
        val dots = compose.onNodeWithTag("chip_dots", useUnmergedTree = true).onChildren().fetchSemanticsNodes().map { it.boundsInRoot }
        val dp = compose.density.density
        assertThat(dots).hasSize(2)
        dots.forEach { assertThat(it.width / dp).isWithin(0.1f).of(8f) }
        assertThat((dots[1].left - dots[0].left) / dp).isWithin(0.1f).of(6f)
        assertThat((dots[0].left - chip.left) / dp).isWithin(0.1f).of(12f)
        compose.onAllNodes(hasText("17:00 · Sam + Mia")).assertCountEquals(1)
    }

    @Test
    fun aSharedChipIsSpokenWithAnd() {
        show { WeekView(WeekViewState(SampleUi.weekWithShared, SampleUi.TODAY, sync(), now)) }
        compose.onNodeWithContentDescription("Cinema, Sam and Mia, Friday 17:00").assert(isButton)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
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

    /** 9 §4.7. Fails if the ‹ › pill isn't level with the sync line's foot, or drops to the reconnect chip's row. */
    @Test
    fun theNavPillSitsLevelWithTheSyncLineAboveTheReconnectChip() {
        show { WeekView(state(sync(agoMinutes = 45, needsSignIn = listOf("Google"))), onAdd = {}) }
        val pill = compose.onNodeWithTag("week_nav_pill").fetchSemanticsNode().boundsInRoot
        val update = compose.onNodeWithTag("week_update_now").fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithText("Google needs reconnecting").fetchSemanticsNode().boundsInRoot
        assertThat(pill.bottom).isWithin(1f).of(update.bottom)
        assertThat(chip.top).isAtLeast(pill.bottom)
        compose.assertClickablesHaveRoleAndLabel()
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
    fun addEventInTheHeaderOpensWithNoDay() {
        val added = mutableListOf<LocalDate?>()
        show { WeekView(state(), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").assertHeightIsEqualTo(48.dp).performClick()
        assertThat(added).containsExactly(null)
    }

    @Test
    fun aTapOnTheSpaceBelowAColumnsLastChipAddsOnThatDay() {
        val added = mutableListOf<LocalDate?>()
        show { WeekView(state(), onAdd = { added += it }) }
        // Sunday has just the one chip ("Sunday lunch at Gran's"), leaving plenty of empty space below it.
        val sunday = SampleUi.TODAY.plusDays(6)
        val columnTop = compose.onNodeWithTag("week_day_$sunday").fetchSemanticsNode().boundsInRoot.top
        val chipBottom = compose.onNodeWithText("Sunday lunch at Gran's").fetchSemanticsNode().boundsInRoot.bottom
        val columnBottom = compose.onNodeWithTag("week_day_$sunday").fetchSemanticsNode().boundsInRoot.bottom
        val midY = (chipBottom + columnBottom) / 2f
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { click(Offset(centerX, midY - columnTop)) }
        assertThat(added).containsExactly(sunday)
    }

    @Test
    fun aTapAnywhereInAnEmptyColumnAddsOnThatDay() {
        val added = mutableListOf<LocalDate?>()
        // Today's column with no chips at all: there's no last chip to compare against (the `last == null` branch).
        val emptyToday = SampleUi.week.copy(days = SampleUi.week.days.map { if (it.date == SampleUi.TODAY) it.copy(events = emptyList()) else it })
        show { WeekView(WeekViewState(emptyToday, SampleUi.TODAY, sync(), now), onAdd = { added += it }) }
        compose.onNodeWithTag("week_day_${SampleUi.TODAY}").performTouchInput { click(Offset(centerX, centerY)) }
        assertThat(added).containsExactly(SampleUi.TODAY)
    }

    @Test
    fun aTapInTheGapBetweenTwoChipsAddsNothing() {
        val added = mutableListOf<LocalDate?>()
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
        val added = mutableListOf<LocalDate?>()
        val opened = mutableListOf<EventRef>()
        show { WeekView(state(), onOpen = { opened += it }, onAdd = { added += it }) }
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        assertThat(opened).hasSize(1)
        assertThat(added).isEmpty()
    }

    /** A point in [date]'s column, in the column's own coordinates, halfway between [lastChip]'s bottom and the column's foot. */
    private fun belowChip(date: LocalDate, lastChip: String): Offset {
        val column = compose.onNodeWithTag("week_day_$date").fetchSemanticsNode().boundsInRoot
        val chipBottom = compose.onNodeWithText(lastChip).fetchSemanticsNode().boundsInRoot.bottom
        return Offset(column.width / 2f, (chipBottom + column.bottom) / 2f - column.top)
    }

    /** A column must be wide enough for the ghost's label; the default test screen is 320 dp wide. */
    private companion object {
        const val TABLET = "w1280dp-h800dp"
    }

    private val sunday = SampleUi.TODAY.plusDays(6)
    private val sundayLastChip = "Sunday lunch at Gran's"

    @Test
    @Config(qualifiers = TABLET)
    fun pressingEmptySpaceShowsTheGhost() {
        val added = mutableListOf<LocalDate?>()
        show { WeekView(state(), onAdd = { added += it }) }
        val point = belowChip(sunday, sundayLastChip)
        val ghost = "Add on ${sunday.format(DAY_AND_DATE)}"
        compose.onAllNodesWithText(ghost).assertCountEquals(0)
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { down(point) }
        compose.onNodeWithText(ghost).assertIsDisplayed()
        assertThat(added).isEmpty()
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { up() }
        compose.onAllNodesWithText(ghost).assertCountEquals(0)
        assertThat(added).containsExactly(sunday)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun pressingBetweenTwoChipsShowsNoGhost() {
        show { WeekView(state(), onAdd = {}) }
        val saturday = SampleUi.TODAY.plusDays(3)
        val column = compose.onNodeWithTag("week_day_$saturday").fetchSemanticsNode().boundsInRoot
        val firstBottom = compose.onNodeWithText("INSET day — no school").fetchSemanticsNode().boundsInRoot.bottom
        val secondTop = compose.onNodeWithText("Piano").fetchSemanticsNode().boundsInRoot.top
        val gap = Offset(column.width / 2f, (firstBottom + secondTop) / 2f - column.top)
        compose.onNodeWithTag("week_day_$saturday").performTouchInput { down(gap) }
        compose.onAllNodesWithText("Add on ", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("week_day_$saturday").performTouchInput { up() }
    }

    @Test
    @Config(qualifiers = TABLET)
    fun aPressDraggedOffTheColumnAddsNothing() {
        val added = mutableListOf<LocalDate?>()
        show { WeekView(state(), onAdd = { added += it }) }
        val point = belowChip(sunday, sundayLastChip)
        val ghost = "Add on ${sunday.format(DAY_AND_DATE)}"
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { down(point) }
        compose.onNodeWithText(ghost).assertIsDisplayed()
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { moveBy(Offset(-3000f, 0f)) }
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { up() }
        compose.onAllNodesWithText(ghost).assertCountEquals(0)
        assertThat(added).isEmpty()
    }

    @Test
    fun everyColumnHasOneAddButtonClearOfItsChips() {
        val added = mutableListOf<LocalDate?>()
        val emptyThursday = SampleUi.week.copy(days = SampleUi.week.days.map { if (it.date == SampleUi.TODAY.plusDays(1)) it.copy(events = emptyList()) else it })
        show { WeekView(WeekViewState(emptyThursday, SampleUi.TODAY, sync(), now), onAdd = { added += it }) }
        emptyThursday.days.forEach { day ->
            val add = compose.onAllNodesWithTag("week_add_${day.date}", useUnmergedTree = true)
            add.assertCountEquals(1)
            val node = add[0]
            node.assert(isButton).assertContentDescriptionEquals(addEventOn(day.date)).onChildren().assertCountEquals(0)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertThat(bounds.height).isGreaterThan(0f)
            day.events.forEach { event ->
                assertThat(compose.onNodeWithText(event.title).fetchSemanticsNode().boundsInRoot.bottom).isAtMost(bounds.top + 1f)
            }
            node.performClick()
        }
        assertThat(added).containsExactlyElementsIn(emptyThursday.days.map { it.date }).inOrder()
    }

    @Test
    fun aFullColumnCanAddFromItsHeader() {
        val added = mutableListOf<LocalDate?>()
        val wednesday = SampleUi.TODAY
        val full = SampleUi.week.copy(days = SampleUi.week.days.map {
            if (it.date == wednesday) it.copy(events = List(30) { n -> SampleUi.event("Errand $n", "09:00–10:00", SampleUi.sam) }) else it
        })
        show { WeekView(WeekViewState(full, SampleUi.TODAY, sync(), now), onAdd = { added += it }) }
        val header = compose.onNodeWithContentDescription(dayHeading(wednesday, "Today"))
        val actions = header.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Add event on Wednesday 23 September")
        compose.runOnUiThread { actions.single().action() }
        assertThat(added).containsExactly(wednesday)
    }

    @Test
    fun noAddTargetsWithoutOnAdd() {
        show { WeekView(state()) }
        val isDayAdd = SemanticsMatcher("a day's add node") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> tag.startsWith("week_add_") && tag != "week_add_event" } == true
        }
        compose.onAllNodes(isDayAdd, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { down(belowChip(sunday, sundayLastChip)) }
        compose.onAllNodesWithText("Add on ", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("week_day_$sunday").performTouchInput { up() }
        SampleUi.week.days.forEach { day ->
            val heading = dayHeading(day.date, if (day.date == SampleUi.TODAY) "Today" else null)
            val node = compose.onNodeWithContentDescription(heading).fetchSemanticsNode()
            assertThat(node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()).isEmpty()
        }
    }

    @Test
    fun addEventAndThisWeekAre48DpButtons() {
        show { WeekView(state().copy(weeksAhead = 1), onAdd = {}) }
        listOf("week_add_event", "week_this_week").forEach { compose.onNodeWithTag(it).assert(isButton).assertHeightIsEqualTo(48.dp) }
    }

    @Test
    fun aTapOnAColumnsHeaderAddsNothing() {
        val added = mutableListOf<LocalDate?>()
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
        compose.onNodeWithTag("week_subtitle").assert(hasText("Next week", substring = true))
        repeat(MAX_WEEKS_AHEAD) { compose.onNodeWithTag("week_later").performClick() }
        compose.onNodeWithTag("week_subtitle").assert(hasText("In 3 weeks", substring = true))
        compose.onNodeWithTag("week_later").assertIsNotEnabled()
        compose.onNodeWithTag("week_earlier").assertIsEnabled()
        compose.onNodeWithTag("week_this_week").performClick()
        assertThat(asked).containsExactly(1, 2, 3, 0).inOrder()
        compose.onNodeWithTag("week_subtitle").assert(hasText("This week", substring = true))
        compose.onNodeWithTag("week_this_week").assertDoesNotExist()
    }

    @Test
    fun eachWeekHasItsTitle() {
        assertThat((0..MAX_WEEKS_AHEAD).map(::weekTitle)).containsExactly("This week", "Next week", "In 2 weeks", "In 3 weeks").inOrder()
    }

    /** 4c §6.6: on a later week, Add event opens with no day and a column on its own day; no column says Today. */
    @Test
    fun aLaterWeekAddsOnItsOwnDays() {
        val added = mutableListOf<LocalDate?>()
        show { WeekView(WeekViewState(SampleUi.weekAhead(1), SampleUi.TODAY, sync(), now, weeksAhead = 1), onAdd = { added += it }) }
        compose.onNodeWithTag("week_add_event").performClick()
        compose.onNodeWithTag("week_add_${SampleUi.TODAY.plusDays(9)}").performClick()
        assertThat(added).containsExactly(null, SampleUi.TODAY.plusDays(9)).inOrder()
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
        val chip = compose.onClickableShowing("Parkrun")
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
        compose.onNodeWithTag("week_title").assert(isHeading()).assertContentDescriptionEquals("September 2026")
    }

    /** 5 §A.3: the reason in the week's line, with Update now still beside it. */
    @Test
    fun aFailedUpdateSaysWhyAndKeepsUpdateNow() {
        show { WeekView(state(sync(agoMinutes = 45, connections = listOf("Google Calendar")).copy(problem = SyncProblem.Offline))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · The tablet is offline. Check the Wi-Fi, then Update now.")
        compose.onNodeWithTag("week_update_now").assertExists()
        compose.onNodeWithTag("week_out_of_date", useUnmergedTree = true).assertExists()
    }

    /** 5 §A.3: a failed pass shows at once, with Update now, before the week counts as out of date. */
    @Test
    fun aFreshWeekWithAProblemStillSaysWhyAndOffersUpdateNow() {
        show { WeekView(state(sync(agoMinutes = 2, connections = listOf("Google Calendar")).copy(problem = SyncProblem.NoAnswer("Google Calendar")))) }
        compose.onNodeWithTag("week_subtitle").assertTextEquals("This week · Wed 23 – Tue 29 Sep · Google Calendar didn’t answer. Try Update now in a few minutes.")
        compose.onNodeWithTag("week_update_now").assertExists()
    }
}
