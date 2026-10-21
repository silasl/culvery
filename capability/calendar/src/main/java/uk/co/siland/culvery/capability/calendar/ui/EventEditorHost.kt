package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.Held
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
 * The form once loaded for [request], with the service the change goes to and the calendar a change is saved to; [form]
 * is null when there is nothing to open: the event has gone, or there is no master. [childOnly] is the child signed in
 * as it opened.
 */
private class Opened(
    val request: EditorRequest,
    val form: EditorForm?,
    val service: String,
    val calendar: String? = null,
    val childOnly: PersonId? = null,
)

private suspend fun open(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor): Opened {
    val service = repo.masterService.first() ?: return Opened(request, null, "")
    val at = editor.openedAt()
    val session = editor.session.value
    val child = session?.takeIf { it.role == Role.CHILD }?.person?.id
    val original = (request as? EditorRequest.Edit)?.let { repo.editable(it.ref).first() ?: return Opened(request, null, service) }
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
    return Opened(request, form, service, original?.sourceName, child)
}

/**
 * The full-screen Add event and Change event (9 §3.1). It owns the form for this opening, runs Save and Delete one at a
 * time (Save is disabled while one runs), and closes itself when there is nothing to edit. An unchanged edit closes
 * with no PIN. A refusal keeps the editor open with the failure card, and so does the tablet failing before the write; a
 * queued or saved change closes it, and a new event then shows on the Calendar tab's week that holds it ([weekFocus]),
 * from Home too, unless no week shown holds it. Nothing in the form changes while a save runs. Delete authorises, then [onDeleteAuthorised] swaps to the detail sheet asking to confirm. A signed-in
 * Child's other Who tiles are locked while their session lasts. Try again after a tablet failure reuses the create's key.
 * An editor that can't load closes with a toast.
 */
@Composable
internal fun EventEditorHost(
    request: EditorRequest,
    repo: CalendarRepository,
    editor: CalendarEditor,
    weekFocus: WeekFocus,
    onClose: () -> Unit,
    onDeleteAuthorised: (EventRef) -> Unit,
    onEdited: (Boolean) -> Unit = {},
) {
    val opened: Opened? by produceState<Opened?>(null, request) {
        value = try {
            open(request, repo, editor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The editor closes (no form) and says so, instead of the store failure killing the app.
            Log.w(TAG, "Couldn't open the editor (${e::class.simpleName})")
            editor.couldNotOpen()
            Opened(request, null, "")
        }
    }
    val members: List<Member>? by repo.members.collectAsState(initial = null)
    val master: String? by repo.masterCalendarName.collectAsState(initial = null)
    val session by editor.session.collectAsState()
    val navigator = if (request.from == EditorFrom.Home) LocalShellNavigator.current else null
    var failure by remember(request) { mutableStateOf<SaveFailure?>(null) }
    var picker by remember(request) { mutableStateOf(EditorPicker.None) }
    // Kept only after the tablet failed (WriteProblem.Tablet), so Try again can't make a second event (3a design §3.12).
    var createKey by remember(request) { mutableStateOf(newClientKey()) }
    val action = rememberSingleAction(request) { e ->
        Log.w(TAG, "Couldn't save (${e::class.simpleName})")
        failure = SaveFailure(opened?.service.orEmpty(), WriteProblem.Tablet)
    }

    // Swapped straight to another request, the last form shows until this one's loads: wait instead.
    val loaded = opened?.takeIf { it.request == request } ?: return
    val form = loaded.form
    if (form == null) {
        LaunchedEffect(request) { onClose() }
        return
    }
    val household = members ?: return
    val held: Held? by remember(form.today) { repo.held(form.today) }.collectAsState(initial = null)
    val flow = remember(form) { editorFlow(form, loaded.childOnly, (request as? EditorRequest.Edit)?.start) }
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
                is EditResult.Rejected -> failure = SaveFailure(loaded.service, result.problem)
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
                editor.couldNotDelete(loaded.service)
            }
        }
    }

    val child = session?.takeIf { it.role == Role.CHILD }?.person
    // A new request is a new editor: its focus and title state don't carry over from the last one.
    key(request) {
        EventEditorScreen(
            form = form,
            flow = flow,
            members = household,
            context = contextLine(request.from, session?.person?.name),
            savesTo = if (form.mode == EditorForm.Mode.New) master else loaded.calendar,
            service = loaded.service,
            busy = action.busy,
            failure = failure,
            picker = picker,
            childOnly = child?.id,
            childName = child?.name,
            held = held,
            onClose = onClose,
            onSave = { save() },
            onDelete = { delete() },
            onRefusedWho = { session?.person?.name?.let(editor::refuseOtherWho) },
            onPicker = { picker = it },
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
): ((LocalDate?) -> Unit)? {
    val overlay = LocalOverlayHost.current
    val addTo: String? by repo.masterService.collectAsState(initial = null)
    val add: (LocalDate?) -> Unit = remember(overlay, repo, editor, weekFocus, today, from) {
        { day -> overlay.showEditor(EditorRequest.New(day, from), today, repo, editor, weekFocus) }
    }
    return add.takeIf { addTo != null }
}
