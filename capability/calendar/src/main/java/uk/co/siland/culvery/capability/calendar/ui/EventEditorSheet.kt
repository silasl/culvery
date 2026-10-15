package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.HOURS_MINUTES
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.lengthLabel
import uk.co.siland.culvery.capability.calendar.saveFailedBody
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.PersonChipInk
import uk.co.siland.culvery.core.ui.ShellTokens

/** A save that failed: where it was going ([service]) and why ([problem]); the card says both. */
data class SaveFailure(val service: String, val problem: WriteProblem)

internal const val REPEATS = "Repeats"
internal const val ENDS = "Ends"
internal const val ENDS_TOO_EARLY = "The end date is before the event’s day. Pick a later one."

/**
 * Hand-off §7 "Sheet 2 — Quick-add / edit" (2b-2 design §4.2). Stateless apart from the [form] it edits: the host
 * decides [busy], [failure] and which [picker] shows. [childOnly] is a signed-in Child's own id, in a new event or an
 * edit (§6): every other Who chip is drawn disabled and a tap on one calls [onRefusedWho]. The pickers draw inside
 * the sheet's box. Save, ✕, Done and opening a picker close the keyboard; tapping a chip doesn't. Everything but the
 * background sits above [keyboard]. While [busy], the form can't change: a save in flight would close the sheet on
 * edits it never sent.
 */
