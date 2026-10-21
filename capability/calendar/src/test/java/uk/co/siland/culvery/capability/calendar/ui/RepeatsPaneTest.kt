package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.DEFAULT_TIMES
import uk.co.siland.culvery.capability.calendar.EDITOR_REPEATS
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.MAX_TIMES
import uk.co.siland.culvery.capability.calendar.MIN_TIMES
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.RepeatEnd
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Handoff 6f in the editor on the 1280 × 800 dp canvas, at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepeatsPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private val saturday = LocalDate.of(2026, 9, 26)
    private var form by mutableStateOf(guitar())
    private var flow by mutableStateOf(editorFlow(form, null, EditorStep.Repeats))
    private var scale by mutableStateOf(1f)
    private var shown = false

    /** "Guitar lesson" for Mia on [day] (none when null), with [setUp] applied. */
    private fun guitar(day: LocalDate? = saturday, setUp: EditorForm.() -> Unit = {}) = editorForm().apply {
        updateTitle("Guitar lesson")
        toggleWho(SampleUi.mia.id)
        day?.let(::tapDay)
        setUp()
    }

    /** Puts [editor] on screen opened on Repeats, in place of the one showing. */
    private fun show(editor: EditorForm = guitar()) {
        form = editor
        flow = editorFlow(editor, null, EditorStep.Repeats)
        if (shown) {
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) {
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
                            picker = EditorPicker.None,
                            childOnly = null,
                            childName = null,
                            held = null,
                            onClose = {},
                            onSave = {},
                            onDelete = {},
                            onRefusedWho = {},
                            onPicker = {},
                            keyboard = WindowInsets(bottom = 0.dp),
                        )
                    }
                }
            }
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun height(tag: String) = node(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    private fun width(tag: String) = node(tag).getUnclippedBoundsInRoot().let { (it.right - it.left).value }

    private fun top(tag: String) = node(tag).getUnclippedBoundsInRoot().top

    private fun repeating(setUp: EditorForm.() -> Unit = {}) = guitar {
        chooseRepeat(RepeatChoice.Weekly)
        setUp()
    }

    @Test
    fun theOptionsAreWordedFromTheDay() {
        show(repeating())
        val words = listOf(
            "Doesn’t repeat",
            "Every Saturday",
            "Every 2 weeks, on Saturday",
            "Every month, on the 4th Saturday",
            "Every year, on 26 September",
            "Every day",
        )
        val tags = EDITOR_REPEATS.map { "repeat_${it.name}" }
        tags.zip(words).forEach { (tag, word) ->
            node(tag).assertTextEquals(word)
            assertWithMessage("$tag's height").that(height(tag)).isWithin(0.01f).of(56f)
        }
        assertWithMessage("in EDITOR_REPEATS order, one under another").that(tags.map(::top)).isInStrictOrder()
        node("repeat_Weekly").assertIsSelected()
        tags.filter { it != "repeat_Weekly" }.forEach { node(it).assertIsNotSelected() }
        val selected = node("repeat_Weekly")
        assertWithMessage("accentSoft fill").that(pixelAt(selected, (width("repeat_Weekly") - 12).dp, 28.dp)).isEqualTo(c.accentSoft.toArgb())
        assertWithMessage("2 dp accent ring").that(pixelAt(selected, (width("repeat_Weekly") / 2).dp, 1.dp)).isEqualTo(c.accent.toArgb())
        assertWithMessage("inside the ring").that(pixelAt(selected, (width("repeat_Weekly") / 2).dp, 3.dp)).isEqualTo(c.accentSoft.toArgb())
        val other = node("repeat_Fortnightly")
        assertWithMessage("an unselected row has no ring").that(pixelAt(other, (width("repeat_Fortnightly") / 2).dp, 1.dp)).isEqualTo(c.surf2.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun doesntRepeatMovesOnAndARepeatDoesNot() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        node("repeat_Weekly").performClick()
        compose.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Repeats)
        assertThat(form.repeat).isEqualTo(RepeatChoice.Weekly)
        node("repeat_Never").performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS - 16, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Repeats)
        compose.mainClock.advanceTimeBy(32, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.Check)
        compose.mainClock.autoAdvance = true
        assertThat(form.repeat).isEqualTo(RepeatChoice.Never)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun endsShowOnlyWhileItRepeats() {
        show()
        node("ends_mode").assertDoesNotExist()
        node("repeat_Weekly").performClick()
        node("ends_mode_0").assertTextEquals("Keeps going").assertIsSelected()
        node("ends_mode_1").assertTextEquals("Stops after").assertIsNotSelected()
        node("ends_count").assertDoesNotExist()
        node("repeat_Never").performClick()
        node("ends_mode").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun stopsAfterShowsOneStepper() {
        show(repeating())
        node("ends_mode_1").performClick()
        node("ends_mode_1").assertIsSelected()
        node("ends_count").assertTextEquals("10 times · last on Sat 28 Nov")
        node("ends_fewer").assert(hasContentDescription("Fewer times"))
        node("ends_more").assert(hasContentDescription("More times"))
        val fewer = node("ends_fewer").getUnclippedBoundsInRoot()
        val count = node("ends_count").getUnclippedBoundsInRoot()
        val more = node("ends_more").getUnclippedBoundsInRoot()
        assertWithMessage("fewer, count, more on one line").that(listOf(fewer.right, count.left, count.right, more.left)).isInOrder()
        assertWithMessage("a 48 dp button").that((fewer.bottom - fewer.top).value).isWithin(0.01f).of(48f)
        node("ends_more").performClick()
        node("ends_more").performClick()
        node("ends_count").assertTextEquals("12 times · last on Sat 12 Dec")
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.After(12))
        node("ends_mode_0").performClick()
        node("ends_count").assertDoesNotExist()
        assertThat(form.repeatEnd).isEqualTo(RepeatEnd.Never)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theStepperStopsAtItsEnds() {
        show(repeating { stopsAfter(); repeat(DEFAULT_TIMES - MIN_TIMES) { fewerTimes() } })
        node("ends_fewer").assertIsNotEnabled()
        node("ends_more").assertIsEnabled()
        compose.runOnIdle { repeat(MAX_TIMES - MIN_TIMES) { form.moreTimes() } }
        node("ends_more").assertIsNotEnabled()
        node("ends_fewer").assertIsEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The pane on its own, out of the editor's scrolling pane, so a press lands at once: steps at 500, 600, 700 and 800 ms. */
    @Test
    fun holdingMoreStepsTheCount() {
        val f = repeating { stopsAfter() }
        compose.setContent {
            CulveryTheme(dark = true) { RepeatsPane(f, "Google Calendar", onPicked = {}, onChooseDay = {}) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        node("ends_more").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(484)
        assertThat(f.repeatEnd).isEqualTo(RepeatEnd.After(10))
        compose.mainClock.advanceTimeBy(300)
        assertThat(f.repeatEnd).isEqualTo(RepeatEnd.After(13))
        compose.mainClock.advanceTimeBy(32)
        assertThat(f.repeatEnd).isEqualTo(RepeatEnd.After(14))
        node("ends_more").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        assertThat(f.repeatEnd).isEqualTo(RepeatEnd.After(14))
        compose.mainClock.autoAdvance = true
        node("ends_count").assertTextEquals("14 times · last on Sat 26 Dec")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theHintIsUnderneath() {
        show(repeating { stopsAfter() })
        node("repeats_hint").assertTextEquals("To skip one date, change it in Google Calendar on your phone.")
        assertThat(top("repeats_hint")).isGreaterThan(top("ends_count"))
        show(guitar())
        node("repeats_hint").assertTextEquals("To skip one date, change it in Google Calendar on your phone.")
        assertThat(top("repeats_hint")).isGreaterThan(top("repeat_Daily"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun withNoDayItAsksForOne() {
        show(guitar(day = null))
        node("repeats_need_day").assertTextEquals("Choose a day first.")
        node("repeat_Weekly").assertDoesNotExist()
        node("repeats_choose_day").assertTextEquals("Choose a day").performClick()
        compose.runOnIdle { assertThat(flow.active).isEqualTo(EditorStep.Day) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theRowReadsTheChoice() {
        show()
        part("editor_row_repeats_value").assertTextEquals("Doesn’t repeat")
        node("repeat_Weekly").performClick()
        part("editor_row_repeats_value").assertTextEquals("Every Saturday")
        node("ends_mode_1").performClick()
        node("ends_more").performClick()
        node("ends_more").performClick()
        part("editor_row_repeats_value").assertTextEquals("Every Saturday · 12 times")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** At 1.3 and 1.5 every option and the stepper's count read on one line, inside the pane. */
    @Test
    fun theRowsFitAtLargerFontScales() {
        listOf(1.3f, 1.5f).forEach { s ->
            scale = s
            show(repeating { stopsAfter(); repeat(2) { moreTimes() } })
            val pane = node("editor_pane").getUnclippedBoundsInRoot()
            val texts = EDITOR_REPEATS.map { "repeat_${it.name}" to node("repeat_${it.name}").fetchSemanticsNode().config[SemanticsProperties.Text].single().text } +
                ("ends_count" to "12 times · last on Sat 12 Dec")
            texts.forEach { (tag, text) ->
                val layout = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).textLayout()
                assertWithMessage("$tag at $s").that(layout.lineCount).isEqualTo(1)
                assertWithMessage("$tag's words fit at $s").that(layout.getLineRight(0) - layout.getLineLeft(0)).isAtMost(layout.size.width.toFloat())
                assertWithMessage("$tag inside the pane at $s").that(node(tag).getUnclippedBoundsInRoot().right).isAtMost(pane.right)
            }
        }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
