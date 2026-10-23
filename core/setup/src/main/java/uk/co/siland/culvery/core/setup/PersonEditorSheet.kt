package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.access.ui.ChoosePinPad
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.label
import uk.co.siland.culvery.core.plugin.KEEP
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhSwatchGrid
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.KeepsToastsAbove
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.RadioRowTokens
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PersonSheet"

/**
 * The editor sheet's fields (4a design §4.4). [existing] is null for a new person; [taken] maps other people's colours to
 * their names. A [refusal] belongs to no edit: changing any field, or the PIN, clears it.
 */
@Stable
internal class PersonForm(val existing: Member?, taken: Map<Long, String>) {
    /** Other people's colours, and any colour refused since the sheet opened. */
    var taken by mutableStateOf(taken)
        private set

    private var nameState by mutableStateOf(existing?.person?.name.orEmpty())
    private var colorState by mutableStateOf(existing?.person?.color ?: PersonPalette.firstFree(taken.keys))
    private var roleState by mutableStateOf(existing?.role ?: Role.ADULT)

    var name: String
        get() = nameState
        set(value) {
            nameState = value
            refusal = null
        }

    var color: Long?
        get() = colorState
        set(value) {
            colorState = value
            refusal = null
        }

    var role: Role
        get() = roleState
        set(value) {
            roleState = value
            refusal = null
        }

    var newPin by mutableStateOf<String?>(null)
        private set
    var removePin by mutableStateOf(false)
        private set

    /** What the last Save or Remove was refused for; null once anything changes. */
    var refusal by mutableStateOf<PeopleOutcome.Refused?>(null)
        private set

    /** How many refusals there have been, so an identical one still counts as new. */
    var refusals by mutableIntStateOf(0)
        private set

    private val opening = listOf(name, color, role)

    /** Anything differs from how the sheet opened (a PIN chosen or removed counts): closing it now would lose it. */
    val edited: Boolean get() = listOf(name, color, role) != opening || newPin != null || removePin

    val hasPin: Boolean get() = newPin != null || (existing?.hasPin == true && !removePin)
    val canSave: Boolean get() = name.isNotBlank() && color != null && color !in taken.keys

    fun choosePin(pin: String) {
        newPin = pin
        removePin = false
        refusal = null
    }

    fun clearPin() {
        newPin = null
        removePin = existing?.hasPin == true
        refusal = null
    }

    /** Shows [outcome]; a refused colour is taken from now on. */
    fun refuse(outcome: PeopleOutcome.Refused) {
        refusal = outcome
        refusals++
        if (outcome.field == PersonField.COLOUR) color?.let { taken = taken + (it to SOMEONE_ELSE) }
    }

    fun draft(): PersonDraft = PersonDraft(name, checkNotNull(color), role, newPin, removePin)
}

/**
 * Opens the editor for [existing], or for a new person when null. [members] is the household now: everyone else's
 * colours are taken, and [existing] may be its only active Admin.
 */
internal fun OverlayHost.showPersonEditor(editor: PeopleEditor, existing: Member?, members: List<Member>) {
    val taken = members.filter { it != existing }.associate { it.person.color to it.person.name }
    val lastAdmin = existing?.isActiveAdmin == true && members.count { it.isActiveAdmin } == 1
    show { PersonEditorHost(editor, existing, taken, lastAdmin, onClose = { dismiss() }, onEdited = ::markEdited) }
}