@Composable
fun EventEditorSheet(
    form: EventForm,
    people: List<Person>,
    service: String,
    childOnly: PersonId?,
    busy: Boolean,
    failure: SaveFailure?,
    picker: EditorPicker,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRefusedWho: () -> Unit,
    onPicker: (EditorPicker) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: WindowInsets = WindowInsets.ime,
    focusTitleOnOpen: Boolean = form.mode == EventForm.Mode.New,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocus = remember { FocusRequester() }
    if (focusTitleOnOpen) {
        LaunchedEffect(Unit) {
            titleFocus.requestFocus()
            keyboardController?.show()
        }
    }
    val closeKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val everyone = remember(people) { listOf(Person.Family) + people }
    val title = if (form.mode == EventForm.Mode.New) "Add event" else "Change event"
    Box(modifier.testTag("editor_sheet").semantics { paneTitle = title }.width(ShellTokens.sheetWidth).fillMaxHeight()) {
        HhSheet(
            modifier = if (picker != EditorPicker.None) Modifier.clearAndSetSemantics {} else Modifier,
            padding = PaddingValues(
                start = CalendarDimens.sheetPaddingH,
                end = CalendarDimens.sheetPaddingH,
                top = CalendarDimens.editorPaddingTop,
                bottom = CalendarDimens.editorPaddingBottom,
            ),
            keyboard = keyboard,
        ) {
            // With the keyboard up (about 320 dp) the sheet's content is about 480 dp tall: the header, Title and
            // footer stay put and the chips scroll (2b-2 design §4.4). The background still runs behind the keyboard.
            Column(
                verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                Header(title, form, everyone) {
                    closeKeyboard()
                    onClose()
                }
                TitleField(form, titleFocus, readOnly = busy, onDone = closeKeyboard)
                Column(
                    verticalArrangement = Arrangement.spacedBy(CalendarDimens.editorBodyGap),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = CalendarDimens.editorBodyTop),
                ) {
                    WhoSection(form, everyone, childOnly, busy, onRefusedWho)
                    if (form.datesLocked) {
                        LockedDates(form.lockedDatesLabel(service).orEmpty())
                    } else {
                        DaySection(form, busy) {
                            closeKeyboard()
                            onPicker(EditorPicker.Date)
                        }
                        TimeSection(form, busy) {
                            closeKeyboard()
                            onPicker(EditorPicker.Time)
                        }
                        if (form.time != TimeChoice.AllDay) LengthSection(form, busy)
                        if (form.canRepeat) {
                            RepeatsSection(form, busy)
                            if (form.repeat != RepeatChoice.Never) {
                                EndsSection(form, busy) {
                                    closeKeyboard()
                                    onPicker(EditorPicker.EndDate)
                                }
                            }
                        }
                    }
                }
                if (failure != null) FailureCard(failure)
                Footer(
                    form = form,
                    busy = busy,
                    failed = failure != null,
                    onSave = {
                        closeKeyboard()
                        onSave()
                    },
                    onDelete = {
                        closeKeyboard()
                        onDelete()
                    },
                )
            }
        }
        when (picker) {
            EditorPicker.None -> Unit
            EditorPicker.Date -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                DatePickerCard(
                    today = form.today,
                    selected = form.day,
                    onPick = {
                        form.chooseDay(it)
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
            EditorPicker.Time -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                TimePickerCard(
                    initial = form.pickerTime,
                    onSet = {
                        form.chooseTime(TimeChoice.Custom(it))
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
            EditorPicker.EndDate -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                DatePickerCard(
                    today = form.today,
                    selected = form.endsOn ?: form.day,
                    onPick = {
                        form.chooseEnd(it)
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, form: EventForm, everyone: List<Person>, onClose: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorHeaderGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CalendarType.editorTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(CalendarDimens.editorSummaryTop))
            Text(
                // A tag whose person has left has no name here, and the line ends at the time.
                form.summary { id -> everyone.firstOrNull { it.id == id }?.name },
                style = CalendarType.editorSummary,
                color = c.mute,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("editor_summary"),
            )
        }
        HhCloseButton(onClick = onClose)
    }
}

/** 64 dp, `surf`, 22 sp / 600, "What’s happening?", one line; Done closes the keyboard and never saves. */
@Composable
private fun TitleField(form: EventForm, focus: FocusRequester, readOnly: Boolean, onDone: () -> Unit) {
    HhTextField(
        value = form.title,
        onValueChange = { form.updateTitle(it) },
        placeholder = "What’s happening?",
        label = "Event name",
        tag = "editor_title",
        modifier = Modifier.focusRequester(focus),
        readOnly = readOnly,
        onDone = onDone,
    )
}

@Composable
private fun Section(label: String, modifier: Modifier = Modifier, chips: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        HhSectionLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
            verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
            modifier = Modifier.selectableGroup(),
        ) {
            chips()
        }
    }
}

@Composable
private fun WhoSection(form: EventForm, everyone: List<Person>, childOnly: PersonId?, busy: Boolean, onRefusedWho: () -> Unit) {
    Section("Who") {
        everyone.forEach { person ->
            val selected = form.who == person.id
            val enabled = childOnly == null || person.id == childOnly
            val colour = Color(person.color)
            HhChoiceChip(
                label = person.name,
                selected = selected,
                tag = "who_${person.name}",
                enabled = enabled,
                selectedColor = colour,
                selectedInk = PersonChipInk,
                leading = { ink ->
                    if (selected) {
                        HhIcon(Icons.CHECK, size = ControlTokens.chipIcon, tint = ink)
                    } else {
                        Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(colour))
                    }
                },
                // The chosen chip may be a disabled one (an adult tagged the child's event); a tap on it does nothing.
                onClick = {
                    when {
                        busy -> Unit
                        enabled || selected -> form.chooseWho(person.id)
                        else -> onRefusedWho()
                    }
                },
            )
        }
    }
}

@Composable
private fun DaySection(form: EventForm, busy: Boolean, onPickDate: () -> Unit) {
    Section("Day") {
        form.dayChoices.forEachIndexed { i, date ->
            HhChoiceChip(
                label = when (i) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> date.format(WEEKDAY)
                },
                selected = form.day == date,
                tag = "day_$date",
                onClick = { if (!busy) form.chooseDay(date) },
            )
        }
        val picked = form.pickedDateLabel
        HhChoiceChip(
            label = picked ?: "Pick date…",
            selected = picked != null,
            tag = "day_pick",
            role = Role.Button,
            leading = { ink -> HhIcon(Icons.CALENDAR_MONTH, size = ControlTokens.chipIcon, tint = ink) },
            onClick = { if (!busy) onPickDate() },
        )
    }
}

@Composable
private fun TimeSection(form: EventForm, busy: Boolean, onPickTime: () -> Unit) {
    Section("Time") {
        HhChoiceChip("All day", form.time == TimeChoice.AllDay, "time_all_day", onClick = { if (!busy) form.chooseTime(TimeChoice.AllDay) })
        val custom = form.time as? TimeChoice.Custom
        HhChoiceChip(
            label = custom?.time?.format(HOURS_MINUTES) ?: "Pick time…",
            selected = custom != null,
            tag = "time_pick",
            role = Role.Button,
            leading = { ink -> HhIcon(Icons.SCHEDULE, size = ControlTokens.chipIcon, tint = ink) },
            onClick = { if (!busy) onPickTime() },
        )
    }
}

