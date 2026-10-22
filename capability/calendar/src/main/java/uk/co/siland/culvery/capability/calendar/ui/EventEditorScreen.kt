package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.ALL_DAY_LABEL
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.FULL_DATE
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.saveFailedBody
import uk.co.siland.culvery.capability.calendar.whoValue
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.KeepsToastsAbove
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.clockText

/** A save that failed: where it was going ([service]) and why ([problem]); the card says both. */
data class SaveFailure(val service: String, val problem: WriteProblem)

internal const val NEW_EVENT = "New event"
internal const val CHANGE_EVENT = "Change event"
internal const val DISCARD_NEW = "Discard this event?"
internal const val DISCARD_CHANGES = "Discard your changes?"
internal const val KEEP_EDITING = "Keep editing"
internal const val DISCARD = "Discard"
internal const val REPEATS_OPTIONAL_HINT = "Repeats are optional"
internal const val CLASH_HINT = "Clashes are allowed; both show on the week"
internal const val ALL_SET_HINT = "Everything’s set"

private const val CALENDAR_SUFFIX = " calendar"

/** "Saves to the Family calendar", from a calendar named "Family" or "Family calendar" (9 §3.1). */
fun savesToLine(name: String): String {
    val bare = if (name.endsWith(CALENDAR_SUFFIX, ignoreCase = true)) name.dropLast(CALENDAR_SUFFIX.length) else name
    return "Saves to the $bare$CALENDAR_SUFFIX"
}

/** A change throws away only the edits, where a bin would read as deleting the event (9 §3.1). */
internal fun discardIcon(mode: EditorForm.Mode): String = if (mode == EditorForm.Mode.New) Icons.DELETE else Icons.CLOSE

/**
 * The full-screen Add event and Change event (handoff §1 Layout): the top bar, the event so far on the left with the one
 * green button at its foot, and on the right the pane for the [flow]'s active row. Stateless apart from the [form] it
 * edits and whether the discard check shows. [savesTo] names the calendar the event goes to; [service] where a locked
 * span's dates change. While the title is focused and the keyboard is up the rows shrink and the foot goes (handoff 6a).
 * [keyboard] is the keyboard's inset, passed in by tests.
 */
@Composable
internal fun EventEditorScreen(
    form: EditorForm,
    flow: EditorFlow,
    members: List<Member>,
    context: String,
    savesTo: String?,
    service: String,
    busy: Boolean,
    failure: SaveFailure?,
    childOnly: PersonId?,
    childName: String?,
    held: Held?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRefusedWho: () -> Unit,
    keyboard: WindowInsets = WindowInsets.ime,
) {
    val c = Culvery.colors
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocus = remember { FocusRequester() }
    var titleFocused by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    val focusOnOpen = remember { form.mode == EditorForm.Mode.New && form.title.isEmpty() }
    LaunchedEffect(flow) { flow.runMoves() }
    LaunchedEffect(Unit) {
        if (focusOnOpen) {
            titleFocus.requestFocus()
            keyboardController?.show()
        }
    }
    val closeKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val typing = titleFocused && keyboard.getBottom(LocalDensity.current) > 0
    val isNew = form.mode == EditorForm.Mode.New
    val heading = if (isNew) NEW_EVENT else CHANGE_EVENT
    val household = remember(members) { EditorPeople(members) }
    val clash = !form.datesLocked && rememberClash(form, held, household.order)
    Box(
        Modifier
            .testTag("editor_screen")
            .semantics { paneTitle = heading }
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(keyboard),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(heading, context) {
                flow.stay()
                closeKeyboard()
                if (form.edited) discarding = true else onClose()
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(EditorDimens.columnGap),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = EditorDimens.sides, end = EditorDimens.sides, bottom = EditorDimens.bodyBottom),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(EditorDimens.rowGap),
                    modifier = Modifier.testTag("editor_column").width(EditorDimens.columnWidth).fillMaxHeight(),
                ) {
                    TitleField(
                        form = form,
                        focus = titleFocus,
                        focused = titleFocused,
                        readOnly = busy,
                        onFocus = { titleFocused = it },
                        onNext = {
                            closeKeyboard()
                            flow.open(EditorStep.Who)
                        },
                    )
                    // A save in flight closes on what it sent, so no other pane opens meanwhile.
                    Rows(
                        form, flow, household.people, service, typing, lit = !titleFocused, clash = clash,
                        onAllDay = { on ->
                            if (!busy) {
                                closeKeyboard()
                                form.chooseAllDay(on)
                                // The time panes go with the times.
                                when (flow.active) {
                                    EditorStep.StartTime -> flow.open(EditorStep.StartDate)
                                    EditorStep.EndTime -> flow.open(EditorStep.EndDate)
                                    else -> Unit
                                }
                            }
                        },
                    ) { step ->
                        if (!busy) {
                            closeKeyboard()
                            flow.open(step)
                        }
                    }
                    if (!typing) {
                        Spacer(Modifier.weight(1f))
                        KeepsToastsAbove {
                            if (discarding) {
                                HhConfirmCard(
                                    tag = "editor_discard",
                                    title = if (isNew) DISCARD_NEW else DISCARD_CHANGES,
                                    keep = KEEP_EDITING,
                                    keepTag = "editor_discard_keep",
                                    confirm = DISCARD,
                                    confirmTag = "editor_discard_confirm",
                                    busy = busy,
                                    onKeep = { discarding = false },
                                    onConfirm = onClose,
                                    confirmIcon = discardIcon(form.mode),
                                )
                            } else {
                                val hint = footHint(clash, isNew, form, flow)
                                Foot(form, savesTo, hint, busy, failure, onSave = {
                                    flow.stay()
                                    closeKeyboard()
                                    onSave()
                                }, onDelete = {
                                    flow.stay()
                                    closeKeyboard()
                                    onDelete()
                                })
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    EditorPaneFor(flow.active, form, flow, members, household, held, service, childOnly, childName, busy, onRefusedWho)
                }
            }
        }
    }
}

