package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.HeldEvent
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.UNTIL_TOO_LATE
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Handoff 6c and 6d in the editor on the 1280 × 800 dp canvas, at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private val today = SampleUi.TODAY
    private var form by mutableStateOf(editorForm())
    private var flow by mutableStateOf(editorFlow(form, null, EditorStep.Day))
    private var held by mutableStateOf<Held?>(null)
    private var scale by mutableStateOf(1f)
    private val screen = ShownOnce(compose)

    /** Puts [editor] on screen opened on Day, in place of the one showing. */
    private fun show(editor: EditorForm = editorForm()) {
        form = editor
        flow = editorFlow(editor, null, EditorStep.Day)
        screen.show {
            ShowEditor(form, flow, held = held, scale = scale)
        }
    }

    /** Handoff 6d's trip for everyone, with "Lasts more than a day" on and [taps] made. */
    private fun multiDay(vararg taps: LocalDate) = editorForm().apply {
        updateTitle("Half-term trip to Devon")
        toggleWho(PersonId.FAMILY)
        setMultiDay(true)
        taps.forEach(::tapDay)
    }

    private fun cell(date: LocalDate) = compose.onNodeWithTag("day_$date")

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun fadeOf(date: LocalDate): Float = cell(date).fetchSemanticsNode().config[DayFade]

    private fun height(tag: String) = part(tag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun width(tag: String) = part(tag).getUnclippedBoundsInRoot().let { it.right - it.left }

    /** A pixel in the band's row of [date]'s cell: [dx] from its left, or from its right when negative; [dy] below the band's top. */
    private fun bandPixel(date: LocalDate, dx: Float, dy: Float = 26f): Int {
        val w = width("day_$date").value
        val bandTop = (height("day_$date").value - 53f) / 2
        return pixelAt(cell(date), (if (dx < 0) w + dx else dx).dp, (bandTop + dy).dp)
    }

    /** The circle's top centre, 3 dp in: its fill, clear of the date and the dots. */
    private fun circleFill(date: LocalDate): Int = pixelAt(part("day_${date}_circle"), (width("day_${date}_circle").value / 2).dp, 3.dp)

    private fun at(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    @Test
    fun quickPicksAreAsTheHandoffSays() {
        assertThat(quickPicks(today)).containsExactly(
            "Today" to today,
            "Tomorrow" to at(9, 24),
            "Sat 26 Sep" to at(9, 26),
            "Wednesday next week" to at(9, 30),
        ).inOrder()
        val friday = at(9, 25)
        assertThat(quickPicks(friday)[2]).isEqualTo("Sun 27 Sep" to at(9, 27))
        assertThat(quickPicks(friday)[3]).isEqualTo("Friday next week" to at(10, 2))
        show()
        listOf("Today", "Tomorrow", "Sat 26 Sep", "Wednesday next week").forEachIndexed { i, label ->
            compose.onNodeWithTag("day_quick_$i").assertTextEquals(label)
        }
        compose.onNodeWithTag("day_quick_2").performClick()
        compose.runOnIdle { assertThat(form.day).isEqualTo(at(9, 26)) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theMonthStartsOnMonday() {
        val cells = monthCells(YearMonth.of(2026, 9))
        assertThat(cells.first()).isEqualTo(at(8, 31))
        assertThat(cells.last()).isEqualTo(at(10, 4))
        assertThat(cells).hasSize(35)
        assertThat(cells.first().dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(monthCells(YearMonth.of(2026, 8))).hasSize(42)
        show()
        compose.onNodeWithTag("month_title").assertTextEquals("September 2026")
        val letters = part("month_letters").fetchSemanticsNode().children.flatMap { it.config[SemanticsProperties.Text] }.map { it.text }
        assertThat(letters).containsExactly("M", "T", "W", "T", "F", "S", "S").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun pastAndOutsideDaysFade() {
        show()
        assertThat(fadeOf(at(9, 21))).isEqualTo(HhAlpha.PAST_DAY)
        assertThat(fadeOf(at(8, 31))).isEqualTo(0.58f)
        assertThat(fadeOf(at(10, 1))).isEqualTo(0.58f)
        assertThat(fadeOf(today)).isEqualTo(1f)
        assertThat(fadeOf(at(9, 30))).isEqualTo(1f)
        // Any date is allowed, the past included.
        cell(at(9, 21)).performClick()
        compose.runOnIdle { assertThat(form.day).isEqualTo(at(9, 21)) }
        assertThat(fadeOf(at(9, 21))).isEqualTo(1f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun todayIsRingedAndTheChosenDayFilled() {
        show()
        val ring = pixelAt(part("day_${today}_circle"), 1.dp, (height("day_${today}_circle").value / 2).dp)
        assertWithMessage("today's ring").that(ring).isEqualTo(c.accent.toArgb())
        assertThat(circleFill(today)).isEqualTo(c.surf.toArgb())
        assertThat(part("day_${today}_date").textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(part("day_${at(9, 24)}_date").textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W500)
        val sat = at(9, 26)
        cell(sat).performClick()
        assertWithMessage("the chosen day's fill").that(circleFill(sat)).isEqualTo(c.accent.toArgb())
        assertThat(part("day_${sat}_date").textLayout().layoutInput.style.color).isEqualTo(c.accentInk)
        assertThat(part("day_${sat}_date").textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(cell(sat).fetchSemanticsNode().config[SemanticsProperties.Selected]).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aPickMovesOnToTime() {
        show()
        compose.mainClock.autoAdvance = false
        cell(at(9, 26)).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS - 16, ignoreFrameDuration = true)
        assertThat(form.day).isEqualTo(at(9, 26))
        assertThat(flow.active).isEqualTo(EditorStep.Day)
        compose.mainClock.advanceTimeBy(32, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Time)
        compose.mainClock.autoAdvance = true
        part("editor_row_day_value").assertTextEquals("Sat 26 Sep")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun upToThreeDotsFromHeldEvents() {
        fun allDay(date: LocalDate, people: List<PersonId>) =
            HeldEvent(EventRef("c1", "s1", "e$date"), "Visit", people, EventTime.AllDay(date), EventTime.AllDay(date.plusDays(1)))
        held = Held(
            listOf(
                allDay(at(9, 25), listOf(SampleUi.alex.id, SampleUi.sam.id)),
                allDay(at(9, 25), listOf(SampleUi.mia.id, PersonId.FAMILY)),
                allDay(at(9, 26), listOf(SampleUi.mia.id)),
                allDay(at(9, 29), listOf(SampleUi.alex.id)),
            ),
            lastDay = at(9, 28),
            unhealthy = emptySet(),
            firstDay = today,
        )
        show()
        val dots = compose.onAllNodesWithTag("day_${at(9, 25)}_dot", useUnmergedTree = true)
        dots.assertCountEquals(3)
        val colours = (0 until 3).map { i ->
            val dot = dots[i]
            val b = dot.getUnclippedBoundsInRoot()
            assertThat((b.right - b.left).value).isWithin(0.5f).of(5f)
            assertThat((b.bottom - b.top).value).isWithin(0.5f).of(5f)
            pixelAt(dot, 2.5.dp, 2.5.dp)
        }
        assertThat(colours).containsExactly(
            Color(SampleUi.alex.color).toArgb(),
            Color(SampleUi.sam.color).toArgb(),
            Color(SampleUi.mia.color).toArgb(),
        ).inOrder()
        compose.onAllNodesWithTag("day_${at(9, 26)}_dot", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("day_${at(9, 29)}_dot", useUnmergedTree = true).assertCountEquals(0)
        // TalkBack names everyone on the day, the fourth too.
        cell(at(9, 25)).assertContentDescriptionEquals("Friday 25 September, Alex, Sam, Mia and Family")
        cell(at(9, 26)).assertContentDescriptionEquals("Saturday 26 September, Mia")
        cell(at(9, 29)).assertContentDescriptionEquals("Tuesday 29 September")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theMonthPagesBothWays() {
        show()
        compose.onNodeWithTag("month_next").assertContentDescriptionEquals("Later months").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("October 2026")
        cell(at(10, 31)).assertExists()
        compose.onNodeWithTag("month_prev").assertContentDescriptionEquals("Earlier months").performClick()
        compose.onNodeWithTag("month_prev").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("August 2026")
        cell(at(8, 3)).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theSwitchTurnsOnRangesAndTheQuestionSaysDays() {
        show(editorForm().apply { chooseHour(10) })
        part("editor_row_time_value").assertTextEquals("10:00–11:00")
        part("editor_question").assertTextEquals("Which day?")
        compose.onNodeWithTag("day_multi").assertTextEquals("Lasts more than a day")
        compose.onNodeWithTag("day_multi").performClick()
        compose.runOnIdle { assertThat(form.multiDay).isTrue() }
        part("editor_question").assertTextEquals("Which days?")
        compose.onNodeWithTag("day_multi").assertTextEquals("Lasts more than a day", "Tap the first day, then the last")
        part("editor_row_time_value").assertTextEquals("All day")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aRangeCrossesIntoTheNextMonth() {
        show(multiDay())
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("October 2026")
        compose.mainClock.autoAdvance = false
        cell(at(10, 29)).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS + 50, ignoreFrameDuration = true)
        assertWithMessage("the first tap waits for the last").that(flow.active).isEqualTo(EditorStep.Day)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("November 2026")
        compose.mainClock.autoAdvance = false
        cell(at(11, 1)).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS - 16, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Day)
        compose.mainClock.advanceTimeBy(32, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Time)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertThat(form.day).isEqualTo(at(10, 29))
            assertThat(form.until).isEqualTo(at(11, 1))
            assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        }
        part("editor_row_day_value").assertTextEquals("Thu 29 Oct – Sun 1 Nov")
        part("editor_row_time_value").assertTextEquals("All day")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theBandIsRoundedAtItsEndsAndWhereItWraps() {
        val soft = c.accentSoft.toArgb()
        val surf = c.surf.toArgb()
        show(multiDay(at(10, 29), at(11, 1)))
        compose.onNodeWithTag("month_title").assertTextEquals("October 2026")
        assertWithMessage("the first day").that(circleFill(at(10, 29))).isEqualTo(c.accent.toArgb())
        assertWithMessage("the trailing 1 Nov, the last day").that(circleFill(at(11, 1))).isEqualTo(c.accent.toArgb())
        listOf(at(10, 30), at(10, 31)).forEach { d ->
            assertWithMessage("$d at its left").that(bandPixel(d, 1f)).isEqualTo(soft)
            assertWithMessage("$d at its right").that(bandPixel(d, -1f)).isEqualTo(soft)
            assertWithMessage("$d's top right corner").that(bandPixel(d, -1f, 2f)).isEqualTo(soft)
            assertThat(fadeOf(d)).isEqualTo(1f)
        }
        assertWithMessage("the band reaches into the first day").that(bandPixel(at(10, 29), -1f)).isEqualTo(soft)
        assertWithMessage("but not before it").that(bandPixel(at(10, 29), 1f)).isEqualTo(surf)
        assertWithMessage("the band reaches into the last day").that(bandPixel(at(11, 1), 1f)).isEqualTo(soft)
        assertWithMessage("but not after it").that(bandPixel(at(11, 1), -1f)).isEqualTo(surf)
        assertThat(fadeOf(at(11, 1))).isEqualTo(1f)
        assertWithMessage("a day after the range").that(bandPixel(at(10, 28), -1f)).isEqualTo(surf)
        listOf(at(10, 29) to "first day", at(10, 30) to "in the range", at(11, 1) to "last day").forEach { (d, state) ->
            assertThat(cell(d).fetchSemanticsNode().config[SemanticsProperties.StateDescription]).isEqualTo(state)
        }

        show(multiDay(at(10, 26), at(11, 3)))
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("November 2026")
        val sun = at(11, 1)
        assertWithMessage("1 Nov's right, mid-band").that(bandPixel(sun, -1f)).isEqualTo(soft)
        assertWithMessage("1 Nov's top right is rounded").that(bandPixel(sun, -1f, 2f)).isEqualTo(surf)
        assertWithMessage("1 Nov's top left is not").that(bandPixel(sun, 1f, 2f)).isEqualTo(soft)
        val mon = at(11, 2)
        assertWithMessage("2 Nov's left, mid-band").that(bandPixel(mon, 1f)).isEqualTo(soft)
        assertWithMessage("2 Nov's top left is rounded").that(bandPixel(mon, 1f, 2f)).isEqualTo(surf)
        assertWithMessage("2 Nov's top right is not").that(bandPixel(mon, -1f, 2f)).isEqualTo(soft)
        assertWithMessage("26 Oct, outside November but in the range").that(fadeOf(at(10, 26))).isEqualTo(1f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun pastSixtyDaysTheLineSays() {
        show(multiDay())
        cell(today).performClick()
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("month_next").performClick()
        cell(today.plusDays(59)).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS + 16)
        compose.runOnIdle { assertThat(flow.active).isEqualTo(EditorStep.Time) }
        part("day_error").assertDoesNotExist()

        compose.runOnIdle { flow.open(EditorStep.Day) }
        cell(today).performClick()
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("month_next").performClick()
        cell(today.plusDays(60)).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS + 16)
        compose.runOnIdle {
            assertThat(form.until).isEqualTo(today.plusDays(60))
            assertWithMessage("it stays for an earlier last day").that(flow.active).isEqualTo(EditorStep.Day)
        }
        compose.onNodeWithTag("day_error").assertTextEquals(UNTIL_TOO_LATE)
        compose.onNodeWithTag("editor_save_reason").assertTextEquals(UNTIL_TOO_LATE)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The cap's message asks for an earlier last day, so the next tap is that day, not a new range. */
    @Test
    fun aTapAfterTheCapPicksAnEarlierLastDay() {
        show(multiDay(today, today.plusDays(60)))
        compose.onNodeWithTag("day_error").assertTextEquals(UNTIL_TOO_LATE)
        compose.onNodeWithTag("month_next").performClick()
        compose.onNodeWithTag("month_title").assertTextEquals("October 2026")
        compose.mainClock.autoAdvance = false
        cell(today.plusDays(30)).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS - 16, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Day)
        part("day_error").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(32, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Time)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertThat(form.day).isEqualTo(today)
            assertThat(form.until).isEqualTo(today.plusDays(30))
        }
        part("editor_row_day_value").assertTextEquals("Wed 23 Sep – Fri 23 Oct")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theDayPaneFitsWithoutScrollingForASixWeekMonth() {
        show(editorForm(preselectedDay = at(8, 20)))
        compose.onNodeWithTag("month_title").assertTextEquals("August 2026")
        cell(at(9, 6)).assertExists()
        val range = compose.onNodeWithTag("editor_pane").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertThat(range.maxValue()).isEqualTo(0f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun atFontScale13TheLastWeekCanStillBeReached() {
        scale = 1.3f
        show(editorForm(preselectedDay = at(8, 20)))
        val pane = part("editor_pane").getUnclippedBoundsInRoot()
        cell(at(9, 6)).performScrollTo().performClick()
        compose.runOnIdle { assertThat(form.day).isEqualTo(at(9, 6)) }
        compose.runOnIdle { flow.open(EditorStep.Day) }
        compose.onNodeWithTag("day_multi").performScrollTo()
        val multi = part("day_multi").getUnclippedBoundsInRoot()
        assertWithMessage("the switch's top").that(multi.top).isAtLeast(pane.top)
        assertWithMessage("the switch's foot").that(multi.bottom).isAtMost(pane.bottom)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
