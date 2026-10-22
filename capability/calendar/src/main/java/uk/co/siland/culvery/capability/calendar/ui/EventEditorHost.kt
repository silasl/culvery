package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.Suggestion
import uk.co.siland.culvery.capability.calendar.WeekFocus
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.daySpan
import uk.co.siland.culvery.capability.calendar.newClientKey
import uk.co.siland.culvery.capability.calendar.weeksTo
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "EventEditorHost"

/** Where the editor was opened from, which its top bar says. */
enum class EditorFrom { Home, Calendar }

/** "From Calendar · Alex signed in", or "From Home" with nobody signed in (handoff top bar). */
fun contextLine(from: EditorFrom, signedIn: String?): String = "From ${from.name}" + signedIn?.let { " · $it signed in" }.orEmpty()

/** What the editor was opened for. */
sealed interface EditorRequest {
    val from: EditorFrom

    /** A new event on [day], or with no day yet for null (9 §5 call 9). */
    data class New(val day: LocalDate?, override val from: EditorFrom) : EditorRequest

    /** A change to [ref], opening on [start]: Check, or Who from the detail's For row (9 §3.3). */
    data class Edit(val ref: EventRef, override val from: EditorFrom, val start: EditorStep = EditorStep.Check) : EditorRequest
}

/**
 * Everything the editor needs that loads once, read before the editor is shown ([loadEditor]) so its first frame is
 * the full editor. [held] is only the first value; the host keeps it live. [childOnly] is the child signed in as it opened.
 */
internal class Opened(
    val request: EditorRequest,
    val form: EditorForm,
    val flow: EditorFlow,
    val members: List<Member>,
    val held: Held,
    val service: String,
    val calendar: String? = null,
    val masterName: String? = null,
    val childOnly: PersonId? = null,
)

/** The editor's data for [request], or null when there is nothing to open: no master calendar, or the event has gone. */
internal suspend fun loadEditor(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor): Opened? {
    val service = repo.masterService.first() ?: return null
    val at = editor.openedAt()
    val child = editor.session.value?.takeIf { it.role == Role.CHILD }?.person?.id
    val original = (request as? EditorRequest.Edit)?.let { repo.editable(it.ref).first() ?: return null }
    val form = EditorForm(
        mode = original?.let { EditorForm.Mode.Edit(it) } ?: EditorForm.Mode.New,
        today = at.toLocalDate(),
        now = at.toLocalTime(),
        zone = at.zone,
        childOnly = child,
        preselectedDay = (request as? EditorRequest.New)?.day,
        order = repo.people.first().map { it.id },
        calendarPerson = original?.calendarPerson,
        repeats = child == null,
    )
    val flow = editorFlow(form, child, (request as? EditorRequest.Edit)?.start)
    val masterName = if (request is EditorRequest.New) repo.masterCalendarName.first() else null
    return Opened(request, form, flow, repo.members.first(), repo.held(form.today).first(), service, original?.sourceName, masterName, child)
}

/**
 * Loads the editor, then shows it full screen, so it draws complete in its first frame. Shows nothing when there is
 * nothing to open, and says so only when the load fails. An overlay shown meanwhile (generation changed) is left alone.
 */
internal suspend fun OverlayHost.openEditor(
    request: EditorRequest,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    weekFocus: WeekFocus,
    load: suspend () -> Opened? = { loadEditor(request, repo, editor) },
) {
    val before = generation
    val opened = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't open the editor (${e::class.simpleName})")
        editor.couldNotOpen()
        null
    } ?: return
    if (generation != before) return
    showFullScreen {
        EventEditorHost(
            opened = opened,
            repo = repo,
            editor = editor,
            weekFocus = weekFocus,
            onClose = { dismiss() },
            onDeleteAuthorised = { ref -> showDetail(ref, today, repo, editor, weekFocus, request.from, DetailMode.ConfirmingDelete) },
            onEdited = ::markEdited,
        )
    }
}

/**
 * The full-screen Add event and Change event (9 §3.1). It owns the form for this opening, runs Save and Delete one at a
 * time (Save is disabled while one runs). An unchanged edit closes
 * with no PIN. A refusal keeps the editor open with the failure card, and so does the tablet failing before the write; a
 * queued or saved change closes it, and a new event then shows on the Calendar tab's week that holds it ([weekFocus]),
 * from Home too, unless no week shown holds it. Nothing in the form changes while a save runs. Delete authorises, then [onDeleteAuthorised] swaps to the detail sheet asking to confirm. A signed-in
 * Child's other Who tiles are locked while their session lasts. Try again after a tablet failure reuses the create's key.
 * [opened] is already loaded by [openEditor].
 */