/** The foot's one hint, first match wins (9c §3.7): a clash; Repeats still unanswered on a new event that can be added; Check on a new event. */
private fun footHint(clash: Boolean, isNew: Boolean, form: EditorForm, flow: EditorFlow): String? = when {
    clash -> CLASH_HINT
    isNew && form.canSave && EditorStep.Repeats in flow.steps && !form.repeatsAnswered -> REPEATS_OPTIONAL_HINT
    isNew && flow.active == EditorStep.Check -> ALL_SET_HINT
    else -> null
}

/** Whether anyone chosen is busy over the form's When: the Clash tag on Starts and the foot's hint. */
@Composable
private fun rememberClash(form: EditorForm, held: Held?, order: List<PersonId>): Boolean {
    val except = (form.mode as? EditorForm.Mode.Edit)?.original?.ref
    return remember(held, form.eventWhen, form.who, except, form.zone, order) {
        held?.checkWhen(form.eventWhen, form.who, form.zone, except, order) is FreeCheck.Busy
    }
}

@Composable
private fun TopBar(heading: String, context: String, onClose: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.barGap),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EditorDimens.barHeight)
            .padding(horizontal = EditorDimens.sides),
    ) {
        HhCloseButton(onClick = onClose)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EditorDimens.contextTop)) {
            Text(heading, style = CalendarType.editorBarTitle, color = c.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
            Text(
                context,
                style = CalendarType.editorContext,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("editor_context"),
            )
        }
    }
}

/**
 * 75 dp, `surf`, 24 sp / 600, "What’s happening?"; focused, a 2 dp `accent` ring inside. The keyboard's Next moves on to
 * Who (9 §2 "the keyboard's Next moves to Who").
 */
@Composable
private fun TitleField(form: EditorForm, focus: FocusRequester, focused: Boolean, readOnly: Boolean, onFocus: (Boolean) -> Unit, onNext: () -> Unit) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(EditorDimens.fieldRadius)
    BasicTextField(
        value = form.title,
        onValueChange = { form.updateTitle(it) },
        singleLine = true,
        readOnly = readOnly,
        textStyle = CalendarType.editorField.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier
            .testTag("editor_title")
            .semantics { contentDescription = EVENT_NAME }
            .focusRequester(focus)
            .fillMaxWidth()
            .height(EditorDimens.fieldHeight)
            .onFocusChanged { onFocus(it.isFocused) },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(c.surf)
                    .then(if (focused) Modifier.border(EditorDimens.ring, c.accent, shape) else Modifier)
                    .padding(horizontal = EditorDimens.fieldPaddingH),
            ) {
                if (form.title.isEmpty()) Text(TITLE_PLACEHOLDER, style = CalendarType.editorField, color = c.mute, maxLines = 1)
                inner()
            }
        },
    )
}

private const val EVENT_NAME = "Event name"
private const val TITLE_PLACEHOLDER = "What’s happening?"
private const val WHO = "Who"
private const val STARTS = "Starts"
private const val ENDS = "Ends"
internal const val REPEATS = "Repeats"

/**
 * Who, All day, Starts, Ends and Repeats as [flow] has them (9c §3.1); with the keyboard up an unset Who reads as its
 * name and All day goes. While the title has focus no row is [lit] (handoff 6a). A locked span's All day, Starts and Ends
 * each say where its dates change. The Clash tag sits on Starts.
 */
