package uk.co.siland.culvery.capability.calendar.ui

import java.time.LocalDate
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
