package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.HOURS_MINUTES
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.TimeSlot
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.lengthLabel
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.ShellTokens

/** Hand-off §7: the failure card's body. */
const val SAVE_FAILED_BODY = "Nothing was changed. Your details are still here — check the connection and try again."

/** A selected Who chip's ink on the person's colour: #0E1011 in both themes (hand-off §7), the dark theme's `bg`. */
private val PersonChipInk: Color = DarkColors.bg

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
    childOnly: PersonId?,
    busy: Boolean,
    failure: String?,
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
    Box(modifier.testTag("editor_sheet").width(ShellTokens.sheetWidth).fillMaxHeight()) {
        HhSheet(
            padding = PaddingValues(
                start = CalendarDimens.sheetPaddingH,
                end = CalendarDimens.sheetPaddingH,
                top = CalendarDimens.editorPaddingTop,
                bottom = CalendarDimens.editorPaddingBottom,
            ),
        ) {
            // With the keyboard up (about 320 dp) the sheet's content is about 480 dp tall: the header, Title and
            // footer stay put and the chips scroll (2b-2 design §4.4). The background still runs behind the keyboard.
            Column(
                verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom)),
            ) {
                Header(form, everyone) {
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
                        LockedDates(form.lockedDatesLabel.orEmpty())
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
        }
    }
}

@Composable
private fun Header(form: EventForm, everyone: List<Person>, onClose: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorHeaderGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (form.mode == EventForm.Mode.New) "New event" else "Edit event", style = CalendarType.editorTitle, color = c.ink)
            Spacer(Modifier.height(CalendarDimens.editorSummaryTop))
            Text(
                // A tag whose person has left has no name here, and the line ends at the time.
                form.summary { id -> everyone.firstOrNull { it.id == id }?.name },
                style = CalendarType.editorSummary,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("editor_summary"),
            )
        }
        HhCloseButton(onClick = onClose)
    }
}

/** 64 dp, `surf`, 22 sp / 600, "What's happening?", one line; Done closes the keyboard and never saves. */
@Composable
private fun TitleField(form: EventForm, focus: FocusRequester, readOnly: Boolean, onDone: () -> Unit) {
    val c = Culvery.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CalendarDimens.titleRadius)
    BasicTextField(
        value = form.title,
        onValueChange = { form.updateTitle(it) },
        singleLine = true,
        readOnly = readOnly,
        textStyle = CalendarType.titleField.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .testTag("editor_title")
            .fillMaxWidth()
            .height(CalendarDimens.titleHeight)
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(c.surf)
                    .then(if (focused) Modifier.border(CalendarDimens.titleBorder, c.accent, shape) else Modifier)
                    .padding(horizontal = CalendarDimens.titlePaddingH),
            ) {
                if (form.title.isEmpty()) Text("What's happening?", style = CalendarType.titleField, color = c.mute, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun Section(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        Text(label, style = CalendarType.sectionLabel, color = Culvery.colors.mute)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.choiceChipGap),
            verticalArrangement = Arrangement.spacedBy(CalendarDimens.choiceChipGap),
        ) {
            chips()
        }
    }
}

