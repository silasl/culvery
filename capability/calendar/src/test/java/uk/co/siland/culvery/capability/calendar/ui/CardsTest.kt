package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.DaySpan
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HalloweenNight
import uk.co.siland.culvery.core.ui.HalloweenDimens
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.onClickableShowing
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.core.ui.test.windowBitmap

// app's HomeGridTest pins the same size for Home's Today card.
internal val todayCardSize = DpSize(400.dp, 586.dp)

/** Native graphics: the sized tests check that rows fit the real card sizes, which needs real text metrics. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** 9 §4.2: Today's titles 21 sp and their time line 16 sp; other cards' rows keep their sizes. */
    @Test
    fun todaysRowsUseTheLargerSizes() {
        show { TodayCard(SampleUi.today) }
        assertThat(compose.onNodeWithText("School run", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(21.sp)
        assertThat(compose.onNodeWithText("07:45–08:30 · Sam", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(16.sp)
    }

    private var seasonal by mutableStateOf<SeasonalTheme?>(null)

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true, seasonal = seasonal) { content() }
        }
    }

    /** The card at the size the Home grid gives it on the 1280×800 canvas. */
    private fun showSized(size: DpSize = todayCardSize, content: @Composable () -> Unit) = show {
        Box(Modifier.size(size)) { content() }
    }

    private val tomorrowDate = SampleUi.TODAY.plusDays(1)

    private fun events(prefix: String, count: Int) = List(count) { SampleUi.event("$prefix ${it + 1}", "1$it:00–1$it:30", SampleUi.alex) }

    private val sam = 0xFF5B9BE0.toInt()
    private val mia = 0xFFE07BA8.toInt()
    private val alex = 0xFF4CB387.toInt()

    private fun barBand(dy: (Dp) -> Dp): Int {
        val bar = compose.onNodeWithTag("event_bar", useUnmergedTree = true)
        return pixelAt(bar, 1.dp, dy(bar.getUnclippedBoundsInRoot().height))
    }

    @Test
    fun aSharedTodayRowHasOneStripePerPersonAndTheWhoLine() {
        show { TodayCard(SampleUi.shared) }
        compose.onAllNodesWithText("Cinema").assertCountEquals(1)
        compose.onNodeWithText("17:00–18:00 · Sam + Mia").assertExists()
        assertThat(barBand { 2.dp }).isEqualTo(sam)
        assertThat(barBand { it - 2.dp }).isEqualTo(mia)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Sam's and Mia's colours at 75 % over the card's dark background. */
    private fun assertNear(argb: Int, r: Int, g: Int, b: Int) {
        val actual = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
        actual.zip(listOf(r, g, b)).forEach { (a, e) -> assertThat(a).isIn(Range.closed(e - 2, e + 2)) }
    }

    @Test
    fun aSharedTomorrowRowKeepsItsFade() {
        show { TodayCard(emptyList(), tomorrow = DayUi(tomorrowDate, SampleUi.shared)) }
        assertNear(barBand { 2.dp }, 75, 124, 176)
        assertNear(barBand { it - 2.dp }, 175, 100, 134)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin. */
    @Test
    fun aSinglePersonRowIsUnchanged() {
        show { TodayCard(listOf(SampleUi.event("Dinner", "19:30–21:00", SampleUi.alex))) }
        assertThat(barBand { 2.dp }).isEqualTo(alex)
        assertThat(barBand { it - 2.dp }).isEqualTo(alex)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun comingUpUsesTheWhoLine() {
        show { ComingUpCard(listOf(DayUi(tomorrowDate, SampleUi.shared))) }
        compose.onNodeWithText("17:00–18:00 · Sam + Mia").assertExists()
        assertThat(barBand { 2.dp }).isEqualTo(sam)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSharedEventIsSpokenWithAnd() {
        show { TodayCard(SampleUi.shared) }
        compose.onNode(hasContentDescription("Cinema, Sam and Mia, 17:00 to 18:00", substring = true)).assertExists()
        assertThat(eventOnDay(SampleUi.cinema, SampleUi.TODAY)).startsWith("Cinema, Sam and Mia, ")
        compose.assertClickablesHaveRoleAndLabel()
    }

    private val pill = hasText("more today · Open calendar", substring = true)

    @Test
    fun todayRowShowsTimeAndPerson() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("School run").assertExists()
        compose.onNodeWithText("07:45–08:30 · Sam").assertExists()
    }

    /** 9 §4.2: the handoff's words, with or without the +. */
    @Test
    fun anEmptyTodayWithAddSaysNothingOnToday() {
        show { TodayCard(emptyList(), onAdd = {}) }
        compose.onNodeWithText("Nothing on today").assertExists()
        compose.onNodeWithText("Tap + to add something", substring = true).assertDoesNotExist()
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
        showSized { TodayCard(SampleUi.today) }
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
        showSized(DpSize(705.dp, 279.dp)) { ComingUpCard(SampleUi.comingUpBusy) }
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

    /** The card's pixels in the [HalloweenDimens.webSpan] squares that aren't [surf], outside the buttons and the rounded corner. */
    private inner class WebCorners(val surf: Color) {
        private val card = compose.onNodeWithTag("calendar_today")
        private val node = card.fetchSemanticsNode()
        private val image = windowBitmap(card)
        private val density = node.layoutInfo.density.density
        private val bounds = node.boundsInWindow
        private val buttons = listOf("today_add" to true, "See the week" to false).map { (name, tag) ->
            (if (tag) compose.onNodeWithTag(name) else compose.onNodeWithText(name)).fetchSemanticsNode().boundsInWindow
        }
        private val span = (HalloweenDimens.webSpan.value * density).toInt()
        private val arc = (CalendarDimens.cardRadius.value * density).toInt()

        private fun differs(x: Int, y: Int) = image.getPixel(x, y) != surf.toArgb() && buttons.none { it.contains(Offset(x + 0.5f, y + 0.5f)) }

        fun topRight(): Int {
            val right = bounds.right.toInt()
            val top = bounds.top.toInt()
            return (top until top + span).sumOf { y ->
                (right - span until right).count { x -> !(x >= right - arc && y < top + arc) && differs(x, y) }
            }
        }

        fun bottomLeft(): Int {
            val left = bounds.left.toInt()
            val bottom = bounds.bottom.toInt()
            return (bottom - span until bottom).sumOf { y ->
                (left until left + span).count { x -> !(x < left + arc && y >= bottom - arc) && differs(x, y) }
            }
        }

        /** Pixels on the corner's diagonal, 8 to 60 dp in. */
        fun diagonal(): Int = (8..60).count { d ->
            val x = bounds.right.toInt() - (d * density).toInt() - 1
            val y = bounds.top.toInt() + (d * density).toInt()
            (-1..1).any { differs(x + it, y) }
        }
    }

    private fun emptyToday() = showSized { TodayCard(emptyList(), onAdd = {}) }

    @Test
    fun theWebSitsInTheTopRightCorner() {
        seasonal = SeasonalTheme.HALLOWEEN
        emptyToday()
        val corners = WebCorners(HalloweenNight.surf)
        assertThat(corners.topRight()).isGreaterThan(0)
        assertThat(corners.diagonal()).isGreaterThan(0)
        assertThat(corners.bottomLeft()).isEqualTo(0)
    }

    /** Pin: no theme, no web. */
    @Test
    fun noWebWithoutATheme() {
        emptyToday()
        assertThat(WebCorners(DarkColors.surf).topRight()).isEqualTo(0)
    }

    @Test
    fun theButtonsStayWhereTheyAre() {
        var added = 0
        showSized { TodayCard(SampleUi.today, onAdd = { added++ }) }
        fun spots() = listOf(compose.onNodeWithTag("today_add"), compose.onNodeWithText("See the week")).map { it.getUnclippedBoundsInRoot() }
        val normal = spots()
        seasonal = SeasonalTheme.HALLOWEEN
        compose.waitForIdle()
        assertThat(spots()).isEqualTo(normal)
        compose.onNodeWithTag("today_add").performClick()
        compose.onNodeWithText("See the week").performClick()
        assertThat(added).isEqualTo(1)
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
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

    @Test
    fun sevenTodayScrollsBehindThePill() {
        showSized { TodayCard(events("Event", 7)) }
        compose.onNodeWithText("See the week").assertDoesNotExist()
        compose.onNodeWithText("3 more today · Open calendar").assertIsDisplayed().performClick()
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun fiveTodayShowsFiveAndNoPill() {
        showSized { TodayCard(SampleUi.today) }
        SampleUi.today.forEach { compose.onNodeWithText(it.title).assertIsDisplayed() }
        compose.onNodeWithContentDescription("Tomorrow").assertDoesNotExist()
        compose.onAllNodes(pill).assertCountEquals(0)
        compose.onNodeWithText("See the week").performClick()
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun twoTodayAddsThreeFromTomorrow() {
        val later = events("Later", 4)
        showSized { TodayCard(events("Event", 2), tomorrow = DayUi(tomorrowDate, later)) }
        compose.onNodeWithContentDescription("Tomorrow").assert(isHeading()).assertIsDisplayed()
        compose.onNodeWithText("TOMORROW", useUnmergedTree = true).assertExists()
        later.take(3).forEach {
            compose.onNodeWithContentDescription(eventOnDay(it, tomorrowDate)).assert(isButton).assertIsDisplayed()
            assertThat(compose.onNodeWithText(it.title, useUnmergedTree = true).textLayout().layoutInput.maxLines).isEqualTo(1)
        }
        compose.onNodeWithText("Later 4", useUnmergedTree = true).assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aShortTomorrowTopsUpWhatItHas() {
        val later = events("Later", 1)
        showSized { TodayCard(events("Event", 2), tomorrow = DayUi(tomorrowDate, later)) }
        compose.onNodeWithContentDescription("Tomorrow").assert(isHeading())
        compose.onNodeWithContentDescription(eventOnDay(later.single(), tomorrowDate)).assertIsDisplayed()
        compose.onAllNodes(hasText("Event ", substring = true), useUnmergedTree = true).assertCountEquals(2)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun nothingTodaySaysSoAndShowsFiveFromTomorrow() {
        val later = events("Later", 6)
        showSized { TodayCard(emptyList(), onAdd = {}, tomorrow = DayUi(tomorrowDate, later)) }
        compose.onNodeWithText("Nothing on today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tomorrow").assert(isHeading()).assertIsDisplayed()
        later.take(5).forEach { compose.onNodeWithContentDescription(eventOnDay(it, tomorrowDate)).assertIsDisplayed() }
        compose.onNodeWithText("Later 6", useUnmergedTree = true).assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEventSpanningMidnightShowsOnceAsToday() {
        val sleepover = SampleUi.event("Sleepover", "20:00–", SampleUi.mia)
        showSized { TodayCard(listOf(sleepover), tomorrow = DayUi(tomorrowDate, listOf(sleepover.copy(timeLabel = "until 10:00")))) }
        compose.onAllNodesWithText("Sleepover", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithContentDescription(todayRowName(sleepover)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Tomorrow").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aMultiDayEventReadsWhichDayItIs() {
        val halfTerm = SampleUi.spanning("Half term", SampleUi.family, 2, 3)
        showSized { TodayCard(listOf(halfTerm)) }
        compose.onNodeWithText("Day 2 of 3 · Family", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Half term, Family, day 2 of 3").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: an overnight event is not multi-day, so it keeps its times. */
    @Test
    fun anOvernightEventKeepsItsTimes() {
        val sleepover = SampleUi.event("Sleepover", "20:00–", SampleUi.mia)
        showSized { TodayCard(listOf(sleepover)) }
        compose.onNodeWithText("20:00– · Mia", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(todayRowName(sleepover)).assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: tomorrow's top-up skips an event that is already one of today's rows. */
    @Test
    fun aMultiDayEventTomorrowShowsOnce() {
        val today = SampleUi.spanning("Half term", SampleUi.family, 2, 3)
        showSized { TodayCard(listOf(today), tomorrow = DayUi(tomorrowDate, listOf(today.copy(span = DaySpan(3, 3))))) }
        compose.onAllNodesWithText("Half term", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Day 2 of 3 · Family", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Tomorrow").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Five one-line rows fit at the default font; at 1.5 they don't, so the card scrolls rather than cut one (9 §4.2). */
    @Test
    fun aLargeFontTurnsFiveRowsIntoTheScroll() {
        var scale by mutableFloatStateOf(1f)
        showSized { WithFontScale(scale) { TodayCard(SampleUi.today) } }
        SampleUi.today.forEach { assertThat(compose.onNodeWithText(it.title, useUnmergedTree = true).textLayout().lineCount).isEqualTo(1) }
        compose.onAllNodes(pill).assertCountEquals(0)
        compose.onNodeWithText("See the week").assertIsDisplayed()

        scale = 1.5f
        compose.onNode(pill).assertIsDisplayed()
        compose.onNodeWithText("See the week").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun todayTitlesWrapToTwoLines() {
        showSized { TodayCard(SampleUi.today) }
        val input = compose.onNodeWithText("School run", useUnmergedTree = true).textLayout().layoutInput
        assertThat(input.maxLines).isEqualTo(2)
        assertThat(input.overflow).isEqualTo(TextOverflow.Ellipsis)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun tomorrowRowsOpenTheirEvent() {
        val opened = mutableListOf<EventRef>()
        val later = events("Later", 3)
        showSized { TodayCard(events("Event", 2), onOpen = { opened += it }, tomorrow = DayUi(tomorrowDate, later)) }
        compose.onNodeWithContentDescription(eventOnDay(later[1], tomorrowDate)).performClick()
        assertThat(opened).containsExactly(later[1].ref)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
