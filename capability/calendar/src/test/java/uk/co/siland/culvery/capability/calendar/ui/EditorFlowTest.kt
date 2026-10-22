package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.test.TouchModeRule

/** The editor's steps and moves (9c §3.6), at 11:54 on Wednesday 23 September. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EditorFlowTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private val saturday = LocalDate.of(2026, 9, 26)
    private var form by mutableStateOf(editorForm())
    private var flow by mutableStateOf(editorFlow(form, null))
    private var child by mutableStateOf<PersonId?>(null)
    private val screen = ShownOnce(compose)

    private fun show(editor: EditorForm, start: EditorStep, childOnly: PersonId? = null) {
        child = childOnly
        form = editor
        flow = editorFlow(editor, childOnly, start)
        screen.show { ShowEditor(form, flow, childOnly = child) }
    }

    private fun guitar(child: PersonId? = null) = editorForm(childOnly = child).apply {
        updateTitle("Guitar lesson")
        if (child == null) toggleWho(SampleUi.mia.id)
        pickStartDate(saturday)
    }

    /** Taps [tag], then lets more than the move-on delay pass. */
    private fun tapAndWait(tag: String) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(tag).performClick()
        compose.mainClock.advanceTimeBy(MOVE_ON_MS + 32)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test
    fun stayDropsAPendingMoveOn() = runTest {
        val flow = EditorFlow(listOf(EditorStep.Who, EditorStep.StartDate, EditorStep.StartTime, EditorStep.Check), EditorStep.StartDate)
        val moves = launch { flow.runMoves() }
        flow.moveOnSoon(EditorStep.StartTime)
        advanceTimeBy(MOVE_ON_MS / 2)
        flow.stay()
        advanceTimeBy(MOVE_ON_MS)
        assertThat(flow.active).isEqualTo(EditorStep.StartDate)
        flow.moveOnSoon(EditorStep.StartTime)
        advanceTimeBy(MOVE_ON_MS + 1)
        assertThat(flow.active).isEqualTo(EditorStep.StartTime)
        moves.cancel()
    }

    @Test
    fun theStepsFollowAllDay() {
        val form = editorForm()
        val flow = editorFlow(form, null)
        assertThat(flow.steps).containsExactly(
            EditorStep.Who, EditorStep.StartDate, EditorStep.StartTime, EditorStep.EndDate, EditorStep.EndTime, EditorStep.Repeats, EditorStep.Check,
        ).inOrder()
        form.chooseAllDay(true)
        assertThat(flow.steps).containsExactly(EditorStep.Who, EditorStep.StartDate, EditorStep.EndDate, EditorStep.Repeats, EditorStep.Check).inOrder()
        form.chooseAllDay(false)
        assertThat(flow.steps).contains(EditorStep.EndTime)
        val child = editorForm(childOnly = SampleUi.mia.id)
        assertThat(editorSteps(child, SampleUi.mia.id)).doesNotContain(EditorStep.Repeats)
    }

    /** P9: a locked span opens no When pane at all. */
    @Test
    fun withTheDatesLockedTheWhenStepsGo() {
        assertThat(editorSteps(editorLockedVisit(), null)).containsExactly(EditorStep.Who, EditorStep.Check).inOrder()
        assertThat(editorSteps(editorTimedSpan(), null)).containsExactly(
            EditorStep.Who, EditorStep.StartDate, EditorStep.StartTime, EditorStep.EndDate, EditorStep.EndTime, EditorStep.Check,
        ).inOrder()
    }

    @Test
    fun aStartsDatePickMovesOnToTheStartTime() {
        show(guitar(), EditorStep.StartDate)
        tapAndWait("day_${saturday.plusDays(1)}")
        assertThat(form.eventWhen.startDate).isEqualTo(saturday.plusDays(1))
        assertThat(flow.active).isEqualTo(EditorStep.StartTime)
    }

    @Test
    fun withAllDayAStartsDatePickMovesOnToRepeats() {
        show(guitar().apply { chooseAllDay(true) }, EditorStep.StartDate)
        tapAndWait("day_${saturday.plusDays(1)}")
        assertThat(flow.active).isEqualTo(EditorStep.Repeats)

        val mia = SampleUi.mia.id
        show(guitar(mia).apply { chooseAllDay(true) }, EditorStep.StartDate, childOnly = mia)
        tapAndWait("day_${saturday.plusDays(1)}")
        assertThat(flow.active).isEqualTo(EditorStep.Check)
    }

    @Test
    fun anEndsDatePickMovesOnToTheEndTime() {
        show(guitar(), EditorStep.EndDate)
        tapAndWait("day_${saturday.plusDays(2)}")
        assertThat(form.eventWhen.endDays).isEqualTo(2)
        assertThat(flow.active).isEqualTo(EditorStep.EndTime)

        show(guitar().apply { chooseAllDay(true) }, EditorStep.EndDate)
        tapAndWait("day_${saturday.plusDays(2)}")
        assertThat(flow.active).isEqualTo(EditorStep.Repeats)
    }

    @Test
    fun theSteppersNeverMoveOn() {
        show(guitar(), EditorStep.StartTime)
        tapAndWait("start_hour_up")
        assertThat(form.eventWhen.startClock.hour).isEqualTo(10)
        assertThat(flow.active).isEqualTo(EditorStep.StartTime)
        compose.runOnIdle { flow.open(EditorStep.EndTime) }
        compose.waitForIdle()
        tapAndWait("end_hour_up")
        assertThat(flow.active).isEqualTo(EditorStep.EndTime)
    }

    @Test
    fun nextNamesTheNextStep() {
        assertThat(EditorStep.entries.map(::nextLabelFor)).containsExactly(
            "Next: Who", "Next: Starts", "Next: Start time", "Next: Ends", "Next: End time", "Next: Repeats", "Next: Check",
        ).inOrder()
        show(editorForm(), EditorStep.Who)
        compose.onNodeWithTag("editor_next").assertTextEquals("Next: Starts").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.StartDate)
        compose.onNodeWithTag("editor_next").assertTextEquals("Next: Start time")
    }

    @Test
    fun skipOnEndsKeepsTheEndAndGoesToRepeats() {
        val before = guitar().eventWhen
        listOf(EditorStep.EndDate, EditorStep.EndTime).forEach { step ->
            show(guitar(), step)
            compose.onNodeWithTag("editor_skip").assertTextEquals("Skip").performClick()
            assertThat(flow.active).isEqualTo(EditorStep.Repeats)
            assertThat(form.eventWhen).isEqualTo(before)
        }
        val mia = SampleUi.mia.id
        show(guitar(mia), EditorStep.EndDate, childOnly = mia)
        compose.onNodeWithTag("editor_skip").performClick()
        assertThat(flow.active).isEqualTo(EditorStep.Check)
    }
}
