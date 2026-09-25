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
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.TRY_AGAIN
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost

private const val TAG = "EventEditorHost"

/** What the add/edit sheet was opened for. */
sealed interface EditorRequest {
    /** A new event on [day] (a week column's date), or on today when null. */
    data class New(val day: LocalDate?) : EditorRequest

    data class Edit(val ref: EventRef) : EditorRequest
}

/** The form once loaded for [request]. [form] is null when there is nothing to open: the event has gone, or there is no master. */
private class Opened(val request: EditorRequest, val form: EventForm?, val label: String)

private suspend fun open(request: EditorRequest, repo: CalendarRepository, editor: CalendarEditor): Opened {
    val label = repo.masterLabel.first() ?: return Opened(request, null, "")
    val at = editor.openedAt()
    val mode = when (request) {
        is EditorRequest.New -> EventForm.Mode.New
        is EditorRequest.Edit -> EventForm.Mode.Edit(repo.editable(request.ref).first() ?: return Opened(request, null, label))
    }
    val form = EventForm(
        mode = mode,
        today = at.toLocalDate(),
        now = at.toLocalTime(),
        zone = at.zone,
        signedIn = editor.session.value?.person?.id,
        preselectedDay = (request as? EditorRequest.New)?.day,
    )
    return Opened(request, form, label)
}

/**
 * The add/edit sheet (2b-2 design §3.2). It owns the form for this opening, runs Save and Delete one at a time (Save
 * is disabled while one runs), and closes itself when there is nothing to edit. An unchanged edit closes with no PIN.
 * A refusal keeps the sheet open with the failure card, and so does the tablet failing before the write; a queued or
 * saved change closes it. Delete authorises, then [onDeleteAuthorised] swaps to the detail sheet asking to confirm.
 * A signed-in Child's other Who chips are disabled in a new event and in an edit alike (§6).
 */
@Composable
internal fun EventEditorHost(
    request: EditorRequest,
    repo: CalendarRepository,
    editor: CalendarEditor,
    onClose: () -> Unit,
    onDeleteAuthorised: (EventRef) -> Unit,
) {
    val opened: Opened? by produceState<Opened?>(null, request) { value = open(request, repo, editor) }
    val people: List<Person>? by repo.people.collectAsState(initial = null)
    val session by editor.session.collectAsState()
    var failure by remember(request) { mutableStateOf<String?>(null) }
    var picker by remember(request) { mutableStateOf(EditorPicker.None) }
    val action = rememberSingleAction(request) { e ->
        Log.w(TAG, "Couldn't save", e)
        failure = couldNotSave(opened?.label.orEmpty(), TRY_AGAIN)
    }

    // Swapped straight to another request, the last form shows until this one's loads: wait instead.
    val loaded = opened?.takeIf { it.request == request } ?: return
    val form = loaded.form
    if (form == null) {
        LaunchedEffect(request) { onClose() }
        return
    }
    val household = people ?: return

    fun save() {
        if (action.busy || !form.canSave) return
        if (form.unchanged) {
            onClose()
            return
        }
        failure = null
        action.run {
            val result = when (val mode = form.mode) {
                EventForm.Mode.New -> editor.create(form.draft(createdBy = null))
                is EventForm.Mode.Edit -> editor.update(mode.original.ref, form.draft(createdBy = null))
            }
            when (result) {
                EditResult.Done, EditResult.Queued, EditResult.NotEditable -> onClose()
                is EditResult.Rejected -> failure = couldNotSave(loaded.label, result.message)
                EditResult.Cancelled -> Unit
            }
        }
    }

    fun delete() {
        val mode = form.mode as? EventForm.Mode.Edit ?: return
        action.run { if (editor.mayDelete(mode.original.ref)) onDeleteAuthorised(mode.original.ref) }
    }

    // A new request is a new sheet: its focus, scroll and title state don't carry over from the last one.
    key(request) {
        EventEditorSheet(
            form = form,
            people = household,
            childOnly = session?.takeIf { it.role == Role.CHILD }?.person?.id,
            busy = action.busy,
            failure = failure,
            picker = picker,
            onClose = onClose,
            onSave = { save() },
            onDelete = { delete() },
            onRefusedWho = { session?.person?.name?.let(editor::refuseOtherWho) },
            onPicker = { picker = it },
        )
    }
}

/**
 * Opens the add/edit sheet for a new event on a day (a week column's), or on today for null. Null when there is
 * nowhere to add to (no writable master calendar with a writer), which hides the add entry points (2b-2 design §4.1).
 */
@Composable
internal fun rememberEventAdder(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): ((LocalDate?) -> Unit)? {
    val overlay = LocalOverlayHost.current
    val addTo: String? by repo.masterLabel.collectAsState(initial = null)
    val add: (LocalDate?) -> Unit = remember(overlay, repo, editor, today) {
        { day -> overlay.showEditor(EditorRequest.New(day), today, repo, editor) }
    }
    return add.takeIf { addTo != null }
}
