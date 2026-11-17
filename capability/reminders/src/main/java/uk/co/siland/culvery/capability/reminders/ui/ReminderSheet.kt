package uk.co.siland.culvery.capability.reminders.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.reminders.COULD_NOT_DELETE
import uk.co.siland.culvery.capability.reminders.COULD_NOT_SAVE
import uk.co.siland.culvery.capability.reminders.LIST_FULL
import uk.co.siland.culvery.capability.reminders.PICK_A_LATER_TIME
import uk.co.siland.culvery.capability.reminders.Reminder
import uk.co.siland.culvery.capability.reminders.ReminderDraft
import uk.co.siland.culvery.capability.reminders.ReminderForm
import uk.co.siland.culvery.capability.reminders.RemindersActions
import uk.co.siland.culvery.capability.reminders.SaveResult
import uk.co.siland.culvery.capability.reminders.cannotAddForOthers
import uk.co.siland.culvery.core.household.FAMILY_COLOR
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.label
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhDateGrid
import uk.co.siland.culvery.core.ui.HhSegmented
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhTimeSteppers
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HhWhoPicker
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.WhoOption
import uk.co.siland.culvery.core.ui.lockedForWho
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "Reminders"
private const val TITLE = "Reminder"
private const val WORDS_PLACEHOLDER = "What’s it for?"
private const val SHOWS_AS_FAMILYS = "Shows as Family’s"
private const val ADD_REMINDER_BUTTON = "Add reminder"
private const val SAVE_CHANGES = "Save changes"
private const val DELETE_REMINDER = "Delete reminder"
private val REPEAT_OPTIONS = listOf("Once", "Repeats")

/**
 * Loads what the form needs, then shows it as a sheet with no PIN pad: Save and Delete ask who's there (22 §4.6). [id] null
 * is a new reminder. Shows nothing when [id] has gone, or when a child's PIN is still in force and it isn't theirs.
 * An overlay shown meanwhile is left alone.
 */
internal suspend fun OverlayHost.openReminder(id: Long?, actions: RemindersActions, members: Flow<List<Member>>) {
    val before = generation
    val opened = try {
        val original = id?.let { actions.openExisting(it) ?: return }
        val at = actions.openedAt()
        Opened(original, at.toLocalDate(), at.toLocalTime(), actions.childHere(), members.first())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't open (${e::class.simpleName})")
        actions.couldNotOpen()
        return
    }
    if (generation != before) return
    val form = ReminderForm(opened.original, opened.today, opened.now, opened.child)
    show {
        val people by members.collectAsState(initial = opened.people)
        val childName = opened.child?.let { child -> people.firstOrNull { it.person.id == child }?.person?.name }
        ReminderSheetHost(
            form = form,
            editing = id,
            people = people,
            childName = childName,
            save = actions::save,
            delete = actions::delete,
            onRefusedWho = { childName?.let(actions::refuseOtherWho) },
            onEdited = ::markEdited,
            onClose = ::dismiss,
        )
    }
}

private class Opened(
    val original: Reminder?,
    val today: LocalDate,
    val now: LocalTime,
    val child: PersonId?,
    val people: List<Member>,
)

/**
 * Runs Save and Delete one at a time. Past and Full show their line and keep the sheet open; so does a cancelled PIN pad.
 * A saved reminder, or one that has gone, closes it.
 */
@Composable
internal fun ReminderSheetHost(
    form: ReminderForm,
    editing: Long?,
    people: List<Member>,
    childName: String?,
    save: suspend (Long?, ReminderDraft) -> SaveResult,
    delete: suspend (Long) -> Boolean,
    onRefusedWho: () -> Unit,
    onEdited: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var line by remember(form) { mutableStateOf<String?>(null) }
    var deleting by remember(form) { mutableStateOf(false) }
    val action = rememberSingleAction(form) { e ->
        if (deleting) {
            Log.w(TAG, "Couldn't delete (${e::class.simpleName})")
            line = COULD_NOT_DELETE
        } else {
            Log.w(TAG, "Couldn't save (${e::class.simpleName})")
            line = COULD_NOT_SAVE
        }
    }
    // A save running counts as edited, so the idle close never cuts it off half done.
    val edited by rememberUpdatedState(onEdited)
    LaunchedEffect(form) { snapshotFlow { form.edited || action.busy }.collect { edited(it) } }
    ReminderSheet(
        form = form,
        people = people,
        editing = editing != null,
        childName = childName,
        line = line,
        busy = action.busy,
        onSave = {
            if (!action.busy && form.canSave) {
                line = null
                deleting = false
                action.run {
                    when (save(editing, form.toDraft())) {
                        SaveResult.Saved, SaveResult.Gone -> onClose()
                        SaveResult.Past -> line = PICK_A_LATER_TIME
                        SaveResult.Full -> line = LIST_FULL
                        SaveResult.Cancelled, SaveResult.Invalid -> Unit
                    }
                }
            }
        },
        onDelete = {
            editing?.let { id ->
                line = null
                deleting = true
                action.run { if (delete(id)) onClose() }
            }
        },
        onClose = onClose,
        onRefusedWho = onRefusedWho,
    )
}

