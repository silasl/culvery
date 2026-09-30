package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.core.household.MAX_PEOPLE
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton

/** 4a design §4.4: each person with their colour, name, role and whether they have a PIN; tap to edit. */
@Composable
internal fun PeopleList(members: List<Member>, onEdit: (Member) -> Unit, onAdd: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(SetupDimens.rowGap), modifier = Modifier.testTag("people_list")) {
        members.forEach { member -> PersonRow(member) { onEdit(member) } }
        if (onAdd != null) HhPillButton(ADD_PERSON, onAdd, Modifier.testTag("people_add"), primary = true)
    }
}

@Composable
internal fun PersonRow(member: Member, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.rowDotGap),
        modifier = Modifier
            .testTag("person_${member.person.name}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(SetupDimens.rowRadius))
            .background(c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = SetupDimens.rowPaddingH, vertical = SetupDimens.rowPaddingV),
    ) {
        Box(Modifier.size(SetupDimens.rowDot).clip(CircleShape).background(Color(member.person.color)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SetupDimens.rowLineGap)) {
            Text(member.person.name, style = SetupType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${roleName(member.role)} · ${if (member.hasPin) PIN_SET else NO_PIN}", style = SetupType.secondary, color = c.mute, maxLines = 1)
        }
        HhIcon("chevron_right", size = SetupDimens.rowIcon, tint = c.mute)
    }
}

/** The people list and its editor sheet: the wizard's Household step and Settings › People. Add person goes at eight. */
@Composable
internal fun PeoplePane(editor: PeopleEditor) {
    val members by editor.members.collectAsState(initial = emptyList())
    val overlay = LocalOverlayHost.current
    val open: (Member?) -> Unit = { overlay.showPersonEditor(editor, it, members) }
    PeopleList(members, onEdit = open, onAdd = if (members.size < MAX_PEOPLE) ({ open(null) }) else null)
}
