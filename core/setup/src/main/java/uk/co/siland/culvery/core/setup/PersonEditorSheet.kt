package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.access.ui.ChoosePinPad
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.KEEP
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.ControlType
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSwatchGrid
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PersonSheet"

/** The editor sheet's fields (4a design §4.4). [existing] is null for a new person; [taken] maps other people's colours to their names. */
@Stable
internal class PersonForm(val existing: Member?, val taken: Map<Long, String>) {
    var name by mutableStateOf(existing?.person?.name.orEmpty())
    var color by mutableStateOf(existing?.person?.color ?: PersonPalette.firstFree(taken.keys))
    var role by mutableStateOf(existing?.role ?: Role.ADULT)
    var newPin by mutableStateOf<String?>(null)
    var removePin by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var nameError by mutableStateOf<String?>(null)

    val hasPin: Boolean get() = newPin != null || (existing?.hasPin == true && !removePin)
    val canSave: Boolean get() = name.isNotBlank() && color != null

    fun choosePin(pin: String) {
        newPin = pin
        removePin = false
        message = null
    }

    fun clearPin() {
        newPin = null
        removePin = existing?.hasPin == true
        message = null
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
    show { PersonEditorHost(editor, existing, taken, lastAdmin, onClose = { dismiss() }) }
}

@Composable
private fun PersonEditorHost(editor: PeopleEditor, existing: Member?, taken: Map<Long, String>, lastAdmin: Boolean, onClose: () -> Unit) {
    val form = remember(existing) { PersonForm(existing, taken) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    val action = rememberSingleAction(existing) { e ->
        Log.w(TAG, "Couldn't save a person (${e::class.simpleName})")
        form.message = COULD_NOT_SAVE
    }
    val settle: (PeopleOutcome) -> Unit = { outcome ->
        when (outcome) {
            PeopleOutcome.Done -> onClose()
            PeopleOutcome.Cancelled -> Unit
            is PeopleOutcome.Refused -> {
                form.message = outcome.message
                form.nameError = outcome.message.takeIf { outcome.nameTaken }
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
 * 4a design §4.4: the right-hand sheet (HhSheet, 600 dp) with Name, Colour (others' colours struck through), Role and PIN;
 * Save person or Save changes; Remove person, which asks first. [choosingPin] shows the PIN pad over the sheet.
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
) {
    val c = Culvery.colors
    val existing = form.existing
    Box(Modifier.fillMaxHeight().width(ShellTokens.sheetWidth).semantics { paneTitle = existing?.person?.name ?: ADD_PERSON }) {
        HhSheet(
            PaddingValues(
                top = SetupDimens.sheetPaddingTop,
                start = SetupDimens.sheetPaddingH,
                end = SetupDimens.sheetPaddingH,
                bottom = SetupDimens.sheetPaddingBottom,
            ),
            Modifier.testTag("person_sheet").then(if (choosingPin) Modifier.clearAndSetSemantics {} else Modifier),
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
                Section(NAME) {
                    HhTextField(
                        value = form.name,
                        onValueChange = {
                            form.name = it
                            form.message = null
                            form.nameError = null
                        },
                        placeholder = NAME,
                        label = NAME,
                        tag = "person_name",
                        capitalization = KeyboardCapitalization.Words,
                        error = form.nameError,
                    )
                }
                Section(COLOUR) {
                    HhSwatchGrid(form.color, form.taken, tagPrefix = "swatch_") {
                        form.color = it
                        form.message = null
                    }
                }
                Section(ROLE) {
                    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.roleGap), modifier = Modifier.selectableGroup()) {
                        Role.entries.forEach { role ->
                            HhChoiceChip(roleLine(role), selected = form.role == role, tag = "role_${role.name}", onClick = {
                                form.role = role
                                form.message = null
                            })
                        }
                    }
                }
                Section(PIN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                        if (form.hasPin) {
                            HhButton(CHANGE_PIN, onChoosePin, Modifier.testTag("person_change_pin"), enabled = !busy, on = ButtonSurface.Bg)
                            HhButton(REMOVE_PIN, form::clearPin, Modifier.testTag("person_remove_pin"), enabled = !busy, on = ButtonSurface.Bg)
                        } else {
                            HhButton(SET_PIN, onChoosePin, Modifier.testTag("person_set_pin"), enabled = !busy, on = ButtonSurface.Bg)
                        }
                    }
                }
            }
            HhMessageLine(form.message, SetupType.message, c.danger, "person_message")
            if (confirmingRemove && existing != null) {
                HhConfirmCard(
                    tag = "person_confirm",
                    keep = KEEP, keepTag = "person_keep",
                    confirm = REMOVE_PERSON, confirmTag = "person_confirm_remove",
                    busy = busy, onKeep = onKeep, onConfirm = onConfirmRemove,
                ) {
                    Text(removeQuestion(existing.person.name), style = ControlType.confirmTitle, color = c.ink)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(SetupDimens.footerGap), modifier = Modifier.fillMaxWidth()) {
                    if (canRemove) {
                        HhButton(
                            REMOVE_PERSON, onRemove, Modifier.testTag("person_remove"),
                            tone = ButtonTone.Danger, size = ButtonSize.Large, enabled = !busy, on = ButtonSurface.Bg,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    HhButton(
                        if (existing == null) SAVE_PERSON else SAVE_CHANGES,
                        onSave,
                        Modifier.testTag("person_save"),
                        tone = ButtonTone.Primary,
                        size = ButtonSize.Large,
                        enabled = form.canSave && !busy,
                        on = ButtonSurface.Bg,
                    )
                }
            }
        }
        if (choosingPin) ChoosePinPad(onChosen = onPinChosen, onCancel = onPinCancelled, overSheet = true)
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.labelGap)) {
        HhSectionLabel(label)
        content()
    }
}