@Composable
private fun PersonEditorHost(
    editor: PeopleEditor,
    existing: Member?,
    taken: Map<Long, String>,
    lastAdmin: Boolean,
    onClose: () -> Unit,
    onEdited: (Boolean) -> Unit,
) {
    val form = remember(existing) { PersonForm(existing, taken) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    val action = rememberSingleAction(existing) { e ->
        Log.w(TAG, "Couldn't save a person (${e::class.simpleName})")
        form.refuse(PeopleOutcome.Refused(couldNotSaveDetails(existing?.person?.name)))
    }
    // Saving counts as edited, so the idle close never cuts a save off half done.
    val edited by rememberUpdatedState(onEdited)
    LaunchedEffect(form) { snapshotFlow { form.edited || action.busy }.collect { edited(it) } }
    val settle: (PeopleOutcome) -> Unit = { outcome ->
        when (outcome) {
            PeopleOutcome.Done -> onClose()
            PeopleOutcome.Cancelled -> Unit
            is PeopleOutcome.Refused -> {
                form.refuse(outcome)
                confirmingRemove = false
            }
        }
    }
    PersonEditorSheet(
        form = form,
        busy = action.busy,
        // 4a design §4.4: not yourself while you're the last Admin.
        canRemove = existing != null && !(lastAdmin && editor.isSignedIn(existing.person.id)),
        confirmingRemove = confirmingRemove,
        choosingPin = choosingPin,
        onChoosePin = { choosingPin = true },
        onPinChosen = { pin ->
            form.choosePin(pin)
            choosingPin = false
        },
        onPinCancelled = { choosingPin = false },
        onSave = {
            action.run { settle(if (existing == null) editor.add(form.draft()) else editor.save(existing.person.id, form.draft())) }
        },
        onRemove = { confirmingRemove = true },
        onKeep = { confirmingRemove = false },
        onConfirmRemove = { action.run { existing?.let { settle(editor.remove(it.person.id)) } } },
        onClose = onClose,
    )
}

/**
 * 4a design §4.4: the right-hand sheet (HhSheet, 613 dp) with Name, Colour (others' colours blocked and named), Role and
 * PIN; Save person or Save changes; Remove person, which asks first. A refusal shows under the section it is about, which
 * scrolls into view with focus on it; one about no field shows above the footer. [choosingPin] shows the PIN pad over
 * the sheet.
 */
@Composable
internal fun PersonEditorSheet(
    form: PersonForm,
    busy: Boolean,
    canRemove: Boolean,
    confirmingRemove: Boolean,
    choosingPin: Boolean,
    onChoosePin: () -> Unit,
    onPinChosen: (String) -> Unit,
    onPinCancelled: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
    onConfirmRemove: () -> Unit,
    onClose: () -> Unit,
    keyboard: WindowInsets = WindowInsets.ime,
) {
    val c = Culvery.colors
    val existing = form.existing
    val views = remember { PersonField.entries.associateWith { BringIntoViewRequester() } }
    val focus = remember { PersonField.entries.associateWith { FocusRequester() } }
    // Only a refusal made while the sheet shows moves focus; a form reopened with one held doesn't.
    val seen = remember(form) { form.refusals }
    LaunchedEffect(form, form.refusals) {
        val field = form.refusal?.field
        if (field != null && form.refusals > seen) {
            views.getValue(field).bringIntoView()
            focus.getValue(field).requestFocus()
        }
    }
    fun errorFor(field: PersonField): String? = form.refusal?.takeIf { it.field == field }?.message
    Box(Modifier.fillMaxHeight().width(ShellTokens.sheetWidth).semantics { paneTitle = existing?.person?.name ?: ADD_PERSON }) {
        HhSheet(
            PaddingValues(
                top = SetupDimens.sheetPaddingTop,
                start = SetupDimens.sheetPaddingH,
                end = SetupDimens.sheetPaddingH,
                bottom = SetupDimens.sheetPaddingBottom,
            ),
            Modifier.testTag("person_sheet").then(if (choosingPin) Modifier.clearAndSetSemantics {} else Modifier),
            keyboard = keyboard,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    existing?.person?.name ?: ADD_PERSON,
                    style = SetupType.sheetTitle,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                HhCloseButton(onClose)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(SetupDimens.sectionGap),
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Section(NAME, views.getValue(PersonField.NAME)) {
                    HhTextField(
                        value = form.name,
                        onValueChange = { form.name = it },
                        placeholder = NAME,
                        label = NAME,
                        tag = "person_name",
                        modifier = Modifier.focusRequester(focus.getValue(PersonField.NAME)),
                        capitalization = KeyboardCapitalization.Words,
                        error = errorFor(PersonField.NAME),
                    )
                }
                Section(COLOUR, views.getValue(PersonField.COLOUR), errorFor(PersonField.COLOUR), "person_colour_error", focus.getValue(PersonField.COLOUR)) {
                    HhSwatchGrid(form.color, form.taken, tagPrefix = "swatch_") { form.color = it }
                    takenLine(form.taken)?.let {
                        Text(
                            it,
                            style = SetupType.takenLine,
                            color = c.mute,
                            modifier = Modifier.padding(top = SetupDimens.takenLineGap - SetupDimens.labelGap).testTag("person_taken_line"),
                        )
                    }
                }
                Section(ROLE, views.getValue(PersonField.ROLE), errorFor(PersonField.ROLE), "person_role_error", focus.getValue(PersonField.ROLE)) {
                    Column(verticalArrangement = Arrangement.spacedBy(RadioRowTokens.gap), modifier = Modifier.selectableGroup()) {
                        Role.entries.forEach { role ->
                            HhRadioRow(role.label, roleWhat(role), selected = form.role == role, tag = "role_${role.name}") { form.role = role }
                        }
                    }
                }
                Section(PIN, views.getValue(PersonField.PIN), errorFor(PersonField.PIN), "person_pin_error", focus.getValue(PersonField.PIN)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SetupDimens.pinHintGap)) {
                        if (form.hasPin) {
                            HhButton(CHANGE_PIN, onChoosePin, Modifier.testTag("person_change_pin"), enabled = !busy, on = ButtonSurface.Bg)
                            HhButton(REMOVE_PIN, form::clearPin, Modifier.testTag("person_remove_pin"), enabled = !busy, on = ButtonSurface.Bg)
                        } else {
                            HhButton(SET_PIN, onChoosePin, Modifier.testTag("person_set_pin"), enabled = !busy, on = ButtonSurface.Bg)
                        }
                        if (form.role != Role.ADMIN && !form.hasPin) {
                            Text(
                                canOnlyLook(form.name),
                                style = SetupType.secondary,
                                color = c.mute,
                                modifier = Modifier.weight(1f).testTag("person_look_only"),
                            )
                        }
                    }
                }
            }
            HhMessageLine(form.refusal?.takeIf { it.field == null }?.message, SetupType.message, c.danger, "person_message")
            KeepsToastsAbove {
                if (confirmingRemove && existing != null) {
                    HhConfirmCard(
                        tag = "person_confirm",
                        title = removeQuestion(existing.person.name),
                        keep = KEEP, keepTag = "person_keep",
                        confirm = REMOVE_PERSON, confirmTag = "person_confirm_remove",
                        busy = busy, onKeep = onKeep, onConfirm = onConfirmRemove,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(SetupDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                        if (canRemove) {
                            HhButton(
                                REMOVE_PERSON, onRemove, Modifier.testTag("person_remove"),
                                tone = ButtonTone.Danger, size = ButtonSize.Large, enabled = !busy, on = ButtonSurface.Bg,
                            )
                        }
                        HhFooterButton(
                            if (existing == null) SAVE_PERSON else SAVE_CHANGES,
                            onSave,
                            "person_save",
                            Modifier.weight(1f),
                            enabled = form.canSave && !busy,
                            busy = busy,
                        )
                    }
                }
            }
        }
        if (choosingPin) ChoosePinPad(onChosen = onPinChosen, onCancel = onPinCancelled, overSheet = true)
    }
}

/** A labelled part of the sheet: [content], then its refusal ([error], as [errorTag]), focusable so focus can move to it. */
@Composable
private fun Section(
    label: String,
    view: BringIntoViewRequester,
    error: String? = null,
    errorTag: String = "",
    errorFocus: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.labelGap), modifier = Modifier.bringIntoViewRequester(view)) {
        HhSectionLabel(label)
        content()
        if (error != null && errorFocus != null) {
            HhStatusLine(error, errorTag, icon = Icons.ERROR, field = true, modifier = Modifier.focusRequester(errorFocus).focusable())
        }
    }
}