@Composable
private fun LengthSection(form: EventForm, busy: Boolean) {
    Section("Length") {
        form.lengths.forEach { length ->
            HhChoiceChip(lengthLabel(length), form.length == length, "length_${length.toMinutes()}", onClick = { if (!busy) form.chooseLength(length) })
        }
    }
}

@Composable
private fun RepeatsSection(form: EventForm, busy: Boolean) {
    Section(REPEATS) {
        RepeatChoice.entries.forEach { choice ->
            HhChoiceChip(choice.label, form.repeat == choice, "repeat_${choice.name}", onClick = { if (!busy) form.chooseRepeat(choice) })
        }
    }
}

/** Never, or a date from the date picker; a line under them while the date is before the event's day. */
@Composable
private fun EndsSection(form: EventForm, busy: Boolean, onPickEnd: () -> Unit) {
    val endsInView = remember { BringIntoViewRequester() }
    val errorInView = remember { BringIntoViewRequester() }
    // Ends appears below the fold when a repeat is chosen, and so can its error line.
    LaunchedEffect(Unit) { endsInView.bringIntoView() }
    LaunchedEffect(form.endsBeforeStart) { if (form.endsBeforeStart) errorInView.bringIntoView() }
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        Section(ENDS, Modifier.bringIntoViewRequester(endsInView)) {
            HhChoiceChip("Never", form.endsOn == null, "ends_never", onClick = { if (!busy) form.chooseEnd(null) })
            val end = form.endsOn
            HhChoiceChip(
                label = end?.format(SHORT_DAY) ?: "On a date…",
                selected = end != null,
                tag = "ends_pick",
                role = Role.Button,
                leading = { ink -> HhIcon(Icons.CALENDAR_MONTH, size = ControlTokens.chipIcon, tint = ink) },
                onClick = { if (!busy) onPickEnd() },
            )
        }
        Box(Modifier.bringIntoViewRequester(errorInView)) {
            HhMessageLine(ENDS_TOO_EARLY.takeIf { form.endsBeforeStart }, CalendarType.noteBody, Culvery.colors.danger, "ends_error")
        }
    }
}

/** 2b-2 design D3: a multi-day event's dates can't change here, so Day, Time and Length become this one line. */
@Composable
private fun LockedDates(label: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorCardIconGap),
        modifier = Modifier
            .testTag("editor_locked_dates")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.editorCardRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.editorCardPaddingH, vertical = CalendarDimens.editorCardPaddingV),
    ) {
        HhIcon(Icons.DATE_RANGE, size = CalendarDimens.lockedIcon, tint = c.mute)
        Text(label, style = CalendarType.lockedDates, color = c.ink)
    }
}

/** Hand-off 14: the save didn't happen; the details are still in the sheet, and the body says what to do next. */
@Composable
private fun FailureCard(failure: SaveFailure) {
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
            // A long provider reason must not push Save off screen with the keyboard up; the body says what to do.
            Text(couldNotSave(failure.service, failure.problem), style = CalendarType.noteTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.failureBodyTop))
            Text(saveFailedBody(failure.service, failure.problem), style = CalendarType.noteBody, color = c.mute)
        }
    }
}

@Composable
private fun Footer(form: EventForm, busy: Boolean, failed: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
        if (form.mode is EventForm.Mode.Edit) DeleteButton(enabled = !busy, tag = "editor_delete", onClick = onDelete)
        HhButton(
            text = when {
                failed -> "Try again"
                form.mode == EventForm.Mode.New -> "Add event"
                else -> "Save changes"
            },
            onClick = onSave,
            modifier = Modifier.weight(1f).testTag("editor_save"),
            tone = ButtonTone.Primary,
            size = ButtonSize.Large,
            icon = if (failed) Icons.REFRESH else Icons.CHECK,
            enabled = form.canSave && !busy,
            on = ButtonSurface.Bg,
        )
    }
}
