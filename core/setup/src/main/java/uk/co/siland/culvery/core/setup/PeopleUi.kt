package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction

/**
 * 4a design §4.4: each person with their colour, name, role and whether they have a PIN; tap to edit. With [onMove]
 * (Settings › People, 4c §7.1) each row has Move up and Move down, none above the first or below the last.
 */
@Composable
internal fun PeopleList(members: List<Member>, onEdit: (Member) -> Unit, onAdd: (() -> Unit)?, onMove: ((Member, Boolean) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap), modifier = Modifier.testTag("people_list")) {
        members.forEachIndexed { i, member ->
            PersonRow(
                member,
                reorderable = onMove != null,
                onUp = if (onMove != null && i > 0) ({ onMove(member, true) }) else null,
                onDown = if (onMove != null && i < members.lastIndex) ({ onMove(member, false) }) else null,
                onClick = { onEdit(member) },
            )
        }
        if (onAdd != null) HhPillButton(ADD_PERSON, onAdd, Modifier.testTag("people_add"), primary = true)
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap),
        modifier = Modifier
            .testTag("person_$name")
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.rowRadius))
            .background(c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = SetupDimens.rowPaddingH, vertical = SetupDimens.rowPaddingV),
    ) {
        Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SetupDimens.rowLineGap)) {
            Text(name, style = SetupType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${roleName(member.role)} · ${if (member.hasPin) PIN_SET else NO_PIN}", style = SetupType.secondary, color = c.mute, maxLines = 1)
        }
        if (reorderable) {
            MoveButton(Icons.ARROW_UPWARD, moveUpLabel(name), "person_up_$name", onUp)
            MoveButton(Icons.ARROW_DOWNWARD, moveDownLabel(name), "person_down_$name", onDown)
        }
        HhIcon(Icons.CHEVRON_RIGHT, size = SetupDimens.rowIcon, tint = c.mute)
    }
}

/** A Move up or Move down button; at the list's ends its empty space, so the buttons line up. */
@Composable
private fun MoveButton(icon: String, label: String, tag: String, onClick: (() -> Unit)?) {
    if (onClick == null) {
        Spacer(Modifier.size(SetupDimens.moveButton))
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(SetupDimens.moveButton)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    ) {
        HhIcon(icon, size = SetupDimens.rowIcon, tint = Culvery.colors.ink, contentDescription = label)
    }
}

/** The people list and its editor sheet: the wizard's Household step and Settings › People ([reorder]). Add person goes at eight. */
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
