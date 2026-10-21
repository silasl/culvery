package uk.co.siland.culvery.capability.calendar.ui

import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.WeekFocus
import uk.co.siland.culvery.core.plugin.OverlayHost

/**
 * The detail sheet and the editor swap through the one overlay (2b-2 design D4): the detail sheet's Change, or its For
 * row, shows the editor full screen, and the editor's Delete, once authorised, shows the detail sheet asking to
 * confirm. Both declare Unit: each calls the other, so neither type can be inferred.
 */
internal fun OverlayHost.showDetail(
    ref: EventRef,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    weekFocus: WeekFocus,
    from: EditorFrom,
    mode: DetailMode = DetailMode.Idle,
): Unit = show {
    EventDetailHost(
        ref = ref,
        today = today,
        repo = repo,
        editor = editor,
        onClose = { dismiss() },
        initialMode = mode,
        onEdit = { showEditor(EditorRequest.Edit(ref, from), today, repo, editor, weekFocus) },
        onChangeWho = { showEditor(EditorRequest.Edit(ref, from, start = EditorStep.Who), today, repo, editor, weekFocus) },
    )
}

internal fun OverlayHost.showEditor(
    request: EditorRequest,
    today: LocalDate,
    repo: CalendarRepository,
    editor: CalendarEditor,
    weekFocus: WeekFocus,
): Unit = showFullScreen {
    EventEditorHost(
        request = request,
        repo = repo,
        editor = editor,
        weekFocus = weekFocus,
        onClose = { dismiss() },
        onDeleteAuthorised = { ref -> showDetail(ref, today, repo, editor, weekFocus, request.from, DetailMode.ConfirmingDelete) },
        onEdited = ::markEdited,
    )
}