/** Add reminder, and Change reminder with Delete: the words, who, Once or Repeats, a day or seven day chips, and the time. */
@Composable
internal fun ReminderSheet(
    form: ReminderForm,
    people: List<Member>,
    editing: Boolean,
    childName: String?,
    line: String?,
    busy: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onRefusedWho: () -> Unit,
) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = RemindersDimens.sheetPaddingH, vertical = RemindersDimens.sheetPaddingV),
        modifier = Modifier.testTag("reminder_sheet").semantics { paneTitle = TITLE },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(TITLE, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(RemindersDimens.sheetGap),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            HhTextField(
                value = form.words,
                onValueChange = { form.words = it },
                placeholder = WORDS_PLACEHOLDER,
                label = TITLE,
                tag = "reminder_words",
                readOnly = busy,
            )
            WhoPart(form, people, childName, busy, onRefusedWho)
            HhSegmented(REPEAT_OPTIONS, if (form.repeats) 1 else 0, { if (!busy) form.repeats = it == 1 }, "reminder_repeat")
            if (form.repeats) DayChips(form, busy) else DayGrid(form, busy)
            TimePart(form, busy)
        }
        if (line != null) {
            Text(
                line, style = HhType.body, color = c.danger,
                modifier = Modifier.testTag("reminder_line").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RemindersDimens.sheetGap)) {
            HhButton(
                if (editing) SAVE_CHANGES else ADD_REMINDER_BUTTON, onSave, Modifier.testTag("reminder_save"),
                tone = ButtonTone.Primary, size = ButtonSize.Large, enabled = form.canSave && !busy, on = ButtonSurface.Bg, busy = busy,
            )
            if (editing) {
                HhButton(
                    DELETE_REMINDER, onDelete, Modifier.testTag("reminder_delete"),
                    tone = ButtonTone.Danger, size = ButtonSize.Large, icon = Icons.DELETE, enabled = !busy, on = ButtonSurface.Bg,
                )
            }
        }
    }
}

@Composable
private fun WhoPart(form: ReminderForm, people: List<Member>, childName: String?, busy: Boolean, onRefusedWho: () -> Unit) {
    val options = remember(people) { people.map { WhoOption(it.person.id.value, it.person.name, it.role.label, it.person.color) } }
    Column(verticalArrangement = Arrangement.spacedBy(RemindersDimens.sheetGap)) {
        HhWhoPicker(
            options = options,
            chosen = form.who.mapTo(mutableSetOf()) { it.value },
            everyone = form.who == listOf(PersonId.FAMILY),
            everyoneLine = SHOWS_AS_FAMILYS,
            everyoneColour = FAMILY_COLOR,
            locked = { key -> lockedForWho(key?.let(::PersonId) ?: PersonId.FAMILY, form.who, form.childOnly) },
            enabled = !busy,
            onPick = { key -> form.toggleWho(key?.let(::PersonId) ?: PersonId.FAMILY) },
            onRefused = onRefusedWho,
        )
        if (childName != null) {
            Text(cannotAddForOthers(childName), style = HhType.secondary, color = Culvery.colors.mute, modifier = Modifier.testTag("reminder_who_only"))
        }
    }
}

@Composable
private fun DayGrid(form: ReminderForm, busy: Boolean) {
    var month by remember { mutableStateOf(YearMonth.from(form.date)) }
    HhDateGrid(
        month = month,
        onMonth = { month = it },
        today = form.today,
        chosen = setOf(form.date),
        band = null,
        allowed = { day -> !busy && form.allowedDay(day) },
        dots = emptyMap(),
        spoken = { "" },
        onPick = { form.date = it },
        modifier = Modifier.testTag("reminder_date"),
    )
}

@Composable
private fun DayChips(form: ReminderForm, busy: Boolean) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().testTag("reminder_days")) {
        DayOfWeek.entries.forEach { day ->
            HhChoiceChip(
                label = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                selected = day in form.days,
                tag = "reminder_day_${day.name}",
                onClick = { if (!busy) form.toggleDay(day) },
                role = Role.Checkbox,
            )
        }
    }
}

@Composable
private fun TimePart(form: ReminderForm, busy: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RemindersDimens.pickerRadius))
            .background(Culvery.colors.surf)
            .padding(RemindersDimens.pickerPadding),
    ) {
        HhTimeSteppers(
            time = form.time,
            tagPrefix = "reminder_time_",
            onStep = { field, up -> if (!busy) form.step(field, up) },
        )
    }
}
