package uk.co.siland.culvery.core.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One person's tile: [key] says who it is to the caller; [colour] is ARGB. Prints only its type name, since [name] is a person's. */
data class WhoOption(val key: String, val name: String, val role: String, val colour: Long) {
    override fun toString(): String = "WhoOption"
}

/**
 * [current] after a tap on [tapped]: [family] replaces the set and a person replaces [family]; otherwise the person is
 * added or removed, but the last one stays unless [mayClear].
 */
fun <T> toggledWho(current: List<T>, tapped: T, family: T, mayClear: Boolean = false): List<T> = when {
    tapped == family -> listOf(family)
    family in current -> listOf(tapped)
    tapped !in current -> current + tapped
    current.size > 1 || mayClear -> current - tapped
    else -> current
}

/**
 * A signed-in child ([only]) is only ever themselves, so every tile is locked once anyone is chosen, theirs included: they
 * can't untick into a set Save would refuse. One flag for the paint and the tap.
 */
fun <T> lockedForWho(option: T, chosen: List<T>, only: T?): Boolean =
    only != null && (option != only || chosen.isNotEmpty())

private const val TILES_A_ROW = 3
private const val TINT_ALPHA = 0x33 / 255f
private const val EVERYONE_NAME = "Everyone"
private const val CHOSEN_LOCKED = "$CHOSEN, $NOT_AVAILABLE"
private val TileInk = Color(0xFF111111)

/** Handoff 6b: the household as tiles, three a row, any number chosen, then Everyone. */
object WhoPickerTokens {
    // Three a row, 13 apart, 160 tall, radius 20; a 64 circle 8 above the name, the role 8 below it. Chosen, a 3 ring inside
    // and a 32 badge 11 in from the top right; locked, a 20 lock 11 in from the top left.
    val sectionGap = 15.dp
    val tileGap = 13.dp
    val tileHeight = 160.dp
    val tileRadius = 20.dp
    val tileCircle = 64.dp
    val tileInnerGap = 8.dp
    val tileRing = 3.dp
    val tileBadge = 32.dp
    val tileBadgeIcon = 21.dp
    val tileCorner = 11.dp
    val tileLock = 20.dp

    // The Everyone row: 69 tall, radius 19, 19 in at the sides, a 40 Family circle with a 24 icon 13 before the words.
    val everyoneHeight = 69.dp
    val everyoneRadius = 19.dp
    val everyonePaddingH = 19.dp
    val everyoneGap = 13.dp
    val everyoneCircle = 40.dp
    val everyoneIcon = 24.dp
    val everyoneLineGap = 1.dp

    /** 28 sp / 700, 20 sp / 700 and 13 sp: a tile's initial, name and role. */
    val initial = HhType.dateNumber.copy(fontSize = 28.sp)
    val name = HhType.sectionTitle.copy(fontSize = 20.sp)
    val role = HhType.secondary.copy(fontSize = 13.sp)

    /** 19 sp / 700 and 14 sp: "Everyone" and the line under it. */
    val everyoneName = HhType.cardTitle
    val everyoneLine = HhType.secondary
}

/**
 * The [options] as tiles, then Everyone (a null key), whose second line is [everyoneLine] and whose colour is
 * [everyoneColour]. A [locked] tile is outlined with a padlock: a tap on it does nothing when it is chosen and calls
 * [onRefused] when it is not. [enabled] false ignores every tap without changing how anything looks.
 */
