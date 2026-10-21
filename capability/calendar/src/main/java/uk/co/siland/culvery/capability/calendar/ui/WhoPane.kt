package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.EVERYONE
import uk.co.siland.culvery.capability.calendar.EditorForm
import uk.co.siland.culvery.capability.calendar.cannotAddForOthers
import uk.co.siland.culvery.core.household.FAMILY_COLOR
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.label
import uk.co.siland.culvery.core.ui.CHOSEN
import uk.co.siland.culvery.core.ui.ControlTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.FIND_OUT_WHY
import uk.co.siland.culvery.core.ui.HhColors
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.NOT_AVAILABLE
import uk.co.siland.culvery.core.ui.NOT_CHOSEN
import uk.co.siland.culvery.core.ui.hhClickable

internal const val SHOWS_AS_FAMILY = "Shows as a Family event"
internal const val PICK_MORE_THAN_ONE = "Pick more than one person for a shared event."

private const val TILES_A_ROW = 3
private const val TINT_ALPHA = 0x33 / 255f
private val TileInk = Color(0xFF111111)
private const val CHOSEN_LOCKED = "$CHOSEN, $NOT_AVAILABLE"

/**
 * A signed-in child is only ever themselves, so every tile is locked once anyone is chosen, theirs included: they can't
 * untick into a set Save would refuse (9 §3.3). One flag for the paint and the tap; CalendarEditor is the gate if the
 * session ends.
 */
internal fun lockedFor(person: PersonId, who: List<PersonId>, childOnly: PersonId?): Boolean =
    childOnly != null && (person != childOnly || who.isNotEmpty())

/**
 * Handoff 6b: the household as tiles, three a row, any number chosen, then Everyone, which saves a Family event and
 * clears the picks. Choosing never moves on by itself. For a child ([childOnly], named [childName]) everyone else is
 * locked and a tap on them calls [onRefusedWho]; a tap on a chosen locked tile does nothing. [busy] ignores taps.
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
    val tap = { id: PersonId, selected: Boolean, locked: Boolean ->
        when {
            busy || (locked && selected) -> Unit
            locked -> onRefusedWho()
            else -> form.toggleWho(id)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.paneGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(EditorDimens.tileGap)) {
            members.chunked(TILES_A_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(EditorDimens.tileGap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { member ->
                        val id = member.person.id
                        val selected = id in form.who
                        val locked = lockedFor(id, form.who, childOnly)
                        PersonTile(member, selected, locked, { tap(id, selected, locked) }, Modifier.weight(1f))
                    }
                    repeat(TILES_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val everyoneLocked = lockedFor(PersonId.FAMILY, form.who, childOnly)
        EveryoneRow(form.everyone, everyoneLocked) { tap(PersonId.FAMILY, form.everyone, everyoneLocked) }
        if (childName != null) LockedNote(cannotAddForOthers(childName), "editor_who_only") else WhoHint()
    }
}

@Composable
private fun PersonTile(member: Member, selected: Boolean, locked: Boolean, onTap: () -> Unit, modifier: Modifier) {
    val c = Culvery.colors
    val person = member.person
    val colour = Color(person.color)
    val tag = "who_${person.name}"
    val shape = RoundedCornerShape(EditorDimens.tileRadius)
    Box(
        modifier
            .testTag(tag)
            .whoToggle(selected, locked, onTap)
            .height(EditorDimens.tileHeight)
            .clip(shape)
            .whoFill(c, selected, locked, colour, shape),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EditorDimens.tileInnerGap),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = EditorDimens.tileCorner),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.testTag("${tag}_dot").size(EditorDimens.tileCircle).clip(CircleShape).background(colour),
            ) {
                // The name follows; TalkBack would read the initial before it.
                Text(person.name.take(1).uppercase(), style = CalendarType.whoInitial, color = TileInk, modifier = Modifier.clearAndSetSemantics {})
            }
            Text(person.name, style = CalendarType.whoName, color = if (locked && !selected) c.mute else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(member.role.label, style = CalendarType.whoRole, color = c.mute, maxLines = 1)
        }
        if (selected) CheckBadge(tag, colour, Modifier.align(Alignment.TopEnd).padding(EditorDimens.tileCorner))
        if (locked) Padlock(tag, Modifier.align(Alignment.TopStart).padding(EditorDimens.tileCorner))
    }
}

@Composable
private fun CheckBadge(tag: String, colour: Color, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.testTag("${tag}_check").size(EditorDimens.tileBadge).clip(CircleShape).background(colour),
    ) {
        HhIcon(Icons.CHECK, size = EditorDimens.tileBadgeIcon, tint = TileInk)
    }
}

@Composable
private fun Padlock(tag: String, modifier: Modifier = Modifier) {
    HhIcon(Icons.LOCK, size = EditorDimens.tileLock, tint = Culvery.colors.mute, modifier = modifier.testTag("${tag}_lock"))
}

/** 69 dp: the Family circle with `groups`, "Everyone" and "Shows as a Family event"; chosen, tinted as a tile. */
@Composable
private fun EveryoneRow(selected: Boolean, locked: Boolean, onTap: () -> Unit) {
    val c = Culvery.colors
    val colour = Color(FAMILY_COLOR)
    val tag = "who_everyone"
    val shape = RoundedCornerShape(EditorDimens.everyoneRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorDimens.everyoneGap),
        modifier = Modifier
            .testTag(tag)
            .whoToggle(selected, locked, onTap)
            .fillMaxWidth()
            .height(EditorDimens.everyoneHeight)
            .clip(shape)
            .whoFill(c, selected, locked, colour, shape)
            .padding(horizontal = EditorDimens.everyonePaddingH),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.testTag("${tag}_dot").size(EditorDimens.everyoneCircle).clip(CircleShape).background(colour),
        ) {
            HhIcon(Icons.GROUPS, size = EditorDimens.everyoneIcon, tint = TileInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EditorDimens.everyoneLineGap)) {
            Text(EVERYONE, style = CalendarType.everyoneName, color = if (locked && !selected) c.mute else c.ink, maxLines = 1)
            Text(SHOWS_AS_FAMILY, style = CalendarType.everyoneLine, color = c.mute, maxLines = 1)
        }
        if (locked) Padlock(tag)
        if (selected) CheckBadge(tag, colour, Modifier)
    }
}

/** Chosen: the colour at 0x33 over `surf2` with a ring inside; locked and not chosen: an outline on the pane (4a). */
private fun Modifier.whoFill(c: HhColors, selected: Boolean, locked: Boolean, colour: Color, shape: Shape): Modifier = when {
    selected -> background(c.surf2).background(colour.copy(alpha = TINT_ALPHA)).border(EditorDimens.tileRing, colour, shape)
    locked -> border(ControlTokens.lockedEdge, c.surf3, shape)
    else -> background(c.surf2)
}

/** A checkbox TalkBack hears as chosen or not; a locked one that isn't chosen says why on a tap rather than toggling. */
private fun Modifier.whoToggle(selected: Boolean, locked: Boolean, onTap: () -> Unit): Modifier = this
    .semantics {
        toggleableState = ToggleableState(selected)
        stateDescription = when {
            selected && locked -> CHOSEN_LOCKED
            selected -> CHOSEN
            locked -> NOT_AVAILABLE
            else -> NOT_CHOSEN
        }
    }
    .hhClickable(onTap, label = FIND_OUT_WHY.takeIf { locked && !selected }, role = Role.Checkbox)

@Composable
private fun WhoHint() {
    Text(PICK_MORE_THAN_ONE, style = CalendarType.whoHint, color = Culvery.colors.mute, modifier = Modifier.testTag("editor_who_hint"))
}
