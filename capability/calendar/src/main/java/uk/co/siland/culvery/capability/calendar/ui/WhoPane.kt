package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.cannotAddForOthers
import uk.co.siland.culvery.core.household.FAMILY_COLOR
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.label
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhWhoPicker
import uk.co.siland.culvery.core.ui.WhoOption
import uk.co.siland.culvery.core.ui.lockedForWho

internal const val SHOWS_AS_FAMILY = "Shows as a Family event"
internal const val PICK_MORE_THAN_ONE = "Pick more than one person for a shared event."

/**
 * A signed-in child is only ever themselves, so every tile is locked once anyone is chosen, theirs included: they can't
 * untick into a set Save would refuse (9 §3.3). One flag for the paint and the tap; CalendarEditor is the gate if the
 * session ends.
 */
internal fun lockedFor(person: PersonId, who: List<PersonId>, childOnly: PersonId?): Boolean = lockedForWho(person, who, childOnly)

/**
 * Handoff 6b in the editor: the household as tiles, then Everyone, which saves a Family event and clears the picks.
 * For a child ([childOnly], named [childName]) everyone else is locked and a tap on them calls [onRefusedWho]. [busy]
 * ignores taps.
 */
@Composable
internal fun WhoPane(
    form: EditorForm,
    members: List<Member>,
    childOnly: PersonId?,
    childName: String?,
    busy: Boolean,
    onRefusedWho: () -> Unit,
) {
    val options = remember(members) { members.map { WhoOption(it.person.id.value, it.person.name, it.role.label, it.person.color) } }
    Column(verticalArrangement = Arrangement.spacedBy(ControlTokens.formGap)) {
        HhWhoPicker(
            options = options,
            chosen = form.who.mapTo(mutableSetOf()) { it.value },
            everyone = form.everyone,
            everyoneLine = SHOWS_AS_FAMILY,
            everyoneColour = FAMILY_COLOR,
            locked = { key -> lockedFor(key?.let(::PersonId) ?: PersonId.FAMILY, form.who, childOnly) },
            enabled = !busy,
            onPick = { key -> form.toggleWho(key?.let(::PersonId) ?: PersonId.FAMILY) },
            onRefused = onRefusedWho,
        )
        if (childName != null) LockedNote(cannotAddForOthers(childName), "editor_who_only") else WhoHint()
    }
}

@Composable
private fun WhoHint() {
    Text(PICK_MORE_THAN_ONE, style = CalendarType.whoHint, color = Culvery.colors.mute, modifier = Modifier.testTag("editor_who_hint"))
}
