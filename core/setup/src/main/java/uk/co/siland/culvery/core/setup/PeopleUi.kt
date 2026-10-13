package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.rememberSingleAction

/**
 * 4a design §4.4: each person with their colour, name, role and whether they have a PIN; tap to edit. With [onMove]
 * (Settings › People, 4c §7.1) each row has Move up and Move down, none above the first or below the last.
 */
@Composable
internal fun PeopleList(members: List<Member>, onEdit: (Member) -> Unit, onAdd: (() -> Unit)?, onMove: ((Member, Boolean) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap), modifier = Modifier.testTag("people_list")) {
        members.forEachIndexed { i, member ->
            // Keyed by the person, so a move moves this row's node, and TalkBack's focus with it.
            key(member.person.id) {
                PersonRow(
                    member,
                    reorderable = onMove != null,
                    onUp = if (onMove != null && i > 0) ({ onMove(member, true) }) else null,
                    onDown = if (onMove != null && i < members.lastIndex) ({ onMove(member, false) }) else null,
                    onClick = { onEdit(member) },
                )
            }
        }
        if (onAdd != null) HhButton(ADD_PERSON, onAdd, Modifier.testTag("people_add"), tone = ButtonTone.Primary, on = ButtonSurface.Bg)
    }
}

@Composable
internal fun PersonRow(
    member: Member,
    reorderable: Boolean = false,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Culvery.colors
    val name = member.person.name
    val moves = listOfNotNull(
        onUp?.let { up -> CustomAccessibilityAction(moveUpLabel(name)) { up(); true } },
        onDown?.let { down -> CustomAccessibilityAction(moveDownLabel(name)) { down(); true } },
    )
    HhListRow(
        title = name,
        modifier = Modifier.testTag("person_$name").semantics { if (moves.isNotEmpty()) customActions = moves },
        leading = { Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color))) },
        subtitle = "${roleName(member.role)} · ${pinState(member)}",
        trailing = {
            if (reorderable) {
                MoveButton(Icons.ARROW_UPWARD, moveUpLabel(name), "person_up_$name", onUp, atEnd = ALREADY_FIRST)
                MoveButton(Icons.ARROW_DOWNWARD, moveDownLabel(name), "person_down_$name", onDown, atEnd = ALREADY_LAST)
            }
            HhIcon(Icons.CHEVRON_RIGHT, size = ListRowTokens.icon, tint = c.mute)
        },
        onClick = onClick,
        clickLabel = changeDetailsLabel(name),
    )
}

/** An Admin's PIN state, or what an Adult or Child without one can't do. */
private fun pinState(member: Member): String = when {
    member.hasPin -> PIN_SET
    member.role == Role.ADMIN -> NO_PIN
    else -> NO_PIN_CANT_ADD
}

/** A Move up or Move down button; at the list's ends disabled and faint, saying why ([atEnd]), so focus has a place to stay. */
@Composable
private fun MoveButton(icon: String, label: String, tag: String, onClick: (() -> Unit)?, atEnd: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(SetupDimens.moveButton)
            .alpha(if (onClick == null) ControlTokens.DISABLED_ALPHA else 1f)
            .clip(CircleShape)
            .then(if (onClick == null) Modifier.semantics { stateDescription = atEnd } else Modifier)
            .hhClickable(onClick ?: {}, enabled = onClick != null),
    ) {
        HhIcon(icon, size = ListRowTokens.icon, tint = Culvery.colors.ink, contentDescription = label)
    }
}

/** The people list and its editor sheet: the wizard's Household step and Settings › People ([reorder]). Add person goes at [MAX_PEOPLE]. */
@Composable
internal fun PeoplePane(editor: PeopleEditor, reorder: Boolean) {
    val members by editor.members.collectAsState(initial = emptyList())
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(Unit) { e -> Log.w(TAG, "Couldn't move a person (${e::class.simpleName})") }
    val open: (Member?) -> Unit = { overlay.showPersonEditor(editor, it, members) }
    PeopleList(
        members,
        onEdit = open,
        onAdd = if (members.size < MAX_PEOPLE) ({ open(null) }) else null,
        onMove = if (reorder) ({ member, up -> action.run { editor.move(member.person.id, up) } }) else null,
    )
}

private const val TAG = "People"
