package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.core.household.PersonId

/** The editor's rows, then Check: which question the pane asks. */
enum class EditorStep { Who, Day, Time, Repeats, Check }

/** Which picker is drawn over the editor's Time pane: its start or its end time. */
enum class EditorPicker { None, StartTime, EndTime }

/** How long after a single-choice pick the next row lights up (handoff Behaviour). */
internal const val MOVE_ON_MS = 250L

/**
 * Which of [steps] the pane asks about. Tapping a row [open]s it; Next moves on at once and [moveOnSoon] after
 * [MOVE_ON_MS], unless the person has gone somewhere else first.
 */
class EditorFlow(val steps: List<EditorStep>, initial: EditorStep) {
    init {
        require(initial in steps) { "$initial isn't one of $steps" }
    }

    private var shown by mutableStateOf(initial)

    // Bumped by every change of row, so a pending move made before it doesn't fire.
    private var generation = 0
    private val moves = Channel<Int>(Channel.CONFLATED)

    var active: EditorStep
        get() = shown
        set(value) {
            require(value in steps) { "$value isn't one of $steps" }
            generation++
            shown = value
        }

    fun open(step: EditorStep) {
        active = step
    }

    /** The row after [active]; Check after the last row, and none after Check. */
    val nextStep: EditorStep? get() = steps.getOrNull(steps.indexOf(active) + 1)

    fun next() {
        nextStep?.let { active = it }
    }

    fun moveOnSoon() {
        moves.trySend(generation)
    }

    /** "Next: Day", "Next: Check". */
    fun nextLabel(): String = "Next: ${checkNotNull(nextStep) { "Nothing comes after $active" }.name}"

    /** Carries out [moveOnSoon]'s moves while the editor is on screen. */
    internal suspend fun runMoves() {
        moves.receiveAsFlow().collectLatest { asked ->
            delay(MOVE_ON_MS)
            if (asked == generation) next()
        }
    }
}

/**
 * The rows [form] shows (9 §3.1): Who, Day and Time, and Repeats on a new event that a child isn't adding; a locked
 * span's Day doesn't open and Time goes. Check always ends them.
 */
internal fun editorSteps(form: EditorForm, childOnly: PersonId?): List<EditorStep> = buildList {
    add(EditorStep.Who)
    if (!form.datesLocked) {
        add(EditorStep.Day)
        add(EditorStep.Time)
    }
    if (form.canRepeat && childOnly == null) add(EditorStep.Repeats)
    add(EditorStep.Check)
}

/** A new event opens on Who, a change on Check, unless the caller says where (9 §3.4: the detail's For row opens Who). */
internal fun editorFlow(form: EditorForm, childOnly: PersonId?, start: EditorStep? = null): EditorFlow =
    EditorFlow(editorSteps(form, childOnly), start ?: if (form.mode == EditorForm.Mode.New) EditorStep.Who else EditorStep.Check)
