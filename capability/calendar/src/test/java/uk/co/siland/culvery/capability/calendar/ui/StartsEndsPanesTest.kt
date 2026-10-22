package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.EventWhen
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.HeldEvent
import uk.co.siland.culvery.capability.calendar.lastShownDay
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.windowBitmap

/** The Starts and Ends panes in the editor's right-hand pane (688 × 686 dp of the 1280 × 800 dp canvas), at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartsEndsPanesTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private enum class Pane(val question: String, val tag: String) {
        StartDate(STARTS_DATE_QUESTION, "starts_date_pane"),
        StartTime(STARTS_TIME_QUESTION, "starts_time_pane"),
        EndDate(ENDS_DATE_QUESTION, "ends_date_pane"),
        EndTime(ENDS_TIME_QUESTION, "ends_time_pane"),
    }

    private val c = DarkColors
    private val today = SampleUi.TODAY
    private val saturday = LocalDate.of(2026, 9, 26)
    private val lastShown = lastShownDay(today)
    private val household = EditorPeople(SampleUi.members)
    private val flow = EditorFlow(listOf(EditorStep.Who, EditorStep.StartDate, EditorStep.Check), EditorStep.StartDate)

    private var which by mutableStateOf(Pane.StartDate)
    private var eventWhen by mutableStateOf(saturdayAt(10, 0, lengthMinutes = 60))
    private var held by mutableStateOf<Held?>(null)
    private var who by mutableStateOf(listOf(SampleUi.mia.id))
    private var except by mutableStateOf<EventRef?>(null)
    private var scale by mutableStateOf(1f)
    private var picked = 0
    private var banner by mutableStateOf<String?>(null)
    private var quickPicks by mutableStateOf(emptyList<LocalDate>())

    private fun saturdayAt(hour: Int, minute: Int, lengthMinutes: Long, day: LocalDate = saturday): EventWhen {
        val end = LocalTime.of(hour, minute).plusMinutes(lengthMinutes)
        return EventWhen(false, day, LocalTime.of(hour, minute), if (end < LocalTime.of(hour, minute)) 1 else 0, end)
    }

    private fun show(first: Pane) {
        which = first
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) {
                    Box(Modifier.size(688.dp, 686.dp)) {
                        val context = WhenContext(held, household, who, except, LONDON, today)
                        EditorPane(which.question, flow, skip = null) {
                            val freeStart = { clock: LocalTime -> eventWhen = eventWhen.withStart(clock) }
                            when (which) {
                                Pane.StartDate -> StartDatePane(eventWhen, context, banner, quickPicks) {
                                    eventWhen = eventWhen.pickStartDate(it, defaults = false, today, LocalTime.of(11, 54))
                                    picked++
                                }
                                Pane.StartTime -> StartTimePane(eventWhen, context, { field, up -> eventWhen = eventWhen.stepStart(field, up) }, freeStart)
                                Pane.EndDate -> EndDatePane(eventWhen, context) { date ->
                                    eventWhen.pickEndDate(date)?.let {
                                        eventWhen = it
                                        picked++
                                    }
                                }
                                Pane.EndTime -> EndTimePane(eventWhen, context, { field, up -> eventWhen.stepEnd(field, up)?.let { eventWhen = it } }, freeStart)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Mia's Piano, 15:30–16:15, on each of [days]. */
    private fun piano(vararg days: LocalDate, ref: String = "piano", unhealthy: Set<PersonId> = emptySet()) = Held(
        days.map { d ->
            HeldEvent(
                EventRef("c1", "s1", "$ref$d"), "Piano", listOf(SampleUi.mia.id),
                EventTime.Timed(d.atTime(15, 30).atZone(LONDON).toInstant()), EventTime.Timed(d.atTime(16, 15).atZone(LONDON).toInstant()),
            )
        },
        lastDay = lastShown,
        unhealthy = unhealthy,
        firstDay = today,
    )

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun cell(date: LocalDate) = node("day_$date")

    private fun texts(tag: String): List<String> = node(tag).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    private fun anyPixel(node: SemanticsNodeInteraction, colour: Color): Boolean {
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val bitmap = windowBitmap(node)
        val want = colour.toArgb()
        return (bounds.left.toInt() until bounds.right.toInt()).any { x ->
            (bounds.top.toInt() until bounds.bottom.toInt()).any { y -> bitmap.getPixel(x, y) == want }
        }
    }

    /** 15:00 for 45 minutes on [day]: over Mia's Piano. */
    private fun clashAt3(day: LocalDate = saturday) = saturdayAt(15, 0, lengthMinutes = 45, day = day)

    @Test
    fun aBannerAndQuickPicksShowAboveTheGridAndAPickIsAStartsDatePick() {
        eventWhen = eventWhen.copy(startDate = null, endDays = 0)
        banner = "Copied from Guitar lesson (last on Sat 12 Sep): who, time and repeats. Tap any row to change it. The date is never copied."
        quickPicks = listOf(saturday, saturday.plusDays(7), saturday.plusDays(14))
        show(Pane.StartDate)
        node("copied_banner").assertTextEquals(banner!!)
        assertThat(anyPixel(node("copied_banner"), c.accentSoft)).isTrue()
        val bannerBounds = node("copied_banner").getUnclippedBoundsInRoot()
        val picksBounds = node("quick_picks").getUnclippedBoundsInRoot()
        val gridBounds = node("starts_date_pane").getUnclippedBoundsInRoot()
        assertThat(bannerBounds.bottom.value).isAtMost(picksBounds.top.value)
        assertThat(picksBounds.bottom.value).isAtMost(gridBounds.top.value)
        listOf("Sat 26 Sep", "Sat 3 Oct", "Sat 10 Oct").forEachIndexed { i, label ->
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("quick_pick_$i")), useUnmergedTree = true).assertExists()
        }
        node("quick_pick_1").performClick()
        assertThat(eventWhen.startDate).isEqualTo(saturday.plusDays(7))
        assertThat(picked).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun withNoBannerOrQuickPicksOnlyTheGridShows() {
        show(Pane.StartDate)
        node("copied_banner").assertDoesNotExist()
        node("quick_picks").assertDoesNotExist()
        node("starts_date_pane").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun eachPaneAsksItsQuestion() {
        show(Pane.StartDate)
        Pane.entries.forEach { pane ->
            compose.runOnIdle { which = pane }
            node(pane.tag).assertExists()
            part("editor_question").assertTextEquals(pane.question)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, pane.question))
        }
        assertThat(Pane.entries.map { it.question }).containsExactly(
            "Which day does it start?", "What time does it start?", "Which day does it end?", "What time does it end?",
        ).inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aStartsPickKeepsTheLengthAndMovesOn() {
        eventWhen = EventWhen(false, saturday, LocalTime.of(18, 0), 2, LocalTime.of(16, 0))
        show(Pane.StartDate)
        cell(saturday).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        cell(saturday.plusDays(2)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        cell(saturday.plusDays(1)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "in the range"))
        cell(saturday.plusDays(7)).performClick()
        compose.runOnIdle {
            assertThat(eventWhen).isEqualTo(EventWhen(false, saturday.plusDays(7), LocalTime.of(18, 0), 2, LocalTime.of(16, 0)))
            assertThat(picked).isEqualTo(1)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theEndsGridDimsDaysBeforeTheStartAndPastFiftyNine() {
        show(Pane.EndDate)
        val before = saturday.minusDays(1)
        assertThat(cell(before).fetchSemanticsNode().config[DayFade]).isEqualTo(HhAlpha.PAST_DAY)
        cell(before).assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        cell(before).performClick()
        cell(saturday).assertIsEnabled()
        cell(saturday.plusDays(1)).performClick()
        compose.runOnIdle {
            assertThat(eventWhen.endDays).isEqualTo(1)
            assertThat(picked).isEqualTo(1)
        }
        val lastAllowed = saturday.plusDays(59)
        repeat(2) { node("month_next").performClick() }
        node("month_title").assertTextEquals("November 2026")
        cell(lastAllowed).assertIsEnabled()
        cell(lastAllowed.plusDays(1)).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        assertThat(cell(lastAllowed.plusDays(1)).fetchSemanticsNode().config[DayFade]).isEqualTo(HhAlpha.PAST_DAY)
        cell(lastAllowed).performClick()
        compose.runOnIdle {
            assertThat(eventWhen.endDays).isEqualTo(59)
            assertThat(picked).isEqualTo(2)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEndsHourDownThatWouldReachTheStartIsDisabled() {
        show(Pane.EndTime)
        node("end_hour_down").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        node("end_hour_down").performClick()
        compose.runOnIdle { assertThat(eventWhen.endClock).isEqualTo(LocalTime.of(11, 0)) }
        node("end_hour_up").assertIsEnabled()
        node("end_minute_down").assertIsEnabled().performClick()
        compose.runOnIdle { assertThat(eventWhen.endClock).isEqualTo(LocalTime.of(10, 45)) }
        node("end_minute_value").assertTextEquals("45")
        node("start_hour_up").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aStartsStepMovesTheEndToo() {
        show(Pane.StartTime)
        node("start_hour_up").performClick()
        compose.runOnIdle { assertThat(eventWhen).isEqualTo(saturdayAt(11, 0, lengthMinutes = 60)) }
        node("start_hour_value").assertTextEquals("11")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aFreeStartMovesTheStartAndTheEndFollows() {
        held = piano(saturday)
        eventWhen = clashAt3()
        show(Pane.StartTime)
        node("time_free_1445").assertTextEquals("14:45")
        node("time_free_1615").performClick()
        compose.runOnIdle { assertThat(eventWhen).isEqualTo(saturdayAt(16, 15, lengthMinutes = 45)) }
        assertThat(texts("time_result")).containsExactly("Mia is free then")
        node("time_free").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun overSeveralDaysThereAreNoFreeStarts() {
        held = piano(saturday)
        eventWhen = EventWhen(false, saturday, LocalTime.of(15, 0), 1, LocalTime.of(15, 45))
        show(Pane.EndTime)
        assertThat(texts("time_result")).containsExactly("Overlaps Piano, Sat 26 Sep 15:30–16:15")
        part("time_free_at").assertDoesNotExist()
        node("time_free_1445").assertDoesNotExist()
        part("time_free_tail").assertTextEquals("Clashes are allowed.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun allDayHasNoClashLine() {
        held = piano(saturday)
        eventWhen = clashAt3().withAllDay(true)
        show(Pane.StartTime)
        node("time_result").assertDoesNotExist()
        node("time_free").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theResultSaysFree() {
        held = piano(saturday)
        show(Pane.StartTime)
        assertThat(texts("time_result")).containsExactly("Mia is free then")
        val line = compose.onNode(hasText("Mia is free then"), useUnmergedTree = true).textLayout().layoutInput.style
        assertThat(line.color).isEqualTo(c.accent)
        assertThat(anyPixel(part("time_result_icon"), c.accent)).isTrue()
        node("time_free").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aClashWarnsAndNamesTheFreeStarts() {
        held = piano(saturday)
        eventWhen = clashAt3()
        show(Pane.StartTime)
        assertThat(texts("time_result")).containsExactly("Overlaps Piano, 15:30–16:15")
        val line = compose.onNode(hasText("Overlaps Piano, 15:30–16:15"), useUnmergedTree = true).textLayout().layoutInput.style
        assertThat(line.color).isEqualTo(c.weather)
        assertThat(anyPixel(part("time_result_icon"), c.weather)).isTrue()
        part("time_free_at").assertTextEquals("Mia is free at")
        node("time_free_1445").assertTextEquals("14:45")
        node("time_free_1615").assertTextEquals("16:15")
        part("time_free_tail").assertTextEquals("or keep it; clashes are allowed.")
        assertThat(listOf("time_free_at", "time_free_1445", "time_free_1615").map { part(it).getUnclippedBoundsInRoot().left }).isInStrictOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noLineBeyondTheHeldWindow() {
        held = piano(lastShown, lastShown.plusDays(1))
        eventWhen = clashAt3(lastShown)
        show(Pane.StartTime)
        assertThat(texts("time_result")).containsExactly("Overlaps Piano, 15:30–16:15")

        compose.runOnIdle { eventWhen = clashAt3(lastShown.plusDays(1)) }
        node("time_result").assertDoesNotExist()
        part("time_result_icon").assertDoesNotExist()
        node("time_free").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun everyoneHasNoCheck() {
        held = piano(saturday)
        eventWhen = clashAt3()
        show(Pane.StartTime)
        node("time_result").assertExists()
        compose.runOnIdle { who = listOf(PersonId.FAMILY) }
        node("time_result").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anUnhealthyCalendarHasNoCheck() {
        held = piano(saturday)
        eventWhen = clashAt3()
        show(Pane.StartTime)
        node("time_result").assertExists()
        compose.runOnIdle { held = piano(saturday, unhealthy = setOf(SampleUi.mia.id)) }
        node("time_result").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Changing Piano itself, at its own time: it doesn't clash with itself, though another Piano at the same time does. */
    @Test
    fun theEventBeingChangedDoesNotClashWithItself() {
        held = piano(saturday, ref = "e")
        eventWhen = saturdayAt(15, 30, lengthMinutes = 45)
        except = EventRef("c1", "s1", "another")
        show(Pane.StartTime)
        assertThat(texts("time_result")).containsExactly("Overlaps Piano, 15:30–16:15")

        compose.runOnIdle { except = EventRef("c1", "s1", "e$saturday") }
        assertThat(texts("time_result")).containsExactly("Mia is free then")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** At 1.3 and 1.5 every step button, the clash line and its free starts sit inside the pane. */
    @Test
    fun theStepsPaneFitsAtFontScales13And15() {
        held = piano(saturday)
        eventWhen = clashAt3()
        show(Pane.StartTime)
        listOf(1.3f, 1.5f).forEach { s ->
            compose.runOnIdle { scale = s }
            listOf(Pane.StartTime, Pane.EndTime).forEach { pane ->
                compose.runOnIdle { which = pane }
                val prefix = if (pane == Pane.StartTime) "start_" else "end_"
                val bounds = node("editor_pane").getUnclippedBoundsInRoot()
                listOf("hour_up", "hour_down", "minute_up", "minute_down", "hour_value", "minute_value").forEach { part ->
                    val b = node("$prefix$part").performScrollTo().getUnclippedBoundsInRoot()
                    assertWithMessage("$prefix$part's top at $s").that(b.top).isAtLeast(bounds.top)
                    assertWithMessage("$prefix$part's foot at $s").that(b.bottom).isAtMost(bounds.bottom)
                    assertWithMessage("$prefix$part's right at $s").that(b.right).isAtMost(bounds.right)
                }
                listOf("time_result", "time_free_1445", "time_free_1615", "time_free_tail").forEach { tag ->
                    val b = node(tag).performScrollTo().getUnclippedBoundsInRoot()
                    assertWithMessage("$tag's foot at $s").that(b.bottom).isAtMost(bounds.bottom)
                    assertWithMessage("$tag's right at $s").that(b.right).isAtMost(bounds.right)
                }
                val value = part("${prefix}hour_value").textLayout()
                assertWithMessage("the hour on one line at $s").that(value.lineCount).isEqualTo(1)
            }
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theStartsDatePaneFitsWithoutScrollingForASixWeekMonth() {
        eventWhen = EventWhen(false, LocalDate.of(2026, 8, 20), LocalTime.of(10, 0), 0, LocalTime.of(11, 0))
        show(Pane.StartDate)
        node("month_title").assertTextEquals("August 2026")
        cell(LocalDate.of(2026, 9, 6)).assertExists()
        val range = node("editor_pane").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertThat(range.maxValue()).isEqualTo(0f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A long span shows each end where it is: the Starts grid on August, the Ends grid on October. */
    @Test
    fun eachGridOpensOnItsOwnMonth() {
        eventWhen = EventWhen(false, LocalDate.of(2026, 8, 20), LocalTime.of(10, 0), 46, LocalTime.of(11, 0))
        show(Pane.StartDate)
        node("month_title").assertTextEquals("August 2026")
        compose.runOnIdle { which = Pane.EndDate }
        node("month_title").assertTextEquals("October 2026")
        cell(LocalDate.of(2026, 10, 5)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.runOnIdle { which = Pane.StartDate }
        node("month_title").assertTextEquals("August 2026")
    }
}
