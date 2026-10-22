package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.Suggestion
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** The "From before" list in the editor's right-hand pane (9c §4.3), with a fake in place of the remembered titles. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FromBeforePaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)

    private val weekly = "RRULE:FREQ=WEEKLY;INTERVAL=1"

    private val guitar = Suggestion(
        "Guitar lesson", "mia", false, LocalTime.of(10, 0), Duration.ofMinutes(45), null, weekly, day(9, 5), day(9, 12),
    )
    private val guitarClub = Suggestion("Guitar club", "family", false, LocalTime.of(18, 0), Duration.ofMinutes(60), null, null, null, day(7, 11))
    private val guests = Suggestion("Guest night", "alex,sam", true, null, null, 3, null, null, day(8, 1))
    private val remembered = listOf(guitar, guitarClub, guests)

    private var scale by mutableStateOf(1f)
    private var asked = mutableListOf<String>()
    private var answer: suspend (String) -> List<Suggestion> = { typed -> remembered.filter { typed.length >= 2 && it.title.startsWith(typed, ignoreCase = true) } }

    private fun show(form: EditorForm = editorForm(), keyboard: Int = 0, start: EditorStep? = null) {
        val flow = editorFlow(form, null, start)
        compose.setContent {
            ShowEditor(
                form, flow, scale = scale, keyboard = WindowInsets(bottom = keyboard.dp),
                suggest = { typed ->
                    asked += typed
                    answer(typed)
                },
            )
        }
    }

    private fun type(text: String) = compose.onNodeWithTag("editor_title").performTextInput(text)

    private fun question() = compose.onNodeWithTag("editor_question")

    private fun row(i: Int) = compose.onNodeWithTag("suggestion_$i")

    @Test
    fun theListShowsWhileTheTitleIsFocused() {
        show()
        type("G")
        question().assertTextEquals("Who’s it for?")
        type("ui")
        question().assertTextEquals("From before")
        row(0).assertExists()
        row(1).assertExists()
        row(2).assertDoesNotExist()
        compose.onNodeWithTag("suggestion_0_fill", useUnmergedTree = true).assertTextEquals("Fills in Mia, 10:00–10:45 and every Saturday")
        compose.onNodeWithTag("suggestion_0_pattern", useUnmergedTree = true).assertTextEquals("Mia · Saturdays 10:00–10:45 · last on Sat 12 Sep")
        compose.onNodeWithTag("from_before_note")
            .assertTextEquals("A suggestion never copies the date. Or keep typing; Next on the keyboard moves to Who.")
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("editor_title").performImeAction()
        question().assertTextEquals("Who’s it for?")
        row(0).assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun withNoMatchesThePaneAsksTheActiveQuestion() {
        show()
        type("Zq")
        question().assertTextEquals("Who’s it for?")
        row(0).assertDoesNotExist()
        assertThat(asked).contains("Zq")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun changeNeverSuggests() {
        show(editorEditOf(EventTime.AllDay(day(9, 26)), EventTime.AllDay(day(9, 27)), "Bin day", "family"))
        compose.onNodeWithTag("editor_title").performClick()
        type("Gu")
        compose.onNodeWithTag("editor_title").assertIsFocused()
        question().assertTextEquals("Check it")
        row(0).assertDoesNotExist()
        assertThat(asked).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun eachRowIsOneItemWithUseThis() {
        show()
        type("Gui")
        compose.waitForIdle()
        val spoken = "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday"
        val node = row(0).fetchSemanticsNode()
        assertThat(node.config[SemanticsProperties.ContentDescription]).containsExactly(spoken)
        assertThat(node.config[SemanticsProperties.Role]).isEqualTo(Role.Button)
        assertThat(node.config[SemanticsActions.OnClick].label).isEqualTo("Use this")
        // The pill is drawn inside the row, not a second button.
        val clickable = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("editor_pane")), useUnmergedTree = true).fetchSemanticsNodes()
        assertThat(clickable.mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }).containsExactly("suggestion_0", "suggestion_1", "editor_next")
        compose.onNodeWithTag("suggestion_0_use", useUnmergedTree = true).assertTextEquals("Use this")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun threeRowsAtFontScale13ScrollAndNoneIsCutMidRow() {
        scale = 1.3f
        show(keyboard = 320)
        type("G")
        answer = { remembered }
        type("u")
        compose.waitForIdle()
        row(2).assertExists()
        val pane = compose.onNodeWithTag("editor_pane")
        assertThat(pane.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()).isGreaterThan(0f)
        repeat(3) { i ->
            row(i).performScrollTo()
            val bounds = row(i).getUnclippedBoundsInRoot()
            val paneBounds = pane.getUnclippedBoundsInRoot()
            assertWithMessage("row $i").that(bounds.top.value).isAtLeast(paneBounds.top.value)
            assertWithMessage("row $i").that(bounds.bottom.value).isAtMost(paneBounds.bottom.value)
            for (part in listOf("title", "pattern", "fill")) {
                val layout = compose.onNodeWithTag("suggestion_${i}_$part", useUnmergedTree = true).fetchSemanticsNode().let { n ->
                    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                    results.first()
                }
                assertWithMessage("row $i $part").that(layout.didOverflowHeight).isFalse()
            }
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSlowerEarlierAnswerNeverReplacesALaterOne() {
        val gate = CompletableDeferred<Unit>()
        val slow = guitar.copy(title = "Swan boat")
        val later = guitar.copy(title = "Swimming")
        answer = { typed ->
            when (typed) {
                "sw" -> {
                    gate.await()
                    listOf(slow)
                }
                "swim" -> listOf(later)
                else -> emptyList()
            }
        }
        show()
        type("sw")
        compose.waitForIdle()
        type("im")
        compose.waitForIdle()
        compose.onNodeWithTag("suggestion_0_title", useUnmergedTree = true).assertTextEquals("Swimming")
        gate.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithTag("suggestion_0_title", useUnmergedTree = true).assertTextEquals("Swimming")
        assertThat(compose.onAllNodesWithText("Swan boat", useUnmergedTree = true).fetchSemanticsNodes()).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
