package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import uk.co.siland.culvery.capability.calendar.DEFAULT_TIMES
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.MAX_TIMES
import uk.co.siland.culvery.capability.calendar.MIN_TIMES
import uk.co.siland.culvery.capability.calendar.HOURS_MINUTES
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.RepeatEnd
import uk.co.siland.culvery.capability.calendar.SHORT_DAY
import uk.co.siland.culvery.capability.calendar.TimeChoice
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.cannotAddForOthers
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.lengthLabel
import uk.co.siland.culvery.capability.calendar.saveFailedBody
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.PersonChipInk
import uk.co.siland.culvery.core.ui.ShellTokens

/** A save that failed: where it was going ([service]) and why ([problem]); the card says both. */
data class SaveFailure(val service: String, val problem: WriteProblem)

internal const val PICK_A_DATE = "Pick a date"
internal const val PICK_A_TIME = "Pick a time"
internal const val REPEATS = "Repeats"
internal const val ENDS = "Ends"
internal const val MORE_OPTIONS = "More options"
internal const val ON_A_DATE = "On a date"
internal const val FEWER_TIMES = "Fewer times"
internal const val MORE_TIMES = "More times"
internal const val ADD_A_TITLE_FIRST = "Add a title first"
internal const val ENDS_TOO_EARLY = "The end date is before the event’s day. Pick a later one."

internal fun afterTimes(times: Int): String = "After $times times"

/**
 * Hand-off §7 "Sheet 2 — Quick-add / edit" (2b-2 design §4.2). Stateless apart from the [form] it edits: the host
 * decides [busy], [failure] and which [picker] shows. [childOnly] is a signed-in Child's own id, in a new event or an
 * edit (§6): every other Who chip is drawn locked and a tap on one calls [onRefusedWho]. The pickers draw inside
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
                start = CalendarDimens.editorPaddingH,
                end = CalendarDimens.editorPaddingH,
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
                        .testTag("editor_body")
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
                        WhenRow(form, busy) {
                            closeKeyboard()
                            onPicker(EditorPicker.Time)
                        }
                        if (form.canRepeat && childOnly == null) {
                            RepeatsSection(form, busy)
                            if (form.moreRepeats && form.repeat != RepeatChoice.Never) {
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
                    selected = (form.repeatEnd as? RepeatEnd.OnDate)?.date ?: form.day,
                    onPick = {
                        form.chooseEnd(RepeatEnd.OnDate(it))
                        onPicker(EditorPicker.None)
                    },
                    onCancel = { onPicker(EditorPicker.None) },
                )
            }
            EditorPicker.Until -> PickerLayer(onDismiss = { onPicker(EditorPicker.None) }) {
                DatePickerCard(
                    today = form.today,
                    selected = form.until ?: form.day,
                    onPick = {
                        form.chooseUntil(it)
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
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
        verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap),
    ) { content() }
}

@Composable
private fun Section(label: String, modifier: Modifier = Modifier, chips: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        HhSectionLabel(label)
        Box(Modifier.selectableGroup()) { ChipFlow(chips) }
    }
}

@Composable
private fun WhoSection(form: EventForm, everyone: List<Person>, childOnly: PersonId?, busy: Boolean, onRefusedWho: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.whoOnlyTop)) {
        Section("Who") {
            everyone.forEach { person ->
                val selected = form.who == person.id
                // One flag for the paint and the tap; CalendarEditor is the gate if the session ends mid-sheet (9 §4.8).
                val locked = childOnly != null && person.id != childOnly
                val colour = Color(person.color)
                HhChoiceChip(
                    label = person.name,
                    selected = selected,
                    tag = "who_${person.name}",
                    locked = locked,
                    selectedColor = colour,
                    selectedInk = PersonChipInk,
                    leading = { ink ->
                        if (selected) {
                            HhIcon(Icons.CHECK, size = ControlTokens.chipIcon, tint = ink)
                        } else {
                            Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(colour))
                        }
                    },
                    // The chosen chip may be a locked one (an adult tagged the child's event); a tap on it does nothing.
                    onClick = {
                        when {
                            busy -> Unit
                            locked && !selected -> onRefusedWho()
                            locked -> Unit
                            else -> form.chooseWho(person.id)
                        }
                    },
                )
            }
        }
        val childName = childOnly?.let { id -> everyone.firstOrNull { it.id == id }?.name }
        if (childName != null) WhoOnlyLine(cannotAddForOthers(childName))
    }
}

@Composable
private fun WhoOnlyLine(text: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.whoOnlyGap),
        modifier = Modifier.testTag("editor_who_only").semantics(mergeDescendants = true) {},
    ) {
        HhIcon(Icons.LOCK, size = CalendarDimens.whoOnlyIcon, tint = c.mute, modifier = Modifier.testTag("editor_who_only_icon"))
        Text(text, style = CalendarType.whoOnly, color = c.mute)
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
            label = picked ?: PICK_A_DATE,
            selected = picked != null,
            tag = "day_pick",
            role = Role.Button,
            leading = { ink -> HhIcon(Icons.CALENDAR_MONTH, size = ControlTokens.chipIcon, tint = ink) },
            onClick = { if (!busy) onPickDate() },
        )
    }
}

/** Time at its natural width with Length beside it, wrapping within what is left; all day has no Length (9 §4.8). */
@Composable
private fun WhenRow(form: EventForm, busy: Boolean, onPickTime: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.timeLengthGap), modifier = Modifier.testTag("editor_time_length").fillMaxWidth()) {
        TimeSection(form, busy, onPickTime)
        if (form.time != TimeChoice.AllDay) LengthSection(form, busy, Modifier.weight(1f))
    }
}

