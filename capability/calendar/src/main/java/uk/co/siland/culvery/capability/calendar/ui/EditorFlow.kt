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
enum class EditorStep { Who, StartDate, StartTime, EndDate, EndTime, Repeats, Check }

/** How long after a single-choice pick the next row lights up (handoff Behaviour). */
internal const val MOVE_ON_MS = 250L

/** What Next says it moves to (9c §3.6). */
internal fun nextLabelFor(step: EditorStep): String = "Next: " + when (step) {
    EditorStep.Who -> "Who"
    EditorStep.StartDate -> "Starts"
    EditorStep.StartTime -> "Start time"
    EditorStep.EndDate -> "Ends"
    EditorStep.EndTime -> "End time"
    EditorStep.Repeats -> "Repeats"
    EditorStep.Check -> "Check"
}

/**
 * Which of [stepsOf]'s steps the pane asks about; the steps can change while it is open, as All day hides the times.
 * Tapping a row [open]s it; Next moves on at once and [moveOnSoon] after [MOVE_ON_MS], unless the person has gone
 * somewhere else first.
 */
class EditorFlow(private val stepsOf: () -> List<EditorStep>, initial: EditorStep) {
    constructor(steps: List<EditorStep>, initial: EditorStep) : this({ steps }, initial)

    init {
        require(initial in steps) { "$initial isn't one of $steps" }
    }

    val steps: List<EditorStep> get() = stepsOf()

    var active by mutableStateOf(initial)
        private set

    // Bumped by every change of row, so a pending move made before it doesn't fire.
    private var generation = 0
    private val moves = Channel<Pair<Int, EditorStep>>(Channel.CONFLATED)

    fun open(step: EditorStep) {
        require(step in steps) { "$step isn't one of $steps" }
        generation++
        active = step
    }

    /** The row after [active]; Check after the last row, and none after Check. */
    val nextStep: EditorStep? get() = steps.getOrNull(steps.indexOf(active) + 1)

    fun next() {
        nextStep?.let(::open)
    }

    /** Drops a pending [moveOnSoon], for an action that leaves the person where they are (Add event, Delete, close). */
    fun stay() {
        generation++
    }

    fun moveOnSoon(to: EditorStep) {
        moves.trySend(generation to to)
    }

    /** After a Starts or Ends date pick (9c §3.6): on to its [time], or with All day on, to Repeats, else Check. */
    fun moveOnAfterDate(time: EditorStep) = moveOnSoon(if (time in steps) time else afterWhen)

    /** Skip on Ends keeps the end that follows the start: on to Repeats, else Check. */
    fun skipEnds() = open(afterWhen)

    private val afterWhen: EditorStep get() = if (EditorStep.Repeats in steps) EditorStep.Repeats else EditorStep.Check

    /** "Next: Starts", "Next: Check". */
    fun nextLabel(): String = nextLabelFor(checkNotNull(nextStep) { "Nothing comes after $active" })

    /** Carries out [moveOnSoon]'s moves while the editor is on screen. */
    internal suspend fun runMoves() {
        moves.receiveAsFlow().collectLatest { (asked, to) ->
            delay(MOVE_ON_MS)
            if (asked == generation && to in steps) open(to)
        }
    }
}

/**
 * The rows [form] shows (9c §3.6): Who, Starts and Ends with their times while All day is off, and Repeats on a new
 * event that a child isn't adding; a locked span opens no When pane. Check always ends them.
 */
internal fun editorSteps(form: EditorForm, childOnly: PersonId?): List<EditorStep> = buildList {
    add(EditorStep.Who)
    if (!form.datesLocked) {
        val timed = !form.eventWhen.allDay
        add(EditorStep.StartDate)
        if (timed) add(EditorStep.StartTime)
        add(EditorStep.EndDate)
        if (timed) add(EditorStep.EndTime)
    }
    if (form.canRepeat && childOnly == null) add(EditorStep.Repeats)
    add(EditorStep.Check)
}

/** A new event opens on Who, a change on Check, unless the caller says where (9 §3.4: the detail's For row opens Who). */
internal fun editorFlow(form: EditorForm, childOnly: PersonId?, start: EditorStep? = null): EditorFlow =
    EditorFlow({ editorSteps(form, childOnly) }, start ?: if (form.mode == EditorForm.Mode.New) EditorStep.Who else EditorStep.Check)