@Composable
internal fun EventEditorHost(
    opened: Opened,
    repo: CalendarRepository,
    editor: CalendarEditor,
    weekFocus: WeekFocus,
    onClose: () -> Unit,
    onDeleteAuthorised: (EventRef) -> Unit,
    onEdited: (Boolean) -> Unit = {},
) {
    val request = opened.request
    val form = opened.form
    val household: List<Member> by repo.members.collectAsState(initial = opened.members)
    val held: Held by remember(opened) { repo.held(form.today) }.collectAsState(initial = opened.held)
    // Only a new event says where it saves; a change names its own calendar.
    val master: String? by remember(opened) { if (request is EditorRequest.New) repo.masterCalendarName else flowOf(null) }.collectAsState(initial = opened.masterName)
    val session by editor.session.collectAsState()
    val navigator = if (request.from == EditorFrom.Home) LocalShellNavigator.current else null
    var failure by remember(opened) { mutableStateOf<SaveFailure?>(null) }
    // Kept only after the tablet failed (WriteProblem.Tablet), so Try again can't make a second event (3a design §3.12).
    var createKey by remember(opened) { mutableStateOf(newClientKey()) }
    val action = rememberSingleAction(opened) { e ->
        Log.w(TAG, "Couldn't save (${e::class.simpleName})")
        failure = SaveFailure(opened.service, WriteProblem.Tablet)
    }
    val flow = opened.flow
    // Saving counts as edited, so the idle close never cuts a save off half done.
    val edited by rememberUpdatedState(onEdited)
    LaunchedEffect(form) { snapshotFlow { form.edited || action.busy }.collect { edited(it) } }

    // The event's first date shows on the week (9 §3.1 After Add); past the weeks shown, the toast names it instead (9 §3.4).
    fun showAdded(day: LocalDate) {
        val weeks = weeksTo(form.today, day) ?: return
        weekFocus.show(weeks)
        navigator?.openTab(CALENDAR_TAB_ID)
    }

    fun save() {
        if (action.busy || !form.canSave) return
        if (form.unchanged) {
            onClose()
            return
        }
        failure = null
        action.run {
            val draft = form.draft(createdBy = null)
            form.saving = true
            val result = try {
                when (val mode = form.mode) {
                    EditorForm.Mode.New -> editor.create(draft, createKey)
                    is EditorForm.Mode.Edit -> editor.update(mode.original.ref, draft, form.touched)
                }
            } finally {
                form.saving = false
            }
            if (result != EditResult.Rejected(WriteProblem.Tablet)) createKey = newClientKey()
            when (result) {
                EditResult.Done, EditResult.Queued -> {
                    onClose()
                    if (form.mode == EditorForm.Mode.New) showAdded(daySpan(draft.start, draft.end, form.zone).first)
                }
                EditResult.NotEditable -> onClose()
                is EditResult.Rejected -> failure = SaveFailure(opened.service, result.problem)
                EditResult.Cancelled -> Unit
            }
        }
    }

    fun delete() {
        val mode = form.mode as? EditorForm.Mode.Edit ?: return
        action.run {
            try {
                if (editor.mayDelete(mode.original.ref)) onDeleteAuthorised(mode.original.ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A delete, so the detail sheet's wording rather than the Save failure card.
                Log.w(TAG, "Couldn't start a delete (${e::class.simpleName})")
                editor.couldNotDelete(opened.service)
            }
        }
    }

    val child = session?.takeIf { it.role == Role.CHILD }?.person
    val suggest: suspend (String) -> List<Suggestion> = if (request is EditorRequest.New) repo.suggestions::matching else { _ -> emptyList() }
    // A new opening is a new editor: its focus and title state don't carry over from the last one.
    key(opened) {
        EventEditorScreen(
            form = form,
            flow = flow,
            members = household,
            context = contextLine(request.from, session?.person?.name),
            savesTo = if (form.mode == EditorForm.Mode.New) master else opened.calendar,
            service = opened.service,
            busy = action.busy,
            failure = failure,
            childOnly = child?.id,
            childName = child?.name,
            held = held,
            onClose = onClose,
            onSave = { save() },
            onDelete = { delete() },
            onRefusedWho = { session?.person?.name?.let(editor::refuseOtherWho) },
            suggest = suggest,
        )
    }
}

/**
 * Opens the editor for a new event on a day (an empty column's, or today from Home), or with no day for null. Null when
 * there is nowhere to add to (no writable master calendar with a writer), which hides the add entry points (2b-2 design §4.1).
 */
@Composable
internal fun rememberEventAdder(
    repo: CalendarRepository,
    editor: CalendarEditor,
    weekFocus: WeekFocus,
    today: LocalDate,
    from: EditorFrom,
    load: suspend (EditorRequest) -> Opened? = { loadEditor(it, repo, editor) },
): ((LocalDate?) -> Unit)? {
    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val addTo: String? by repo.masterService.collectAsState(initial = null)
    val add: (LocalDate?) -> Unit = remember(overlay, repo, editor, weekFocus, today, from, load) {
        { day ->
            if (!loading) {
                loading = true
                scope.launch {
                    try {
                        val request = EditorRequest.New(day, from)
                        overlay.openEditor(request, today, repo, editor, weekFocus) { load(request) }
                    } finally {
                        loading = false
                    }
                }
            }
        }
    }
    return add.takeIf { addTo != null }
}