@Composable
private fun TimeSection(form: EventForm, busy: Boolean, onPickTime: () -> Unit) {
    Section("Time") {
        HhChoiceChip("All day", form.time == TimeChoice.AllDay, "time_all_day", onClick = { if (!busy) form.chooseTime(TimeChoice.AllDay) })
        val custom = form.time as? TimeChoice.Custom
        HhChoiceChip(
            label = custom?.time?.format(HOURS_MINUTES) ?: PICK_A_TIME,
            selected = custom != null,
            tag = "time_pick",
            role = Role.Button,
            leading = { ink -> HhIcon(Icons.SCHEDULE, size = ControlTokens.chipIcon, tint = ink) },
            onClick = { if (!busy) onPickTime() },
        )
    }
}

@Composable
private fun LengthSection(form: EventForm, busy: Boolean, modifier: Modifier) {
    Section("Length", modifier) {
        form.lengths.forEach { length ->
            HhChoiceChip(lengthLabel(length), form.length == length, "length_${length.toMinutes()}", onClick = { if (!busy) form.chooseLength(length) })
        }
    }
}

/** Never, every week, every 2 weeks and More options; a tap opens the rest in place, and it stays open (9 §4.8). */
@Composable
private fun RepeatsSection(form: EventForm, busy: Boolean) {
    val dailyFocus = remember { FocusRequester() }
    var focusDaily by remember { mutableStateOf(false) }
    // The tapped chip is gone, so focus moves onto the first of the new choices.
    LaunchedEffect(focusDaily) {
        if (focusDaily) {
            dailyFocus.requestFocus()
            focusDaily = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        HhSectionLabel(REPEATS)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
            ChipFlow {
                RepeatChoice.COMMON.forEach { RepeatChip(it, form, busy) }
                if (!form.moreRepeats) {
                    HhChoiceChip(
                        label = MORE_OPTIONS,
                        selected = false,
                        tag = "repeat_more",
                        role = Role.Button,
                        trailing = { ink -> HhIcon(Icons.EXPAND_MORE, size = ControlTokens.chipIcon, tint = ink) },
                        onClick = {
                            if (!busy) {
                                form.showMoreRepeats()
                                focusDaily = true
                            }
                        },
                    )
                }
            }
            if (form.moreRepeats) {
                ChipFlow {
                    RepeatChoice.MORE.forEach { choice ->
                        RepeatChip(choice, form, busy, if (choice == RepeatChoice.Daily) Modifier.focusRequester(dailyFocus).focusable() else Modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatChip(choice: RepeatChoice, form: EventForm, busy: Boolean, modifier: Modifier = Modifier) {
    HhChoiceChip(choice.label, form.repeat == choice, "repeat_${choice.name}", onClick = { if (!busy) form.chooseRepeat(choice) }, modifier = modifier)
}

/** Never, a date, or After N times with its count buttons; a line under them while the date is before the event day. */
@Composable
private fun EndsSection(form: EventForm, busy: Boolean, onPickEnd: () -> Unit) {
    val endsInView = remember { BringIntoViewRequester() }
    val errorInView = remember { BringIntoViewRequester() }
    // Ends appears below the fold when More options opens, and so can its error line.
    LaunchedEffect(Unit) { endsInView.bringIntoView() }
    LaunchedEffect(form.endsBeforeStart) { if (form.endsBeforeStart) errorInView.bringIntoView() }
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.sectionGap)) {
        Section(ENDS, Modifier.bringIntoViewRequester(endsInView)) {
            HhChoiceChip("Never", form.repeatEnd == RepeatEnd.Never, "ends_never", onClick = { if (!busy) form.chooseEnd(RepeatEnd.Never) })
            val end = (form.repeatEnd as? RepeatEnd.OnDate)?.date
            HhChoiceChip(
                label = end?.format(SHORT_DAY) ?: ON_A_DATE,
                selected = end != null,
                tag = "ends_pick",
                role = Role.Button,
                leading = { ink -> HhIcon(Icons.CALENDAR_MONTH, size = ControlTokens.chipIcon, tint = ink) },
                onClick = { if (!busy) onPickEnd() },
            )
            val after = form.repeatEnd as? RepeatEnd.After
            HhChoiceChip(
                label = afterTimes(after?.times ?: DEFAULT_TIMES),
                selected = after != null,
                tag = "ends_after",
                onClick = { if (!busy) form.chooseAfter() },
            )
            if (after != null) {
                CountButton(Icons.REMOVE, FEWER_TIMES, "ends_fewer", after.times > MIN_TIMES) { if (!busy) form.fewerTimes() }
                CountButton(Icons.ADD, MORE_TIMES, "ends_more", after.times < MAX_TIMES) { if (!busy) form.moreTimes() }
            }
        }
        Box(Modifier.bringIntoViewRequester(errorInView)) {
            HhMessageLine(ENDS_TOO_EARLY.takeIf { form.endsBeforeStart }, CalendarType.noteBody, Culvery.colors.danger, "ends_error")
        }
    }
}

@Composable
private fun CountButton(icon: String, spoken: String, tag: String, enabled: Boolean, onStep: () -> Unit) {
    HhRoundButton(
        icon = icon,
        spoken = spoken,
        onClick = onStep,
        modifier = Modifier.testTag(tag),
        enabled = enabled,
        on = ButtonSurface.Bg,
        size = ControlTokens.chipHeight,
        iconSize = ButtonTokens.icon,
        repeatWhileHeld = true,
    )
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
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (form.mode is EventForm.Mode.Edit) DeleteButton(enabled = !busy, tag = "editor_delete", onClick = onDelete)
        HhFooterButton(
            text = when {
                failed -> "Try again"
                form.mode == EventForm.Mode.New -> "Add event"
                else -> "Save changes"
            },
            onClick = onSave,
            tag = "editor_save",
            modifier = Modifier.weight(1f),
            icon = if (failed) Icons.REFRESH else Icons.CHECK,
            enabled = form.canSave && !busy,
            reason = ADD_A_TITLE_FIRST.takeIf { form.mode == EventForm.Mode.New && form.title.isBlank() },
        )
    }
}
