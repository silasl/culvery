package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.core.plugin.OverlayHost

/**
 * The calendar's two sheets swap through the one overlay (2b-2 design D4): the detail sheet's Edit shows the
 * editor, and the editor's Delete, once authorised, shows the detail sheet asking to confirm. ✕ or the scrim closes
 * whichever is showing. Both declare Unit: each calls the other, so neither type can be inferred.
 */
internal fun OverlayHost.showDetail(
    ref: EventRef,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    mode: DetailMode = DetailMode.Idle,
): Unit = show {
    EventDetailHost(
        ref = ref,
        today = today,
        repo = repo,
        editor = editor,
        onClose = { dismiss() },
        initialMode = mode,
        onEdit = { showEditor(EditorRequest.Edit(ref), today, repo, editor) },
    )
}

internal fun OverlayHost.showEditor(request: EditorRequest, today: LocalDate, repo: CalendarRepository, editor: CalendarEditor): Unit = show {
    EventEditorHost(
        request = request,
        repo = repo,
        editor = editor,
        onClose = { dismiss() },
        onDeleteAuthorised = { ref -> showDetail(ref, today, repo, editor, DetailMode.ConfirmingDelete) },
    )
}

/**
 * One action at a time for a sheet: while one runs, [busy] is true and further taps are ignored. An exception the
 * action lets through (the tablet's own store failing before the editor's write) goes to [onError] instead of
 * crashing the app; the sheet closing mid-action cancels it, which is not an error.
 */
internal class SingleAction(private val scope: CoroutineScope, private val onError: (Exception) -> Unit) {
    var busy by mutableStateOf(false)
        private set

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            } finally {
                busy = false
            }
        }
    }
}

/** A [SingleAction] for one opening of a sheet: a new [key] (another event or request) starts a fresh one. */
@Composable
internal fun rememberSingleAction(key: Any?, onError: (Exception) -> Unit): SingleAction {
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    return remember(key) { SingleAction(scope) { currentOnError(it) } }
}
