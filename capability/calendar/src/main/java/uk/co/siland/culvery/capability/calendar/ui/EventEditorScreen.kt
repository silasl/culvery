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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.FreeCheck
import uk.co.siland.culvery.capability.calendar.Held
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.dayValue
import uk.co.siland.culvery.capability.calendar.saveFailedBody
import uk.co.siland.culvery.capability.calendar.timeValue
import uk.co.siland.culvery.capability.calendar.whoValue
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons

/** A save that failed: where it was going ([service]) and why ([problem]); the card says both. */
data class SaveFailure(val service: String, val problem: WriteProblem)

internal const val NEW_EVENT = "New event"
internal const val CHANGE_EVENT = "Change event"
internal const val DISCARD_NEW = "Discard this event?"
internal const val DISCARD_CHANGES = "Discard your changes?"
internal const val KEEP_EDITING = "Keep editing"
internal const val DISCARD = "Discard"
internal const val OPTIONAL_HINT = "Time and repeats are optional"
internal const val TIME_OPTIONAL_HINT = "Time is optional"
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
 * green button at its foot, and on the right the [pane] for the [flow]'s active row. Stateless apart from the [form] it
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
    picker: EditorPicker,
    childOnly: PersonId?,
    childName: String?,
    held: Held?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRefusedWho: () -> Unit,
    onPicker: (EditorPicker) -> Unit,
    keyboard: WindowInsets = WindowInsets.ime,
    pane: @Composable (EditorStep) -> Unit = { EditorPaneFor(it, form, flow, members, held, service, childOnly, childName, busy, onRefusedWho, onPicker) },
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
    val order = remember(members) { members.map { it.person.id } }
    val clash = EditorStep.Time in flow.steps && rememberTimeCheck(form, held, order).result is FreeCheck.Busy
    Box(
        Modifier
            .testTag("editor_screen")
            .semantics { paneTitle = heading }
            .fillMaxSize()
            .background(c.bg)
            .windowInsetsPadding(keyboard),
    ) {
        // Under a picker only the picker is heard.
        Column(Modifier.fillMaxSize().then(if (picker != EditorPicker.None) Modifier.clearAndSetSemantics {} else Modifier)) {
            TopBar(heading, context) {
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
                        readOnly = busy,
                        onFocus = { titleFocused = it },
                        onNext = {
                            closeKeyboard()
                            flow.open(EditorStep.Who)
                        },
                    )
                    Rows(form, flow, members, service, typing, lit = !titleFocused, clash = clash) { step ->
                        closeKeyboard()
                        flow.open(step)
                    }
                    if (!typing) {
                        Spacer(Modifier.weight(1f))
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
                            val hint = when {
                                clash -> CLASH_HINT
                                flow.active == EditorStep.Check -> ALL_SET_HINT
                                EditorStep.Repeats in flow.steps -> OPTIONAL_HINT
                                else -> TIME_OPTIONAL_HINT
                            }
                            Foot(form, savesTo, hint, busy, failure, onSave = {
                                closeKeyboard()
                                onSave()
                            }, onDelete = {
                                closeKeyboard()
                                onDelete()
                            })
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { pane(flow.active) }
            }
        }
        TimePicker(form, flow, picker, onPicker)
    }
}

/** The start picker for an hour outside the chips; the end picker for Until…, which moves on unless the end is refused. */
@Composable
private fun TimePicker(form: EditorForm, flow: EditorFlow, picker: EditorPicker, onPicker: (EditorPicker) -> Unit) {
    val close = { onPicker(EditorPicker.None) }
    when (picker) {
        EditorPicker.StartTime -> PickerLayer(onDismiss = close) {
            TimePickerCard(form.pickerTime, onSet = {
                form.chooseTime(it)
                close()
            }, onCancel = close)
        }
        EditorPicker.EndTime -> PickerLayer(onDismiss = close) {
            TimePickerCard(form.endPickerTime, onSet = {
                form.chooseEndTime(it)
                close()
                if (form.endError == null) flow.moveOnSoon()
            }, onCancel = close)
        }
        else -> Unit
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
private fun TitleField(form: EditorForm, focus: FocusRequester, readOnly: Boolean, onFocus: (Boolean) -> Unit, onNext: () -> Unit) {
    val c = Culvery.colors
    var focused by remember { mutableStateOf(false) }
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
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
            },
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
private const val DAY = "Day"
private const val TIME = "Time"
internal const val REPEATS = "Repeats"

/**
 * Who, Day, Time and Repeats as [flow] has them; with the keyboard up an unset Who and Day read as their names. While the
 * title has focus no row is [lit] (handoff 6a). On Change every row is answered, All day and Doesn’t repeat included.
 */
@Composable
private fun Rows(
    form: EditorForm,
    flow: EditorFlow,
    members: List<Member>,
    service: String,
    typing: Boolean,
    lit: Boolean,
    clash: Boolean,
    onOpen: (EditorStep) -> Unit,
) {
    val people = members.map { it.person }
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
    if (form.datesLocked) {
        EditorRow(
            testTag = "editor_row_day",
            icon = Icons.CALENDAR_MONTH,
            label = DAY,
            value = form.lockedDatesLabel(service).orEmpty(),
            active = false,
            set = true,
            typing = typing,
            onClick = null,
        )
    } else {
        EditorRow(
            testTag = "editor_row_day",
            icon = Icons.CALENDAR_MONTH,
            label = DAY,
            value = if (typing && form.day == null) DAY else dayValue(form),
            active = active(EditorStep.Day),
            set = form.day != null,
            typing = typing,
            onClick = { onOpen(EditorStep.Day) },
        )
    }
    if (EditorStep.Time in flow.steps) {
        EditorRow(
            testTag = "editor_row_time",
            icon = Icons.SCHEDULE,
            label = TIME,
            value = timeValue(form),
            active = active(EditorStep.Time),
            set = editing || form.startTime != null,
            typing = typing,
            onClick = { onOpen(EditorStep.Time) },
            tag = if (clash) clashTag() else null,
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
        )
    }
}

/** Where it saves, a failed save's card, and Delete beside the one green button with what it waits for under it. */
@Composable
private fun Foot(form: EditorForm, savesTo: String?, hint: String, busy: Boolean, failure: SaveFailure?, onSave: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.footGap)) {
        if (failure != null) FailureCard(failure)
        if (savesTo != null) SavesTo(savesToLine(savesTo))
        Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.footGap), modifier = Modifier.fillMaxWidth()) {
            if (form.mode is EditorForm.Mode.Edit) DeleteButton(enabled = !busy, tag = "editor_delete", onClick = onDelete)
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
