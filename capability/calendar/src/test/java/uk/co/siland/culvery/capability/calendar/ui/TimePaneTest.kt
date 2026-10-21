package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.END_BEFORE_START
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.HeldEvent
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.check
import uk.co.siland.culvery.capability.calendar.lastShownDay
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.core.ui.test.windowBitmap

/** Handoff 6e and 6i in the editor on the 1280 × 800 dp canvas, at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimePaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private val saturday = LocalDate.of(2026, 9, 26)
    private val lastShown = lastShownDay(SampleUi.TODAY)
    private var form by mutableStateOf(guitar())
    private var flow by mutableStateOf(editorFlow(form, null, EditorStep.Time))
    private var held by mutableStateOf<Held?>(null)
    private var picker by mutableStateOf(EditorPicker.None)
    private val pickers = mutableListOf<EditorPicker>()
    private var shown = false

    /** "Guitar lesson" for Mia (or [who]) on [day], with [setUp] applied. */
    private fun guitar(day: LocalDate = saturday, who: PersonId = SampleUi.mia.id, setUp: EditorForm.() -> Unit = {}) = editorForm().apply {
        updateTitle("Guitar lesson")
        toggleWho(who)
        tapDay(day)
        setUp()
    }

    /** Mia's Piano, 15:30–16:15, on each of [days]. */
    private fun piano(vararg days: LocalDate, unhealthy: Set<PersonId> = emptySet()) = Held(
        days.map { d ->
            HeldEvent(
                EventRef("c1", "s1", "piano$d"), "Piano", listOf(SampleUi.mia.id),
                EventTime.Timed(d.atTime(15, 30).atZone(LONDON).toInstant()), EventTime.Timed(d.atTime(16, 15).atZone(LONDON).toInstant()),
            )
        },
        lastDay = lastShown,
        unhealthy = unhealthy,
        firstDay = SampleUi.TODAY,
    )

    /** Puts [editor] on screen opened on Time, in place of the one showing. */
    private fun show(editor: EditorForm = guitar()) {
        form = editor
        flow = editorFlow(editor, null, EditorStep.Time)
        if (shown) {
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            CulveryTheme(dark = true) {
                key(form, flow) {
                    EventEditorScreen(
                        form = form,
                        flow = flow,
                        members = SampleUi.members,
                        context = "From Calendar · Alex signed in",
                        savesTo = "Family calendar",
                        service = "Google Calendar",
                        busy = false,
                        failure = null,
                        picker = picker,
                        childOnly = null,
                        childName = null,
                        held = held,
                        onClose = {},
                        onSave = {},
                        onDelete = {},
                        onRefusedWho = {},
                        onPicker = {
                            pickers += it
                            picker = it
                        },
                        keyboard = WindowInsets(bottom = 0.dp),
                    )
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun texts(tag: String): List<String> = node(tag).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    private fun top(tag: String) = node(tag).getUnclippedBoundsInRoot().top

    private fun left(tag: String) = node(tag).getUnclippedBoundsInRoot().left

    /** Whether any pixel inside [node]'s bounds is exactly [colour]: an icon drawn in that tint. */
    private fun anyPixel(node: SemanticsNodeInteraction, colour: Color): Boolean {
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val bitmap = windowBitmap(node)
        val want = colour.toArgb()
        return (bounds.left.toInt() until bounds.right.toInt()).any { x ->
            (bounds.top.toInt() until bounds.bottom.toInt()).any { y -> bitmap.getPixel(x, y) == want }
        }
    }

    /** A pixel on a chip's top edge, half a dp in, at its middle: the busy edge when there is one. */
    private fun topEdge(tag: String): Int {
        val b = node(tag).getUnclippedBoundsInRoot()
        return pixelAt(node(tag), ((b.right - b.left).value / 2).dp, 0.5.dp)
    }

    /** The Time row's tag, drawn inside the row and heard with it: "Clash". */
    private fun clashTagShows() {
        compose.onNode(hasText("Clash") and hasAnyAncestor(hasTestTag("editor_row_time_tag")), useUnmergedTree = true).assertExists()
        assertThat(texts("editor_row_time")).contains("Clash")
    }

    private fun movesOnAfter(action: () -> Unit, to: EditorStep) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        action()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS - 16, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Time)
        compose.mainClock.advanceTimeBy(32, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(to)
        compose.mainClock.autoAdvance = true
    }

    private fun staysAfter(action: () -> Unit) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        action()
        compose.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Time)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun allDayIsTheDefaultAndMovesOn() {
        show()
        node("time_mode_0").assertTextEquals("All day").assertIsSelected()
        node("time_mode_1").assertTextEquals("At a time").assertIsNotSelected()
        part("editor_row_time_value").assertTextEquals("All day")
        node("hour_10").assertDoesNotExist()
        movesOnAfter({ node("time_mode_0").performClick() }, to = EditorStep.Repeats)
        assertThat(form.time).isEqualTo(TimeChoice.AllDay)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun atATimeShowsTheHoursInThreeGroups() {
        show()
        staysAfter { node("time_mode_1").performClick() }
        node("time_mode_1").assertIsSelected()
        listOf("Morning", "Afternoon", "Evening").forEachIndexed { i, name -> part("hour_group_$i").assertTextEquals(name) }
        node("hour_06").assertDoesNotExist()
        node("hour_23").assertDoesNotExist()
        val rows = listOf((7..11).map { "%02d".format(it) }, (12..17).map { "%02d".format(it) }, (18..22).map { "%02d".format(it) } + "other")
        rows.forEachIndexed { i, row ->
            val tags = row.map { "hour_$it" }
            assertWithMessage("row $i in one line").that(tags.map(::top).distinct()).hasSize(1)
            assertWithMessage("row $i left to right").that(tags.map(::left)).isInStrictOrder()
            if (i > 0) assertWithMessage("row $i under row ${i - 1}").that(top(tags[0])).isGreaterThan(top("hour_${rows[i - 1][0]}"))
        }
        assertWithMessage("six a row: an hour lines up with the one above it").that(left("hour_18")).isEqualTo(left("hour_12"))
        assertThat(left("hour_other")).isEqualTo(left("hour_17"))
        node("hour_07").assertTextEquals("07:00")
        node("hour_other").assertTextEquals("Other time")
        val digits = compose.onNode(hasText("10:00"), useUnmergedTree = true).textLayout().layoutInput.style
        assertWithMessage("tabular figures").that(digits.fontFeatureSettings).isEqualTo("tnum")
        part("editor_row_time_value").assertTextEquals("All day")
        node("hour_10").performClick()
        part("editor_row_time_value").assertTextEquals("10:00–11:00")
        node("hour_10").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anHourThenMinutesThenLength() {
        show()
        node("time_mode_1").performClick()
        staysAfter { node("hour_10").performClick() }
        listOf("00", "15", "30", "45").forEach { m -> node("minute_$m").assertTextEquals(":$m") }
        staysAfter { node("minute_45").performClick() }
        part("editor_row_time_value").assertTextEquals("10:45–11:45")
        listOf(30 to "30 min", 45 to "45 min", 60 to "1 hour", 120 to "2 hours").forEach { (m, label) -> node("length_$m").assertTextEquals(label) }
        node("length_45").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("45 minutes")))
        movesOnAfter({ node("length_45").performClick() }, to = EditorStep.Repeats)
        part("editor_row_time_value").assertTextEquals("10:45–11:30")
        assertThat(form.startTime).isEqualTo(LocalTime.of(10, 45))
        assertThat(form.length).isEqualTo(Duration.ofMinutes(45))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun otherTimeAndUntilOpenThePicker() {
        show()
        node("time_mode_1").performClick()
        node("hour_other").performClick()
        compose.runOnIdle { assertThat(pickers).containsExactly(EditorPicker.StartTime) }
        node("time_picker").assertExists()
        node("picker_cancel").performClick()
        compose.runOnIdle { assertThat(picker).isEqualTo(EditorPicker.None) }
        node("hour_10").performClick()
        node("length_until").assertTextEquals("Until…").assertIsNotSelected().performClick()
        compose.runOnIdle { assertThat(pickers).containsExactly(EditorPicker.StartTime, EditorPicker.None, EditorPicker.EndTime).inOrder() }
        node("time_picker").assertExists()
        node("picker_cancel").performClick()
        compose.runOnIdle { form.chooseEndTime(LocalTime.of(11, 20)) }
        node("length_until").assertTextEquals("Until 11:20").assertIsSelected()
        node("length_60").assertIsNotSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEndBeforeTheStartSaysSo() {
        show()
        node("time_mode_1").performClick()
        node("hour_10").performClick()
        node("time_end_error").assertDoesNotExist()
        node("length_until").performClick()
        node("hour_value").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hour 11")))
        node("hour_down").performClick()
        node("hour_down").performClick()
        node("picker_set").performClick()
        compose.runOnIdle {
            assertThat(picker).isEqualTo(EditorPicker.None)
            assertThat(form.length).isEqualTo(Duration.ofHours(1))
        }
        node("time_end_error").assertTextEquals(END_BEFORE_START)
        node("editor_save").assertIsEnabled()
        node("length_30").performClick()
        node("time_end_error").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun busyHoursHaveAnAmberEdgeAndDot() {
        held = piano(saturday)
        show()
        node("time_mode_1").performClick()
        listOf("hour_15", "hour_16").forEach { tag ->
            assertWithMessage("$tag's edge").that(topEdge(tag)).isEqualTo(c.weather.toArgb())
            val dot = part("${tag}_dot").getUnclippedBoundsInRoot()
            assertThat((dot.right - dot.left).value).isWithin(0.5f).of(7f)
            assertThat((dot.bottom - dot.top).value).isWithin(0.5f).of(7f)
            assertWithMessage("$tag's dot").that(pixelAt(part("${tag}_dot"), 3.5.dp, 3.5.dp)).isEqualTo(c.weather.toArgb())
            node(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("${tag.removePrefix("hour_")}:00, busy")))
        }
        assertWithMessage("hour_14's edge").that(topEdge("hour_14")).isEqualTo(c.surf2.toArgb())
        part("hour_14_dot").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theResultSaysFree() {
        held = piano(saturday)
        show()
        node("time_mode_1").performClick()
        node("time_result").assertDoesNotExist()
        node("hour_10").performClick()
        assertThat(texts("time_result")).containsExactly("10:00–11:00", "Mia is free then").inOrder()
        val line = compose.onNode(hasText("Mia is free then"), useUnmergedTree = true).textLayout().layoutInput.style
        assertThat(line.color).isEqualTo(c.accent)
        assertThat(anyPixel(part("time_result_icon"), c.accent)).isTrue()
        assertThat(checkIcon(FreeCheck.Free(listOf(SampleUi.mia.id)))).isEqualTo(Icons.CHECK_CIRCLE)
        node("time_free").assertDoesNotExist()
        part("editor_row_time_tag").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aClashWarnsAndNamesTheFreeStarts() {
        held = piano(saturday)
        show(clashAt3(saturday))
        assertThat(texts("time_result")).containsExactly("15:00–15:45", "Overlaps Piano, 15:30–16:15").inOrder()
        val line = compose.onNode(hasText("Overlaps Piano, 15:30–16:15"), useUnmergedTree = true).textLayout().layoutInput.style
        assertThat(line.color).isEqualTo(c.weather)
        assertThat(anyPixel(part("time_result_icon"), c.weather)).isTrue()
        val busy = compose.runOnIdle { held!!.check(form.who, saturday, LocalTime.of(15, 0), Duration.ofMinutes(45), LONDON, null, form.who) }
        assertThat(checkIcon(busy)).isEqualTo(Icons.WARNING)
        part("time_free_at").assertTextEquals("Mia is free at")
        node("time_free_1445").assertTextEquals("14:45")
        node("time_free_1615").assertTextEquals("16:15")
        part("time_free_tail").assertTextEquals("or keep it; clashes are allowed.")
        assertThat(listOf("time_free_at", "time_free_1445", "time_free_1615").map { part(it).getUnclippedBoundsInRoot().left }).isInStrictOrder()
        clashTagShows()
        node("editor_save").assertIsEnabled()
        node("editor_save_hint").assertTextEquals("Clashes are allowed; both show on the week")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aFreeStartChipMovesTheTime() {
        held = piano(saturday)
        show(clashAt3(saturday))
        staysAfter { node("time_free_1615").performClick() }
        compose.runOnIdle { assertThat(form.startTime).isEqualTo(LocalTime.of(16, 15)) }
        part("editor_row_time_value").assertTextEquals("16:15–17:00")
        assertThat(texts("time_result")).containsExactly("16:15–17:00", "Mia is free then").inOrder()
        node("time_free").assertDoesNotExist()
        part("editor_row_time_tag").assertDoesNotExist()
        node("editor_save_hint").assertTextEquals("Time and repeats are optional")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun pastTheHeldWindowThereIsNoResultLineAndNoTag() {
        held = piano(lastShown, lastShown.plusDays(1))
        show(clashAt3(lastShown))
        assertThat(texts("time_result")).containsExactly("15:00–15:45", "Overlaps Piano, 15:30–16:15").inOrder()
        part("hour_15_dot").assertExists()
        clashTagShows()

        show(clashAt3(lastShown.plusDays(1)))
        assertThat(texts("time_result")).containsExactly("15:00–15:45")
        part("time_result_icon").assertDoesNotExist()
        part("hour_15_dot").assertDoesNotExist()
        node("time_free").assertDoesNotExist()
        part("editor_row_time_tag").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun everyoneHasNoCheck() {
        held = piano(saturday)
        show(clashAt3(saturday))
        clashTagShows()

        show(clashAt3(saturday, who = PersonId.FAMILY))
        assertThat(texts("time_result")).containsExactly("15:00–15:45")
        part("hour_15_dot").assertDoesNotExist()
        part("editor_row_time_tag").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aPersonWhoseCalendarNeedsSignInHasNoCheck() {
        held = piano(saturday)
        show(clashAt3(saturday))
        assertThat(texts("time_result")).containsExactly("15:00–15:45", "Overlaps Piano, 15:30–16:15").inOrder()

        held = piano(saturday, unhealthy = setOf(SampleUi.mia.id))
        show(clashAt3(saturday))
        assertThat(texts("time_result")).containsExactly("15:00–15:45")
        part("hour_15_dot").assertDoesNotExist()
        part("editor_row_time_tag").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun multiDayLocksAtATime() {
        show(
            guitar {
                setMultiDay(true)
                tapDay(saturday)
                tapDay(saturday.plusDays(2))
            },
        )
        node("time_all_day_only").assertDoesNotExist()
        part("time_mode_1_lock").assertExists()
        node("time_mode_1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available")).performClick()
        node("time_all_day_only").assertTextEquals("An event over several days is all day.")
        node("time_mode_0").assertIsSelected()
        node("hour_10").assertDoesNotExist()
        compose.runOnIdle { assertThat(form.time).isEqualTo(TimeChoice.AllDay) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun skipLeavesItAsItIs() {
        show(guitar { chooseHour(10) })
        node("editor_skip").performClick()
        compose.runOnIdle {
            assertThat(flow.active).isEqualTo(EditorStep.Repeats)
            assertThat(form.startTime).isEqualTo(LocalTime.of(10, 0))
            assertThat(form.length).isEqualTo(Duration.ofHours(1))
        }
        part("editor_row_time_value").assertTextEquals("10:00–11:00")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 15:00 for 45 minutes on [day]: over Mia's Piano. */
    private fun clashAt3(day: LocalDate, who: PersonId = SampleUi.mia.id) = guitar(day, who) {
        chooseHour(15)
        chooseLength(Duration.ofMinutes(45))
    }
}
