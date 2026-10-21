package uk.co.siland.culvery.capability.calendar.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Handoff 6g in the editor on the 1280 × 800 dp canvas, at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CheckPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private var scale by mutableStateOf(1f)
    private var form by mutableStateOf(guitar())
    private var shown = false

    /** Guitar lesson for Mia, weekly from Sat 26 Sep, 10:00–10:45, 12 times. */
    private fun guitar() = editorForm().apply {
        updateTitle("Guitar lesson")
        toggleWho(SampleUi.mia.id)
        tapDay(LocalDate.of(2026, 9, 26))
        chooseHour(10)
        chooseLength(Duration.ofMinutes(45))
        chooseRepeat(RepeatChoice.Weekly)
        stopsAfter()
        repeat(2) { moreTimes() }
    }

    /** Cinema for Sam and Mia, Fri 25 Sep, 17:00–18:00, once. */
    private fun cinema() = editorForm().apply {
        updateTitle("Cinema")
        toggleWho(SampleUi.sam.id)
        toggleWho(SampleUi.mia.id)
        tapDay(LocalDate.of(2026, 9, 25))
        chooseHour(17)
    }

    private fun show(editor: EditorForm = guitar()) {
        form = editor
        if (shown) {
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) {
                    key(form) {
                        EventEditorScreen(
                            form = form,
                            flow = editorFlow(form, null, EditorStep.Check),
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

    private fun width(tag: String) = node(tag).getUnclippedBoundsInRoot().let { (it.right - it.left).value }

    private fun height(tag: String) = node(tag).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    /** Within a step of [want] in each channel: a translucent fill rounds when it is drawn. */
    private fun assertNear(what: String, got: Int, want: Color) {
        val w = want.toArgb()
        val near = listOf(AndroidColor::red, AndroidColor::green, AndroidColor::blue).all { abs(it(got) - it(w)) <= 1 }
        assertWithMessage("%s: %s against %s", what, Integer.toHexString(got), Integer.toHexString(w)).that(near).isTrue()
    }

    /** The colours of the dots inside [tag], left to right. */
    private fun dotColours(tag: String, dotTag: String): List<Int> {
        val dots = compose.onAllNodes(hasTestTag(dotTag) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        return dots.fetchSemanticsNodes().indices.map { i ->
            val dot = dots[i]
            val b = dot.getUnclippedBoundsInRoot()
            pixelAt(dot, ((b.right - b.left).value / 2).dp, ((b.bottom - b.top).value / 2).dp)
        }
    }

    @Test
    fun theSentenceAndTheFirstFew() {
        show()
        node("check_sentence").assertTextEquals("Guitar lesson for Mia, every Saturday, 10:00–10:45, from 26 Sep to 12 Dec. 12 times in all.")
        listOf("Sat 26 Sep", "Sat 3 Oct", "Sat 10 Oct", "Sat 17 Oct").forEachIndexed { i, date ->
            val tag = "check_date_$i"
            node(tag).assertTextEquals(date)
            assertWithMessage("$tag's height").that(height(tag)).isWithin(0.01f).of(40f)
            assertWithMessage("$tag's dot").that(dotColours(tag, "check_date_dot")).containsExactly(Color(SampleUi.mia.color).toArgb())
        }
        node("check_date_4").assertDoesNotExist()
        node("check_more").assertTextEquals("+ 8 more")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theWeekChipPreview() {
        show()
        compose.onNode(hasText("10:00 · Mia") and hasAnyAncestor(hasTestTag("check_chip")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Guitar lesson") and hasAnyAncestor(hasTestTag("check_chip")), useUnmergedTree = true).assertExists()
        val tint = Color(SampleUi.mia.color).copy(alpha = HhAlpha.tint(c)).compositeOver(c.surf)
        assertNear("Mia's tint", pixelAt(node("check_chip"), (width("check_chip") - 5).dp, (height("check_chip") / 2).dp), tint)
        compose.onAllNodes(hasTestTag("event_bar") and hasAnyAncestor(hasTestTag("check_chip")), useUnmergedTree = true).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noButtonInThePane() {
        show()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("editor_pane"))).assertCountEquals(0)
        node("editor_next").assertDoesNotExist()
        node("editor_skip").assertDoesNotExist()
        node("editor_save_hint").assertTextEquals("Everything’s set")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSharedOneOffReadsBoth() {
        show(cinema())
        node("check_sentence").assertTextEquals("Cinema for Sam and Mia, Fri 25 Sep, 17:00–18:00.")
        node("check_date_0").assertTextEquals("Fri 25 Sep")
        node("check_date_1").assertDoesNotExist()
        node("check_more").assertDoesNotExist()
        val both = listOf(SampleUi.sam, SampleUi.mia).map { Color(it.color).toArgb() }
        assertThat(dotColours("check_date_0", "check_date_dot")).containsExactlyElementsIn(both).inOrder()
        compose.onNode(hasText("17:00 · Sam + Mia") and hasAnyAncestor(hasTestTag("check_chip")), useUnmergedTree = true).assertExists()
        assertNear("the neutral chip", pixelAt(node("check_chip"), (width("check_chip") - 5).dp, (height("check_chip") / 2).dp), c.surf2)
        compose.onAllNodes(hasTestTag("event_bar") and hasAnyAncestor(hasTestTag("check_chip")), useUnmergedTree = true).assertCountEquals(1)
        assertThat(dotColours("check_chip", "chip_dot")).containsExactlyElementsIn(both).inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun untilItCanBeAddedItSaysOnlyWhatItNeeds() {
        show(editorForm().apply { updateTitle("Cinema") })
        node("check_needs").assertTextEquals("Needs who and a day")
        node("check_sentence").assertDoesNotExist()
        node("check_chip").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** At 1.3 and 1.5 each date chip reads on one line and the row stays inside the pane. */
    @Test
    fun theDatesFitAtLargerFontScales() {
        listOf(1.3f, 1.5f).forEach { s ->
            scale = s
            show(guitar())
            val pane = node("editor_pane").getUnclippedBoundsInRoot()
            listOf("Sat 26 Sep", "Sat 3 Oct", "Sat 10 Oct", "Sat 17 Oct").forEachIndexed { i, date ->
                val tag = "check_date_$i"
                val layout = compose.onNode(hasText(date) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).textLayout()
                assertWithMessage("$tag at $s").that(layout.lineCount).isEqualTo(1)
                assertWithMessage("$tag's words fit at $s").that(layout.getLineRight(0) - layout.getLineLeft(0)).isAtMost(layout.size.width.toFloat())
                assertWithMessage("$tag inside the pane at $s").that(node(tag).getUnclippedBoundsInRoot().right).isAtMost(pane.right)
            }
            assertWithMessage("+ 8 more inside the pane at $s").that(node("check_more").getUnclippedBoundsInRoot().right).isAtMost(pane.right)
        }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