@Composable
private fun WhoSection(form: EventForm, everyone: List<Person>, childOnly: PersonId?, busy: Boolean, onRefusedWho: () -> Unit) {
    Section("WHO") {
        everyone.forEach { person ->
            val selected = form.who == person.id
            val enabled = childOnly == null || person.id == childOnly
            val colour = Color(person.color)
            ChoiceChip(
                label = person.name,
                selected = selected,
                tag = "who_${person.name}",
                enabled = enabled,
                selectedColor = colour,
                selectedInk = PersonChipInk,
                leading = { ink ->
                    if (selected) {
                        HhIcon("check", size = CalendarDimens.choiceChipIcon, tint = ink)
                    } else {
                        Box(Modifier.size(CalendarDimens.choiceChipDot).clip(CircleShape).background(colour))
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
    Section("DAY") {
        form.dayChoices.forEachIndexed { i, date ->
            ChoiceChip(
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
        ChoiceChip(
            label = picked ?: "Pick date…",
            selected = picked != null,
            tag = "day_pick",
            leading = { ink -> HhIcon("calendar_month", size = CalendarDimens.choiceChipIcon, tint = ink) },
            onClick = { if (!busy) onPickDate() },
        )
    }
}

@Composable
private fun TimeSection(form: EventForm, busy: Boolean, onPickTime: () -> Unit) {
    Section("TIME") {
        ChoiceChip("All day", form.time == TimeChoice.AllDay, "time_all_day", onClick = { if (!busy) form.chooseTime(TimeChoice.AllDay) })
        TimeSlot.entries.forEach { slot ->
            ChoiceChip(
                label = slot.label,
                selected = form.time == TimeChoice.Slot(slot),
                tag = "time_${slot.name}",
                secondary = slot.time.format(HOURS_MINUTES),
                onClick = { if (!busy) form.chooseTime(TimeChoice.Slot(slot)) },
            )
        }
        val custom = form.time as? TimeChoice.Custom
        ChoiceChip(
            label = custom?.time?.format(HOURS_MINUTES) ?: "Pick time…",
            selected = custom != null,
            tag = "time_pick",
            leading = { ink -> HhIcon("schedule", size = CalendarDimens.choiceChipIcon, tint = ink) },
            onClick = { if (!busy) onPickTime() },
        )
    }
}

@Composable
private fun LengthSection(form: EventForm, busy: Boolean) {
    Section("LENGTH") {
        form.lengths.forEach { length ->
            ChoiceChip(lengthLabel(length), form.length == length, "length_${length.toMinutes()}", onClick = { if (!busy) form.chooseLength(length) })
        }
    }
}

/**
 * Hand-off §7 chip: 48 dp, padding 0 18, radius 24, 16 sp / 600; `surf`/`ink`, or [selectedColor]/[selectedInk] when
 * selected. A disabled chip is drawn at 38% but stays tappable, so a tap can explain why.
 */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selectedColor: Color = Culvery.colors.accent,
    selectedInk: Color = Culvery.colors.accentInk,
    secondary: String? = null,
    leading: (@Composable (ink: Color) -> Unit)? = null,
) {
    val c = Culvery.colors
    val ink = if (selected) selectedInk else c.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.choiceChipIconGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected }
            .alpha(if (enabled) 1f else CalendarDimens.DISABLED_CHIP_ALPHA)
            .height(CalendarDimens.choiceChipHeight)
            .clip(RoundedCornerShape(CalendarDimens.choiceChipRadius))
            .background(if (selected) selectedColor else c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.choiceChipPaddingH),
    ) {
        leading?.invoke(ink)
        Text(label, style = CalendarType.chip, color = ink, maxLines = 1)
        if (secondary != null) {
            Text(secondary, style = CalendarType.chipSecondary, color = ink.copy(alpha = CalendarDimens.CHIP_SECONDARY_ALPHA), maxLines = 1)
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
        HhIcon("date_range", size = CalendarDimens.lockedIcon, tint = c.mute)
        Text(label, style = CalendarType.lockedDates, color = c.ink)
    }
}

/** Hand-off 14: the calendar refused the save; the details are still in the sheet. */
@Composable
private fun FailureCard(title: String) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.editorCardIconGap),
        modifier = Modifier
            .testTag("editor_failure")
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.editorCardRadius))
            .background(c.dangerSoft)
            .padding(horizontal = CalendarDimens.editorCardPaddingH, vertical = CalendarDimens.editorCardPaddingV),
    ) {
        HhIcon("cloud_off", size = CalendarDimens.failureIcon, tint = c.danger)
        Column {
            // A long provider reason must not push Save off screen with the keyboard up; the body says what to do.
            Text(title, style = CalendarType.noteTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.failureBodyTop))
            Text(SAVE_FAILED_BODY, style = CalendarType.noteBody, color = c.mute)
        }
    }
}

@Composable
private fun Footer(form: EventForm, busy: Boolean, failed: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
        if (form.mode is EventForm.Mode.Edit) DeleteButton(enabled = !busy, tag = "editor_delete", onClick = onDelete)
        PrimaryButton(
            text = when {
                failed -> "Try again"
                form.mode == EventForm.Mode.New -> "Save event"
                else -> "Save changes"
            },
            icon = if (failed) "refresh" else "check",
            enabled = form.canSave && !busy,
            tag = "editor_save",
            onClick = onSave,
            modifier = Modifier.weight(1f),
        )
    }
}
