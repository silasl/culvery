package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EditResult
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "EventDetailHost"

/** Wraps a loaded detail so "still loading" (null) differs from "gone" (Loaded(null)). */
private class Loaded(val detail: EventDetailUi?)

/**
 * The detail sheet for [ref], kept live from the repository: it closes itself when the event disappears.
 * Only one action runs at a time; while one runs, further taps are ignored, and one that fails before the editor's
 * write (the tablet's store) is logged rather than crashing the app. The editor toasts each outcome; the host only
 * decides whether the sheet stays open. [initialMode] is ConfirmingDelete when the editor's Delete handed over
 * (2b-2 design D4); [onEdit] swaps to the add/edit sheet.
 */
@Composable
internal fun EventDetailHost(
    ref: EventRef,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    onClose: () -> Unit,
    initialMode: DetailMode = DetailMode.Idle,
    onEdit: () -> Unit,
) {
    val loaded: Loaded? by remember(ref, today) { repo.event(ref, today).map { Loaded(it) } }.collectAsState(initial = null)
    val people: List<Person> by repo.people.collectAsState(initial = emptyList())
    var mode by remember(ref, initialMode) { mutableStateOf(initialMode) }
    val action = rememberSingleAction(ref) { e -> Log.w(TAG, "Couldn't change an event (${e::class.simpleName})") }

    val state = loaded ?: return
    val detail = state.detail
    if (detail == null) {
        LaunchedEffect(ref) { onClose() }
        return
    }

    EventDetailSheet(
        detail = detail,
        people = people,
        mode = mode,
        busy = action.busy,
        onClose = onClose,
        onDelete = { action.run { if (editor.mayDelete(ref)) mode = DetailMode.ConfirmingDelete } },
        onKeep = { mode = DetailMode.Idle },
        onConfirmDelete = {
            action.run {
                when (editor.delete(ref)) {
                    EditResult.Done, EditResult.Queued -> onClose()
                    is EditResult.Rejected -> mode = DetailMode.Idle
                    EditResult.Cancelled -> Unit
                    EditResult.NotEditable -> onClose()
                }
            }
        },
        onChoosePerson = { mode = DetailMode.ChoosingPerson },
        onAssign = { person ->
            action.run {
                when (editor.assign(ref, person.id)) {
                    EditResult.Done, EditResult.Queued -> mode = DetailMode.Idle
                    is EditResult.Rejected, EditResult.Cancelled -> Unit
                    EditResult.NotEditable -> onClose()
                }
            }
        },
        onEdit = onEdit,
    )
}

/** Opens an event's detail sheet through the shell's overlay host; its Edit swaps to the add/edit sheet. */
@Composable
internal fun rememberEventOpener(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate): (EventRef) -> Unit {
    val overlay = LocalOverlayHost.current
    return remember(overlay, repo, editor, today) {
        { ref -> overlay.showDetail(ref, today, repo, editor) }
    }
}