@Composable
fun HhWhoPicker(
    options: List<WhoOption>,
    chosen: Set<String>,
    everyone: Boolean,
    everyoneLine: String,
    everyoneColour: Long,
    locked: (key: String?) -> Boolean,
    enabled: Boolean,
    onPick: (key: String?) -> Unit,
    onRefused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tap = { key: String?, selected: Boolean, isLocked: Boolean ->
        when {
            !enabled || (isLocked && selected) -> Unit
            isLocked -> onRefused()
            else -> onPick(key)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(WhoPickerTokens.sectionGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(WhoPickerTokens.tileGap)) {
            options.chunked(TILES_A_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(WhoPickerTokens.tileGap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { option ->
                        val selected = option.key in chosen
                        val isLocked = locked(option.key)
                        PersonTile(option, selected, isLocked, { tap(option.key, selected, isLocked) }, Modifier.weight(1f))
                    }
                    repeat(TILES_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val everyoneLocked = locked(null)
        EveryoneRow(everyone, everyoneLocked, everyoneLine, everyoneColour) { tap(null, everyone, everyoneLocked) }
    }
}

@Composable
private fun PersonTile(option: WhoOption, selected: Boolean, locked: Boolean, onTap: () -> Unit, modifier: Modifier) {
    val c = Culvery.colors
    val colour = Color(option.colour)
    val tag = "who_${option.name}"
    val shape = RoundedCornerShape(WhoPickerTokens.tileRadius)
    Box(
        modifier
            .testTag(tag)
            .whoToggle(selected, locked, onTap)
            .height(WhoPickerTokens.tileHeight)
            .clip(shape)
            .whoFill(c, selected, locked, colour, shape),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WhoPickerTokens.tileInnerGap),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = WhoPickerTokens.tileCorner),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.testTag("${tag}_dot").size(WhoPickerTokens.tileCircle).clip(CircleShape).background(colour),
            ) {
                // The name follows; TalkBack would read the initial before it.
                Text(option.name.take(1).uppercase(), style = WhoPickerTokens.initial, color = TileInk, modifier = Modifier.clearAndSetSemantics {})
            }
            Text(option.name, style = WhoPickerTokens.name, color = if (locked && !selected) c.mute else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(option.role, style = WhoPickerTokens.role, color = c.mute, maxLines = 1)
        }
        if (selected) CheckBadge(tag, colour, Modifier.align(Alignment.TopEnd).padding(WhoPickerTokens.tileCorner))
        if (locked) Padlock(tag, Modifier.align(Alignment.TopStart).padding(WhoPickerTokens.tileCorner))
    }
}

@Composable
private fun CheckBadge(tag: String, colour: Color, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.testTag("${tag}_check").size(WhoPickerTokens.tileBadge).clip(CircleShape).background(colour),
    ) {
        HhIcon(Icons.CHECK, size = WhoPickerTokens.tileBadgeIcon, tint = TileInk)
    }
}

@Composable
private fun Padlock(tag: String, modifier: Modifier = Modifier) {
    HhIcon(Icons.LOCK, size = WhoPickerTokens.tileLock, tint = Culvery.colors.mute, modifier = modifier.testTag("${tag}_lock"))
}

/** 69 dp: the Family circle with `groups`, "Everyone" and [line]; chosen, tinted as a tile. */
@Composable
private fun EveryoneRow(selected: Boolean, locked: Boolean, line: String, familyColour: Long, onTap: () -> Unit) {
    val c = Culvery.colors
    val colour = Color(familyColour)
    val tag = "who_everyone"
    val shape = RoundedCornerShape(WhoPickerTokens.everyoneRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WhoPickerTokens.everyoneGap),
        modifier = Modifier
            .testTag(tag)
            .whoToggle(selected, locked, onTap)
            .fillMaxWidth()
            .height(WhoPickerTokens.everyoneHeight)
            .clip(shape)
            .whoFill(c, selected, locked, colour, shape)
            .padding(horizontal = WhoPickerTokens.everyonePaddingH),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.testTag("${tag}_dot").size(WhoPickerTokens.everyoneCircle).clip(CircleShape).background(colour),
        ) {
            HhIcon(Icons.GROUPS, size = WhoPickerTokens.everyoneIcon, tint = TileInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WhoPickerTokens.everyoneLineGap)) {
            Text(EVERYONE_NAME, style = WhoPickerTokens.everyoneName, color = if (locked && !selected) c.mute else c.ink, maxLines = 1)
            Text(line, style = WhoPickerTokens.everyoneLine, color = c.mute, maxLines = 1)
        }
        if (locked) Padlock(tag)
        if (selected) CheckBadge(tag, colour, Modifier)
    }
}

/** Chosen: the colour at 0x33 over `surf2` with a ring inside; locked and not chosen: an outline on the pane (4a). */
private fun Modifier.whoFill(c: HhColors, selected: Boolean, locked: Boolean, colour: Color, shape: Shape): Modifier = when {
    selected -> background(c.surf2).background(colour.copy(alpha = TINT_ALPHA)).border(WhoPickerTokens.tileRing, colour, shape)
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
