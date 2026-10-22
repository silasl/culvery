package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.LocalToastClearance
import uk.co.siland.culvery.core.ui.ToastClearance
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.core.ui.test.windowBitmap

/** The editor on the 1280 × 800 dp canvas, at 11:54 on Wednesday 23 September, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventEditorScreenTest {
    /** Focus and the keyboard behave as on the tablet only in touch mode, set before the activity launches. */
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val c = DarkColors
    private val calls = mutableListOf<String>()
    private var form by mutableStateOf(editorForm())
    private var flow by mutableStateOf(editorFlow(form, null))
    private var childOnly by mutableStateOf<PersonId?>(null)
    private var savesTo by mutableStateOf<String?>("Family calendar")
    private var failure by mutableStateOf<SaveFailure?>(null)
    private var scale by mutableStateOf(1f)

    /** `WindowInsets.ime` is zero under Robolectric, so the hand-off's keyboard is passed in. */
    private fun show(
        editor: EditorForm = editorForm(),
        start: EditorStep? = null,
        child: PersonId? = null,
        keyboard: Dp = 0.dp,
    ) {
        use(editor, start, child)
        compose.setContent {
            ShowEditor(
                form, flow, scale = scale, savesTo = savesTo, failure = failure, childOnly = childOnly,
                keyboard = WindowInsets(bottom = keyboard),
                onClose = { calls += "close" },
                onSave = { calls += "save" },
                onDelete = { calls += "delete" },
                onRefusedWho = { calls += "refused" },
            )
        }
    }

    /** Puts [editor] on screen in place of the one showing. */
    private fun use(editor: EditorForm, start: EditorStep? = null, child: PersonId? = null) {
        childOnly = child
        form = editor
        flow = editorFlow(editor, child, start)
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun valueColor(row: String): Color = node("${row}_value").textLayout().layoutInput.style.color

    /** Whether any pixel inside [tag]'s bounds is exactly [color]: a glyph's core is drawn in its tint. */
    private fun draws(tag: String, color: Color): Boolean {
        val n = node(tag)
        val bitmap = windowBitmap(n)
        val r = n.fetchSemanticsNode().boundsInWindow
        return (r.left.toInt() until r.right.toInt()).any { x -> (r.top.toInt() until r.bottom.toInt()).any { y -> bitmap.getPixel(x, y) == color.toArgb() } }
    }

    private fun SemanticsNodeInteraction.fill(): Int = pixelAt(this, 300.dp, 5.dp)

    private fun newWithTitleWhoAndDay() = editorForm().apply {
        updateTitle("Guitar lesson")
        toggleWho(SampleUi.mia.id)
        pickStartDate(SampleUi.TODAY.plusDays(3))
    }

    @Test
    fun theTopBarHasCloseTitleAndContextOnly() {
        show()
        val close = bounds("sheet_close")
        assertThat((close.right - close.left).value).isWithin(0.5f).of(48f)
        assertThat(close.left.value).isWithin(0.5f).of(32f)
        compose.onAllNodesWithText("New event").filterToOne(isHeading()).assertExists()
        node("editor_context").assertTextEquals("From Calendar · Alex signed in")
        val density = compose.density.density
        val inBar = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.boundsInRoot.top / density < EditorDimens.barHeight.value }
        assertThat(inBar.map { it.config.getOrNull(SemanticsProperties.TestTag) }).containsExactly("sheet_close")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theColumnsAreAsDrawn() {
        show()
        val column = bounds("editor_column")
        val pane = bounds("editor_pane")
        val window = compose.onRoot().getUnclippedBoundsInRoot()
        assertThat((column.right - column.left).value).isWithin(0.5f).of(507f)
        assertThat((pane.left - column.right).value).isWithin(0.5f).of(21f)
        assertThat((window.bottom - column.bottom).value).isWithin(0.5f).of(29f)
        assertThat((window.bottom - pane.bottom).value).isWithin(0.5f).of(29f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Handoff 6a then 6b: while the title has focus no row is lit, though the pane already asks about Who. */
    @Test
    fun aNewEventFocusesTheTitleWithWhoActive() {
        show()
        node("editor_title").assertIsFocused()
        assertThat(flow.active).isEqualTo(EditorStep.Who)
        node("editor_question").assertTextEquals("Who’s it for?")
        val who = compose.onNodeWithTag("editor_row_who").assertIsNotSelected()
        assertThat(who.fill()).isEqualTo(c.surf.toArgb())
        assertThat(draws("editor_row_who_icon", c.accent)).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("editor_title").performImeAction()
        node("editor_title").assertIsNotFocused()
        compose.onNodeWithTag("editor_row_who").assertIsSelected()
        assertThat(who.fill()).isEqualTo(c.accentSoft.toArgb())
        assertThat(draws("editor_row_who_icon", c.accent)).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The words drawn inside [tag], for rows whose parts aren't tagged. */
    private fun textColor(tag: String, text: String): Color =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).textLayout().layoutInput.style.color

    /** On Change every answered row is done, All day and all; only a new event's untouched rows are placeholders. */
    @Test
    fun aChangeShowsEveryRowItHasAsDone() {
        val day = SampleUi.TODAY.plusDays(3)
        show(editorEditOf(EventTime.AllDay(day), EventTime.AllDay(day.plusDays(1)), "Bin day", "family"))
        compose.onNodeWithTag("editor_all_day").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        assertThat(valueColor("editor_row_who")).isEqualTo(c.ink)
        listOf("editor_starts", "editor_ends").forEach { row ->
            assertWithMessage(row).that(textColor("${row}_date", "Sat 26 Sep")).isEqualTo(c.ink)
            node("${row}_time").assertDoesNotExist()
        }
        listOf("editor_row_who", "editor_starts", "editor_ends").forEach { row ->
            assertWithMessage(row).that(draws("${row}_icon", c.accent)).isTrue()
            assertWithMessage(row).that(draws("${row}_icon", c.mute)).isFalse()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** P13: the time always has a value, so only Repeats is optional; with no Repeats row there is no hint. */
    @Test
    fun theHintSaysRepeatsAreOptionalWhereThereAreRepeats() {
        show(newWithTitleWhoAndDay())
        compose.onNodeWithTag("editor_save_hint").assertTextEquals("Repeats are optional")
        compose.assertClickablesHaveRoleAndLabel()
        compose.runOnIdle { use(editorDinnerOf(Duration.ofHours(1)), start = EditorStep.StartDate) }
        node("editor_save_hint").assertDoesNotExist()
        val child = editorForm(childOnly = SampleUi.mia.id).apply {
            updateTitle("Sleepover")
            pickStartDate(SampleUi.TODAY.plusDays(4))
        }
        compose.runOnIdle { use(child, child = SampleUi.mia.id) }
        node("editor_save_hint").assertDoesNotExist()
        assertThat(compose.onAllNodesWithText("Time is optional").fetchSemanticsNodes()).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theRowsShowTheirStates() {
        show(start = EditorStep.StartTime)
        node("editor_row_who_value").assertTextEquals("Choose who")
        assertThat(valueColor("editor_row_who")).isEqualTo(c.mute)
        assertThat(draws("editor_row_who_icon", c.mute)).isTrue()
        assertThat(draws("editor_row_who_icon", c.accent)).isFalse()
        assertThat(textColor("editor_starts_date", "Choose a day")).isEqualTo(c.mute)
        compose.runOnIdle { form.pickStartDate(SampleUi.TODAY.plusDays(3)) }
        compose.onNodeWithTag("editor_title").performImeAction()
        compose.runOnIdle { flow.open(EditorStep.Repeats) }
        val starts = compose.onNodeWithTag("editor_starts").assertIsNotSelected()
        assertThat(starts.fill()).isEqualTo(c.surf.toArgb())
        assertThat(draws("editor_starts_icon", c.accent)).isTrue()
        assertThat(textColor("editor_starts_date", "Sat 26 Sep")).isEqualTo(c.ink)
        val rows = listOf("editor_row_who", "editor_starts", "editor_ends", "editor_row_repeats").map { bounds(it) }
        rows.forEach { assertThat((it.bottom - it.top).value).isWithin(0.5f).of(77f) }
        val all = listOf("editor_row_who", "editor_all_day", "editor_starts", "editor_ends", "editor_row_repeats").map { bounds(it) }
        all.zipWithNext().forEach { (a, b) -> assertThat((b.top - a.bottom).value).isWithin(0.5f).of(9f) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun tappingARowOpensItsPane() {
        show()
        compose.onNodeWithTag("editor_starts_date").performClick()
        compose.onNodeWithTag("editor_starts").assertIsSelected()
        compose.onNodeWithTag("editor_row_who").assertIsNotSelected()
        assertThat(flow.active).isEqualTo(EditorStep.StartDate)
        node("editor_question").assertTextEquals("Which day does it start?")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun nextMovesOnAndIsNeverGreen() {
        show()
        val next = compose.onNodeWithTag("editor_next").assertTextEquals("Next: Starts")
        assertThat(pixelAt(next, 24.dp, 4.dp)).isEqualTo(c.surf2.toArgb())
        next.performClick()
        assertThat(flow.active).isEqualTo(EditorStep.StartDate)
        compose.onNodeWithTag("editor_next").assertTextEquals("Next: Start time")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun skipShowsOnEndsAndRepeatsOnly() {
        show()
        for (step in flow.steps) {
            compose.runOnIdle { flow.open(step) }
            val shown = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "editor_skip")).fetchSemanticsNodes().isNotEmpty()
            assertWithMessage("$step").that(shown).isEqualTo(step in listOf(EditorStep.EndDate, EditorStep.EndTime, EditorStep.Repeats))
        }
        compose.runOnIdle { flow.open(EditorStep.EndTime) }
        compose.onNodeWithTag("editor_skip").assertTextEquals("Skip").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.Repeats)
        assertThat(form.edited).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aChildHasNoRepeatsRowAndAnEditHasNone() {
        show(editorForm(childOnly = SampleUi.mia.id), child = SampleUi.mia.id)
        node("editor_row_who_value").assertTextEquals("Mia")
        node("editor_starts").assertExists()
        node("editor_row_repeats").assertDoesNotExist()
        assertThat(flow.steps).doesNotContain(EditorStep.Repeats)
        compose.assertClickablesHaveRoleAndLabel()
        compose.runOnIdle { use(editorDinnerOf(Duration.ofHours(1))) }
        node("editor_starts_time").assertExists()
        node("editor_ends_time").assertExists()
        node("editor_row_repeats").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun moveOnSoonWaitsAQuarterSecond() {
        show(start = EditorStep.StartDate)
        compose.mainClock.autoAdvance = false
        flow.moveOnSoon(EditorStep.StartTime)
        compose.mainClock.advanceTimeBy(MOVE_ON_MS - 16, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.StartDate)
        compose.mainClock.advanceTimeBy(32, ignoreFrameDuration = true)
        assertThat(flow.active).isEqualTo(EditorStep.StartTime)
        // A row tapped before the move lands keeps the person where they went.
        compose.runOnIdle { flow.open(EditorStep.StartDate) }
        flow.moveOnSoon(EditorStep.StartTime)
        compose.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
        compose.onNodeWithTag("editor_row_who").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertThat(flow.active).isEqualTo(EditorStep.Who)
        compose.mainClock.advanceTimeBy(300)
        assertThat(flow.active).isEqualTo(EditorStep.Who)
        compose.mainClock.autoAdvance = true
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theNewQuestionIsAPaneTitle() {
        show(start = EditorStep.StartDate)
        flow.moveOnSoon(EditorStep.StartTime)
        compose.mainClock.advanceTimeBy(MOVE_ON_MS + 16)
        node("editor_question").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "What time does it start?"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The colour each clickable is filled with, read just inside its top edge at its middle, clear of its words. */
    private fun fills(): Map<String, Int> {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { SemanticsProperties.EditableText !in it.config }
        val bitmap = windowBitmap(compose.onRoot())
        val density = compose.density.density
        return nodes.associate { n: SemanticsNode ->
            val r = n.boundsInWindow
            val name = n.config.getOrNull(SemanticsProperties.TestTag) ?: "node ${n.id}"
            name to bitmap.getPixel(((r.left + r.right) / 2).toInt(), (r.top + 4 * density).toInt())
        }
    }

    @Test
    fun addEventIsTheOnlyGreenButton() {
        show()
        assertThat(fills().filterValues { it == c.accent.toArgb() }).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
        compose.runOnIdle { use(newWithTitleWhoAndDay()) }
        assertThat(fills().filterValues { it == c.accent.toArgb() }.keys).containsExactly("editor_save")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFootSaysWhatIsMissing() {
        show()
        compose.onNodeWithTag("editor_save").assertIsNotEnabled()
        compose.onNodeWithTag("editor_save_reason").assertTextEquals("Needs a title, who and a day")
        assertThat(bounds("editor_save_reason").top.value).isGreaterThan(bounds("editor_save").bottom.value)
        node("editor_save_reason_icon").assertIsDisplayed()
        compose.runOnIdle {
            form.updateTitle("Guitar lesson")
            form.toggleWho(SampleUi.mia.id)
        }
        compose.onNodeWithTag("editor_save_reason").assertTextEquals("Needs a day")
        compose.runOnIdle { form.pickStartDate(SampleUi.TODAY.plusDays(3)) }
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        node("editor_save_reason").assertDoesNotExist()
        compose.onNodeWithTag("editor_save_hint").assertTextEquals("Repeats are optional")
        assertThat(bounds("editor_save_hint").top.value).isGreaterThan(bounds("editor_save").bottom.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFootSaysWhereItSaves() {
        show()
        compose.onNodeWithTag("editor_saves_to").assertTextEquals("Saves to the Family calendar")
        node("editor_saves_to_icon").assertIsDisplayed()
        assertThat(compose.onAllNodesWithText("Change").fetchSemanticsNodes()).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun changeEventSaysItsOwnCalendar() {
        savesTo = "Sam’s calendar"
        show(editorDinnerOf(Duration.ofHours(1)))
        compose.onNodeWithTag("editor_saves_to").assertTextEquals("Saves to the Sam’s calendar")
        assertThat(savesToLine("Family calendar")).isEqualTo("Saves to the Family calendar")
        assertThat(savesToLine("Family Calendar")).isEqualTo("Saves to the Family calendar")
        assertThat(savesToLine("alex@example.com")).isEqualTo("Saves to the alex@example.com calendar")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun changeEventSaysSaveChangesAndOffersDelete() {
        show(editorDinnerOf(Duration.ofHours(1)))
        compose.onAllNodesWithText("Change event").filterToOne(isHeading()).assertExists()
        compose.onNodeWithTag("editor_save").assertTextEquals("Save changes").assertIsEnabled()
        assertThat(bounds("editor_delete").right.value).isAtMost(bounds("editor_save").left.value)
        assertThat(flow.active).isEqualTo(EditorStep.Check)
        node("editor_question").assertTextEquals("Check it")
        node("editor_next").assertDoesNotExist()
        node("editor_title").assertIsNotFocused()
        listOf("editor_row_who", "editor_starts", "editor_ends").forEach { compose.onNodeWithTag(it).assertIsNotSelected() }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The foot keeps toasts above it (9c §5): the clearance is its top, and the discard card's after the close button. */
    @Test
    fun theFootKeepsToastsAbove() {
        val clearance = ToastClearance()
        use(editorDinnerOf(Duration.ofHours(1)))
        compose.setContent {
            CompositionLocalProvider(LocalToastClearance provides clearance) {
                ShowEditor(form, flow, savesTo = null)
            }
        }
        compose.waitForIdle()
        assertThat(clearance.top).isEqualTo(node("editor_save").fetchSemanticsNode().positionInWindow.y)
        compose.runOnIdle { form.updateTitle("Dinner at Gran’s") }
        compose.onNodeWithTag("sheet_close").performClick()
        compose.waitForIdle()
        assertThat(clearance.top).isEqualTo(node("editor_discard").fetchSemanticsNode().positionInWindow.y)
    }

    /** The busy foot keeps its buttons and its hint where they were. */
    @Test
    fun theFootButtonsKeepTheirPlacesWhileBusy() {
        var busy by mutableStateOf(false)
        use(editorDinnerOf(Duration.ofHours(1)))
        compose.setContent { ShowEditor(form, flow, busy = busy) }
        val tags = listOf("editor_save", "editor_delete", "editor_save_hint")
        val before = tags.map { bounds(it) }
        busy = true
        compose.waitForIdle()
        assertThat(tags.map { bounds(it) }).isEqualTo(before)
    }

    @Test
    fun closingUnchangedClosesAtOnce() {
        show()
        compose.onNodeWithTag("sheet_close").performClick()
        assertThat(calls).containsExactly("close")
        node("editor_discard").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun closingWithChangesAsksToDiscard() {
        show()
        compose.onNodeWithTag("editor_title").performTextInput("Swim")
        compose.onNodeWithTag("sheet_close").performClick()
        compose.onNodeWithTag("editor_discard").assertIsDisplayed()
        compose.onNodeWithText("Discard this event?").assertIsDisplayed()
        node("editor_save").assertDoesNotExist()
        assertThat(calls).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("editor_discard_keep").assertTextEquals("Keep editing").performClick()
        node("editor_discard").assertDoesNotExist()
        compose.onNodeWithTag("editor_save").assertExists()
        compose.onNodeWithTag("sheet_close").performClick()
        compose.onNodeWithTag("editor_discard_confirm").assertTextEquals("Discard").performClick()
        assertThat(calls).containsExactly("close")
        assertThat(discardIcon(EditorForm.Mode.New)).isEqualTo(Icons.DELETE)

        calls.clear()
        compose.runOnIdle { use(editorDinnerOf(Duration.ofHours(1))) }
        compose.runOnIdle { form.updateTitle("Dinner at Gran’s") }
        compose.onNodeWithTag("sheet_close").performClick()
        compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
        assertThat(discardIcon(form.mode)).isEqualTo(Icons.CLOSE)
        compose.onNodeWithTag("editor_discard_confirm").performClick()
        assertThat(calls).containsExactly("close")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun withTheKeyboardUpTheRowsShrinkAndTheFootGoes() {
        show(keyboard = KEYBOARD)
        node("editor_title").assertIsFocused()
        val values = mapOf("who" to "Who", "repeats" to "Doesn’t repeat")
        values.forEach { (row, value) ->
            node("editor_row_${row}_value").assertTextEquals(value)
            // No label: the row reads its value alone.
            compose.onNodeWithTag("editor_row_$row").assertTextEquals(value)
        }
        listOf("editor_row_who", "editor_starts", "editor_ends", "editor_row_repeats").forEach { row ->
            val r = bounds(row)
            assertWithMessage(row).that((r.bottom - r.top).value).isWithin(0.5f).of(61f)
        }
        assertThat(compose.onAllNodesWithText("STARTS", useUnmergedTree = true).fetchSemanticsNodes()).isEmpty()
        node("editor_save").assertDoesNotExist()
        node("editor_saves_to").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLongValueEllipsisesOnOneLineAtTheDefaultScale() {
        show(editorLockedVisit())
        val starts = node("editor_starts_value").textLayout()
        assertThat(starts.lineCount).isEqualTo(1)
        assertThat(starts.isLineEllipsized(0)).isTrue()
        node("editor_starts_time").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A pin of current behaviour, not a fix: one show() on open, and none when the editor is covered and uncovered. The walk-through will try to reproduce the keyboard note. */
    @Test
    fun theKeyboardIsAskedForOncePerOpening() {
        val keyboard = object : SoftwareKeyboardController {
            var shown = 0
            override fun show() { shown++ }
            override fun hide() {}
        }
        var covered by mutableStateOf(false)
        use(editorForm())
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                // As the shell's OverlayLayer(hidden = ...) holds the editor under the PIN pad: composed, not heard.
                Box(Modifier.then(if (covered) Modifier.clearAndSetSemantics {} else Modifier)) {
                    ShowEditor(form, flow)
                }
            }
        }
        compose.runOnIdle { assertThat(keyboard.shown).isEqualTo(1) }
        compose.onNodeWithTag("editor_row_who").performClick()
        node("editor_title").assertIsNotFocused()
        covered = true
        compose.waitForIdle()
        covered = false
        compose.waitForIdle()
        compose.runOnIdle { assertThat(keyboard.shown).isEqualTo(1) }
    }

    @Test
    fun theKeyboardsNextMovesToWho() {
        show(start = EditorStep.StartDate)
        node("editor_title").assertIsFocused()
        compose.onNodeWithTag("editor_title").performImeAction()
        node("editor_title").assertIsNotFocused()
        assertThat(flow.active).isEqualTo(EditorStep.Who)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFailureCardShowsAboveTheFoot() {
        failure = SaveFailure("Google Calendar", WriteProblem.Tablet)
        show(newWithTitleWhoAndDay())
        compose.onNodeWithTag("editor_failure").assertIsDisplayed()
        assertThat(bounds("editor_failure").bottom.value).isAtMost(bounds("editor_save").top.value)
        compose.onNodeWithTag("editor_save").assertTextEquals("Try again")
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun description(tag: String): List<String> =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]

    @Test
    fun theRowsReadTitleWhoAllDayStartsEndsRepeats() {
        show()
        val tops = listOf("editor_title", "editor_row_who", "editor_all_day", "editor_starts", "editor_ends", "editor_row_repeats").map { bounds(it).top }
        assertThat(tops).isInStrictOrder()
        compose.onNodeWithTag("editor_all_day").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        assertThat(description("editor_starts_date")).containsExactly("Starts, Choose a day")
        node("editor_starts_time").assertDoesNotExist()
        assertThat(description("editor_ends")).containsExactly("Ends, choose a start day first")
        compose.onNodeWithTag("editor_ends").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.runOnIdle { form.pickStartDate(SampleUi.TODAY.plusDays(3)) }
        assertThat(description("editor_starts_date")).containsExactly("Starts, Saturday 26 September")
        assertThat(description("editor_starts_time")).containsExactly("Starts at 09:00")
        assertThat(description("editor_ends_date")).containsExactly("Ends, Saturday 26 September")
        assertThat(description("editor_ends_time")).containsExactly("Ends at 10:00")
        compose.onNodeWithTag("editor_starts_time").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.StartTime)
        node("editor_question").assertTextEquals("What time does it start?")
        compose.onNodeWithTag("editor_ends_date").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.EndDate)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun allDayHidesBothTimes() {
        show(newWithTitleWhoAndDay())
        node("editor_starts_time").assertExists()
        node("editor_ends_time").assertExists()
        compose.onNodeWithTag("editor_all_day").performClick()
        assertThat(form.eventWhen.allDay).isTrue()
        node("editor_starts_time").assertDoesNotExist()
        node("editor_ends_time").assertDoesNotExist()
        assertThat(description("editor_starts_date")).containsExactly("Starts, Saturday 26 September")
        assertThat(description("editor_ends_date")).containsExactly("Ends, Saturday 26 September")
        compose.onNodeWithTag("editor_all_day").performClick()
        node("editor_starts_time").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun allDayMovesAnOpenTimePaneToItsDate() {
        show(newWithTitleWhoAndDay(), start = EditorStep.StartTime)
        compose.onNodeWithTag("editor_all_day").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.StartDate)
        compose.onNodeWithTag("editor_all_day").performClick()
        compose.runOnIdle { flow.open(EditorStep.EndTime) }
        compose.onNodeWithTag("editor_all_day").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.EndDate)
    }

    @Test
    fun withTheKeyboardUpTheAllDayRowHidesAndNothingIsCut() {
        show(keyboard = KEYBOARD)
        for (s in listOf(1f, 1.3f, 1.5f)) {
            compose.runOnIdle { scale = s }
            compose.waitForIdle()
            node("editor_title").assertIsFocused()
            node("editor_all_day").assertDoesNotExist()
            val keyboardTop = compose.onRoot().getUnclippedBoundsInRoot().bottom - KEYBOARD
            listOf("editor_row_who", "editor_starts", "editor_ends", "editor_row_repeats").forEach { row ->
                assertWithMessage("$row at $s").that(bounds(row).bottom.value).isAtMost(keyboardTop.value)
            }
            compose.onAllNodes(hasAnyAncestor(hasTestTag("editor_column")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { n ->
                    val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
                    layout.firstOrNull()?.let {
                        assertWithMessage("${n.config.getOrNull(SemanticsProperties.Text)} at $s").that(it.didOverflowHeight || it.isLineEllipsized(0)).isFalse()
                    }
                }
            compose.assertClickablesHaveRoleAndLabel()
        }
        compose.runOnIdle { form.updateTitle("Swim") }
        compose.onNodeWithTag("editor_row_who").performClick()
        node("editor_all_day").assertExists()
    }

    /** A span past the cap keeps its dates: All day, Starts and Ends are locked and say why. */
    @Test
    fun aLockedSpanShowsLockedRows() {
        show(editorLockedVisit())
        listOf("editor_all_day", "editor_starts", "editor_ends").forEach { row ->
            node("${row}_value").assertTextEquals("Mon 28 Sep – Sun 6 Dec · change dates in Google Calendar on your phone")
            node("${row}_lock").assertExists()
            compose.onNodeWithTag(row).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LOCKED_SPAN))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            compose.onNodeWithTag(row).performClick()
            assertThat(flow.active).isEqualTo(EditorStep.Check)
        }
        assertThat(LOCKED_SPAN).isEqualTo("dates locked, as it runs over more than 60 days")
        node("editor_starts_time").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    private companion object {
        val KEYBOARD = 320.dp
    }
}