@Composable
private fun Rows(
    form: EditorForm,
    flow: EditorFlow,
    people: List<Person>,
    service: String,
    typing: Boolean,
    lit: Boolean,
    clash: Boolean,
    onAllDay: (Boolean) -> Unit,
    onOpen: (EditorStep) -> Unit,
) {
    val editing = form.mode is EditorForm.Mode.Edit
    val active = { step: EditorStep -> lit && flow.active == step }
    EditorRow(
        testTag = "editor_row_who",
        icon = Icons.GROUP,
        label = WHO,
        value = if (typing && form.who.isEmpty()) WHO else whoValue(form, people),
        active = active(EditorStep.Who),
        set = form.who.isNotEmpty(),
        typing = typing,
        onClick = { onOpen(EditorStep.Who) },
    )
    val w = form.eventWhen
    if (form.datesLocked) {
        val dates = form.lockedDatesLabel(service).orEmpty()
        if (!typing) EditorRow("editor_all_day", Icons.SCHEDULE, ALL_DAY_LABEL, dates, active = false, set = true, typing = false, onClick = null)
        EditorWhenRow("editor_starts", Icons.CALENDAR_MONTH, STARTS, dates, null, null, active = false, set = true, typing = typing, onDate = null, onTime = null, locked = true)
        EditorWhenRow("editor_ends", Icons.EVENT_AVAILABLE, ENDS, dates, null, null, active = false, set = true, typing = typing, onDate = null, onTime = null, locked = true)
    } else {
        if (!typing) HhSwitchRow(ALL_DAY_LABEL, null, w.allDay, "editor_all_day", onAllDay)
        val start = w.startDate
        EditorWhenRow(
            testTag = "editor_starts",
            icon = Icons.CALENDAR_MONTH,
            label = STARTS,
            date = start?.format(SHORT_DAY),
            time = clockText(w.startClock).takeIf { start != null && !w.allDay },
            spokenDate = start?.format(FULL_DATE),
            active = active(EditorStep.StartDate) || active(EditorStep.StartTime),
            set = start != null,
            typing = typing,
            onDate = { onOpen(EditorStep.StartDate) },
            onTime = { onOpen(EditorStep.StartTime) },
            tag = startsTag(clash = if (clash) clashTag() else null, copied = null),
        )
        val end = w.endDate
        EditorWhenRow(
            testTag = "editor_ends",
            icon = Icons.EVENT_AVAILABLE,
            label = ENDS,
            date = end?.format(SHORT_DAY),
            time = clockText(w.endClock).takeIf { end != null && !w.allDay },
            spokenDate = end?.format(FULL_DATE),
            active = active(EditorStep.EndDate) || active(EditorStep.EndTime),
            set = end != null,
            typing = typing,
            onDate = if (end != null) ({ onOpen(EditorStep.EndDate) }) else null,
            onTime = { onOpen(EditorStep.EndTime) },
        )
    }
    if (EditorStep.Repeats in flow.steps) {
        EditorRow(
            testTag = "editor_row_repeats",
            icon = Icons.REPEAT,
            label = REPEATS,
            value = form.repeatsValue,
            active = active(EditorStep.Repeats),
            set = editing || form.repeat != RepeatChoice.Never,
            typing = typing,
            onClick = { onOpen(EditorStep.Repeats) },
            tag = if (form.repeatTooLong) tooLongTag() else null,
        )
    }
}

/** Where it saves, a failed save's card, and Delete beside the one green button with what it waits for under it. */
@Composable
private fun Foot(form: EditorForm, savesTo: String?, hint: String?, busy: Boolean, failure: SaveFailure?, onSave: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.footGap)) {
        if (failure != null) FailureCard(failure)
        if (savesTo != null) SavesTo(savesToLine(savesTo))
        Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.footGap), modifier = Modifier.fillMaxWidth()) {
            if (form.mode is EditorForm.Mode.Edit) DeleteButton(enabled = !busy, busy = busy, tag = "editor_delete", onClick = onDelete)
            HhFooterButton(
                text = when {
                    failure != null -> "Try again"
                    form.mode == EditorForm.Mode.New -> "Add event"
                    else -> "Save changes"
                },
                onClick = onSave,
                tag = "editor_save",
                modifier = Modifier.weight(1f),
                icon = if (failure != null) Icons.REFRESH else Icons.CHECK,
                enabled = form.canSave && !busy,
                reason = form.reason,
                below = true,
                hint = hint,
                busy = busy,
            )
        }
    }
}

@Composable
private fun SavesTo(line: String) {
    val mute = Culvery.colors.mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.footIconGap),
        modifier = Modifier
            .testTag("editor_saves_to")
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth()
            .padding(horizontal = EditorDimens.footLinePaddingH),
    ) {
        HhIcon(Icons.EVENT_NOTE, size = EditorDimens.footIcon, tint = mute, modifier = Modifier.testTag("editor_saves_to_icon"))
        Text(line, style = CalendarType.footLine, color = mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Hand-off 14: the save didn't happen; the details are still in the editor, and the body says what to do next. */
@Composable
internal fun FailureCard(failure: SaveFailure) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorCardIconGap),
        modifier = Modifier
            .testTag("editor_failure")
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.editorCardRadius))
            .background(c.dangerSoft)
            .padding(horizontal = CalendarDimens.editorCardPaddingH, vertical = CalendarDimens.editorCardPaddingV),
    ) {
        HhIcon(Icons.CLOUD_OFF, size = CalendarDimens.failureIcon, tint = c.danger)
        Column {
            // A long provider reason must not push Save off screen; the body says what to do.
            Text(couldNotSave(failure.service, failure.problem), style = CalendarType.noteTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.failureBodyTop))
            Text(saveFailedBody(failure.service, failure.problem), style = CalendarType.noteBody, color = c.mute)
        }
    }
}
