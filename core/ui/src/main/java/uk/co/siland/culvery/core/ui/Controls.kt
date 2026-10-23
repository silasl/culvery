package uk.co.siland.culvery.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Control values from the hand-off (§7 chips, the title field, person chips) and 4a's colour swatches. */
object ControlTokens {
    // Choice chips: 48 dp, padding 0 18, radius 24, 8 apart; a 12 dp dot or 20 dp icon 8 from the label.
    val chipHeight = 48.dp
    val chipPaddingH = 18.dp
    val chipRadius = 24.dp
    val chipGap = 8.dp
    val chipIconGap = 8.dp
    val chipDot = 12.dp
    val chipBusyIcon = 16.dp
    val chipIcon = 20.dp

    // A segmented control (handoff 6e): its chips 4 inside a `surf2` pill.
    val segmentPadding = 4.dp

    /** Hand-off §7: a disabled chip is drawn at 38% and stays tappable. */
    const val DISABLED_ALPHA = 0.38f

    // Text field (the hand-off's title field): 64 dp, radius 18, `surf`, padding 0 20, a 2 dp `accent` border while focused.
    val fieldHeight = 64.dp
    val fieldRadius = 18.dp
    val fieldPaddingH = 20.dp
    val fieldBorder = 2.dp

    // A confirmation, as the calendar's delete confirmation: `dangerSoft`, radius 24, padding 20, 16 between blocks,
    // buttons 12 apart.
    val confirmRadius = 24.dp
    val confirmPadding = 20.dp
    val confirmGap = 16.dp
    val confirmButtonGap = 12.dp

    // Colour swatch (9 §4.11, handoff 5b): a 51 dp circle, 12 apart; chosen, a 3 dp `bg` gap, a 3 dp `ink` ring and a 25 dp
    // check; taken, at 38% with a 23 dp block. The ring and its gap sit inside the 63 dp slot, so slots touch.
    val swatch = 51.dp
    val swatchSlot = 63.dp
    val swatchSpacing = 0.dp
    val swatchRing = 3.dp
    val swatchRingGap = 3.dp
    val swatchCheck = 25.dp
    val swatchBlock = 23.dp

    // A status line: a 20 dp info icon 8 from its words.
    val statusIcon = 20.dp
    val statusGap = 8.dp

    // A field's error (9 §4.8): a filled 17 dp icon 5 from its words.
    val fieldErrorIcon = 17.dp
    val fieldErrorGap = 5.dp
    val fieldErrorTop = 8.dp

    // A locked chip (9 §4.8): no fill, a 1 dp `surf3` edge.
    val lockedEdge = 1.dp
}

/** Control text styles, derived from HhType. */
object ControlType {
    /** 16 sp / 600: chips and person chips. */
    val chip = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 16 sp / 500: a chip's secondary text, e.g. a time chip's "09:00". */
    val chipSecondary = HhType.body.copy(fontWeight = FontWeight.W500)

    /** 22 sp / 600: a text field. */
    val field = HhType.sectionTitle.copy(fontWeight = FontWeight.W600)

    /** 17 sp / 700: a sheet's footer button. */
    val button = HhType.rowTitle.copy(fontWeight = FontWeight.W700)

    /** 14 sp / 600: the error under a field. */
    val fieldError = HhType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.W600)

    /** 18 sp / 700: a confirmation's question. */
    val confirmTitle = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)
}

/**
 * Hand-off §7 chip: at least 48 dp; a long label takes two lines, then ends in …; padding 0 18, radius 24,
 * 16 sp / 600; [unselectedFill]/[unselectedInk] (`surf`/`ink`), or [selectedColor]/[selectedInk] when selected. One of a radio group by default ([role]);
 * a chip that opens something is `Role.Button`. [trailing] follows the label (an expand arrow) and, as [leading], is given the ink. [spokenName] is what TalkBack reads when that isn't the label. A disabled chip is drawn at 38% but stays tappable, so a tap can
 * explain why: TalkBack hears "not available" and "double-tap to find out why". A [locked] chip (9 §4.8) says the same
 * but isn't faded: the `lock` icon first, no fill, a `surf3` edge and a `mute` label, or selected, filled as chosen.
 * [labelStyle] can ask for tabular figures; a chip stretched wider than its contents centres them.
 */
@Composable
fun HhChoiceChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selectedColor: Color = Culvery.colors.accent,
    selectedInk: Color = Culvery.colors.accentInk,
    unselectedFill: Color = Culvery.colors.surf,
    unselectedInk: Color = Culvery.colors.ink,
    secondary: String? = null,
    role: Role = Role.RadioButton,
    clickLabel: String? = null,
    stateDescription: String? = null,
    leading: (@Composable (ink: Color) -> Unit)? = null,
    spokenName: String? = null,
    trailing: (@Composable (ink: Color) -> Unit)? = null,
    locked: Boolean = false,
    labelStyle: TextStyle = ControlType.chip,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val bare = locked && !selected
    val ink = when {
        selected -> selectedInk
        bare -> c.mute
        else -> unselectedInk
    }
    val unavailable = !enabled || locked
    val state = stateDescription ?: NOT_AVAILABLE.takeIf { unavailable }
    val shape = RoundedCornerShape(ControlTokens.chipRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap, Alignment.CenterHorizontally),
        modifier = modifier
            .boundedWidth()
            .testTag(tag)
            .semantics {
                // A button chip that isn't holding a value says nothing about selection.
                if (role != Role.Button || selected) this.selected = selected
                if (state != null) this.stateDescription = state
            }
            .alpha(if (enabled) 1f else ControlTokens.DISABLED_ALPHA)
            .heightIn(min = ControlTokens.chipHeight)
            .clip(shape)
            .then(
                if (bare) Modifier.border(ControlTokens.lockedEdge, c.surf3, shape) else Modifier.background(if (selected) selectedColor else unselectedFill),
            )
            // Never `enabled = false`: that would stop the explaining tap.
            .hhClickable(onClick, label = clickLabel ?: FIND_OUT_WHY.takeIf { unavailable }, role = role, spoken = spokenName)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        if (locked) HhIcon(Icons.LOCK, size = ControlTokens.chipIcon, tint = ink, modifier = Modifier.testTag("${tag}_lock"))
        leading?.invoke(ink)
        Text(label, style = labelStyle, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (secondary != null) {
            Text(secondary, style = ControlType.chipSecondary, color = ink, maxLines = 1)
        }
        trailing?.invoke(ink)
    }
}

/** Under an unbounded width (a LazyRow's) the label's weight would get none, so the chip is bounded at its natural width. */
private fun Modifier.boundedWidth(): Modifier = layout { measurable, constraints ->
    val bounded = if (constraints.hasBoundedWidth) {
        constraints
    } else {
        constraints.copy(maxWidth = measurable.maxIntrinsicWidth(constraints.maxHeight).coerceAtLeast(constraints.minWidth))
    }
    val placeable = measurable.measure(bounded)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * One of [options], as handoff 6e's All day | At a time: a `surf2` pill of 48 dp [HhChoiceChip] segments tagged
 * `{tag}_{index}`, the [selected] one `accent` with `accentInk`; a radio group for TalkBack. A [locked] segment draws as a
 * locked chip and a tap on it calls [onLocked], to say why, instead of [onSelect].
 */
@Composable
fun HhSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    locked: Set<Int> = emptySet(),
    onLocked: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .testTag(tag)
            .selectableGroup()
            .clip(RoundedCornerShape(percent = 50))
            .background(Culvery.colors.surf2)
            .padding(ControlTokens.segmentPadding),
    ) {
        options.forEachIndexed { i, option ->
            val isLocked = i in locked
            HhChoiceChip(
                label = option,
                selected = i == selected,
                tag = "${tag}_$i",
                onClick = { if (isLocked) onLocked() else onSelect(i) },
                unselectedFill = Color.Transparent,
                locked = isLocked,
            )
        }
    }
}

/** What TalkBack says of a choice's state, and a locked one's tap. */
const val NOT_AVAILABLE = "not available"
const val FIND_OUT_WHY = "find out why"
const val CHOSEN = "chosen"
const val NOT_CHOSEN = "not chosen"

/**
 * 64 dp, `surf`, 22 sp / 600, one line; [placeholder] while empty; the keyboard's Done calls [onDone] and never saves.
 * [label] is its name for TalkBack once text hides the placeholder. While [error] is set the field has a `danger` ring in
 * place of its focus ring, and the error shows 8 dp under it as `{tag}_error`; TalkBack reads it with the field, once.
 * A new error scrolls the field and its error into view. [modifier] goes on the field itself.
 */
@Composable
fun HhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    error: String? = null,
    onDone: () -> Unit = {},
) {
    val c = Culvery.colors
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val ring = if (error != null) c.danger else if (focused) c.accent else null
    val shape = RoundedCornerShape(ControlTokens.fieldRadius)
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error != null) bringIntoView.bringIntoView() }
    Column(verticalArrangement = Arrangement.spacedBy(ControlTokens.fieldErrorTop), modifier = Modifier.bringIntoViewRequester(bringIntoView)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = readOnly,
            textStyle = ControlType.field.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
            // Leaving the field closes the keyboard: a KeyboardActions onDone replaces Compose's own closing.
            keyboardActions = KeyboardActions(onDone = {
                onDone()
                focusManager.clearFocus()
            }),
            modifier = modifier
                .testTag(tag)
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                }
                .fillMaxWidth()
                .height(ControlTokens.fieldHeight)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(c.surf)
                        .then(ring?.let { Modifier.border(ControlTokens.fieldBorder, it, shape) } ?: Modifier)
                        .padding(horizontal = ControlTokens.fieldPaddingH),
                ) {
                    // A placeholder that only repeats the label would be read after it; a different one is a useful hint.
                    if (value.isEmpty()) {
                        Text(
                            placeholder, style = ControlType.field, color = c.mute, maxLines = 1,
                            modifier = if (placeholder == label) Modifier.shownNotSpoken() else Modifier,
                        )
                    }
                    inner()
                }
            },
        )
        if (error != null) {
            HhStatusLine(error, "${tag}_error", icon = Icons.ERROR, field = true, spoken = false)
        }
    }
}

/**
 * An in-page message that TalkBack hears appear. Compose speaks a live region only when a node it already has changes,
 * and leaves a node with no size out of the tree, so the line is always composed and one line tall while empty.
 * [spoken] is its description when it differs from what shows ("3 towns found" under a list).
 */
@Composable
fun HhMessageLine(
    message: String?,
    style: TextStyle,
    color: Color,
    tag: String,
    modifier: Modifier = Modifier,
    spoken: String? = message,
) {
    Text(
        message.orEmpty(),
        style = style,
        color = color,
        modifier = modifier.testTag(tag).semantics {
            liveRegion = LiveRegionMode.Polite
            if (spoken != null && spoken != message) contentDescription = spoken
        },
    )
}

/**
 * The one line saying what is wrong and how to fix it, in `danger` with an [icon]; TalkBack hears it appear. Under a field
 * ([field]) it takes [ControlType.fieldError] and a 17 dp icon 5 dp from its words (else 20 and 8), drawn filled for
 * [Icons.ERROR]; [spoken] false hides it from TalkBack and makes it not live, for a field that already carries its error.
 */
@Composable
fun HhStatusLine(
    text: String,
    tag: String,
    icon: String = Icons.INFO,
    field: Boolean = false,
    style: TextStyle = if (field) ControlType.fieldError else HhType.body,
    spoken: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (field) ControlTokens.fieldErrorGap else ControlTokens.statusGap),
        modifier = modifier.testTag(tag).semantics(mergeDescendants = true) {
            if (spoken) liveRegion = LiveRegionMode.Polite else hideFromAccessibility()
        },
    ) {
        HhIcon(
            icon,
            size = if (field) ControlTokens.fieldErrorIcon else ControlTokens.statusIcon,
            filled = icon == Icons.ERROR,
            tint = c.danger,
            modifier = Modifier.testTag("${tag}_icon"),
        )
        Text(text, style = style, color = c.danger)
    }
}

/**
 * Hand-off §7 person chip: a chip's size and shape, `surf`, a 12 dp dot in the person's [color] 8 from their [name].
 * A toggle, filled in `accent` while [selected]. [busy] swaps the dot for the `schedule` glyph and the state for "Saving",
 * so the chip keeps its size; the caller still disables it.
 */
@Composable
fun HhPersonChip(
    name: String,
    color: Color,
    tag: String,
    enabled: Boolean,
    selected: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Culvery.colors
    val ink = if (selected) c.accentInk else c.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(if (selected) c.accent else c.surf)
            .semantics { stateDescription = if (busy) BUSY_STATE else if (selected) CHOSEN else NOT_CHOSEN }
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        Box(Modifier.size(ControlTokens.chipDot), contentAlignment = Alignment.Center) {
            if (busy) {
                HhIcon(Icons.SCHEDULE, size = ControlTokens.chipBusyIcon, tint = ink, modifier = Modifier.requiredSize(ControlTokens.chipBusyIcon))
            } else {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(color))
            }
        }
        Text(name, style = ControlType.chip, color = ink, maxLines = 1)
    }
}

/**
 * A confirmation card on `dangerSoft`: [question] above two buttons. Keep sits on the left where the action that opened
 * the card was, so a double tap on it is harmless; the confirming button is [ButtonTone.Destroy]. TalkBack announces
 * [title] as the card appears, which it otherwise does silently under the focus it leaves on the sheet; [question]
 * shows it unless the card says more.
 */
@Composable
fun HhConfirmCard(
    tag: String,
    title: String,
    keep: String,
    keepTag: String,
    confirm: String,
    confirmTag: String,
    busy: Boolean,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
    confirmIcon: String? = null,
    question: @Composable () -> Unit = { Text(title, style = ControlType.confirmTitle, color = Culvery.colors.ink) },
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ControlTokens.confirmGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { paneTitle = title }
            .readAsOne()
            .fillMaxWidth()
            .clip(RoundedCornerShape(ControlTokens.confirmRadius))
            .background(Culvery.colors.dangerSoft)
            .padding(ControlTokens.confirmPadding),
    ) {
        question()
        Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhButton(
                keep, onKeep, Modifier.weight(1f).testTag(keepTag),
                size = ButtonSize.Large, enabled = !busy, on = ButtonSurface.DangerSoft,
            )
            HhButton(
                confirm, onConfirm, Modifier.weight(1f).testTag(confirmTag),
                tone = ButtonTone.Destroy, size = ButtonSize.Large, icon = confirmIcon, enabled = !busy, on = ButtonSurface.DangerSoft,
            )
        }
    }
}

/**
 * A person colour to pick (9 §4.11): ringed, with a tick, when [chosen]; [takenBy] someone else, faded with a block,
 * disabled and said. The ring and its gap sit inside the swatch's [ControlTokens.swatchSlot].
 */
@Composable
fun HhSwatch(color: Color, name: String, chosen: Boolean, takenBy: String?, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    val taken = takenBy != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .semantics {
                contentDescription = name
                if (takenBy != null) stateDescription = "used by $takenBy"
            }
            .size(ControlTokens.swatchSlot)
            .then(if (chosen) Modifier.border(ControlTokens.swatchRing, c.ink, CircleShape) else Modifier)
            .clip(CircleShape)
            .selectable(selected = chosen, enabled = !taken, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(ControlTokens.swatch)
                .alpha(if (taken) ControlTokens.DISABLED_ALPHA else 1f)
                .clip(CircleShape)
                .background(color),
        )
        if (taken) {
            HhIcon(Icons.BLOCK, size = ControlTokens.swatchBlock, tint = PersonChipInk, modifier = Modifier.testTag("${tag}_block"))
        } else if (chosen) {
            HhIcon(Icons.CHECK, size = ControlTokens.swatchCheck, tint = PersonChipInk, modifier = Modifier.testTag("${tag}_check"))
        }
    }
}

/** The person colours to pick from, a radio group, wrapping; swatch i is tagged [tagPrefix] + i. [taken] maps a colour to who has it. */
@Composable
fun HhSwatchGrid(chosen: Long?, taken: Map<Long, String>, tagPrefix: String, onPick: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.swatchSpacing),
        verticalArrangement = Arrangement.spacedBy(ControlTokens.swatchSpacing),
        modifier = Modifier.selectableGroup(),
    ) {
        PersonPalette.colors.forEachIndexed { i, colour ->
            HhSwatch(Color(colour), PersonPalette.names[i], chosen = chosen == colour, takenBy = taken[colour], tag = "$tagPrefix$i") { onPick(colour) }
        }
    }
}

/** The switch's size (9 §4.12, the handoff's 88 x 50 px). */
object SwitchTokens {
    val width = 59.dp
    val height = 33.dp
    val knob = 25.dp
    val inset = 4.dp
}

/**
 * A switch drawn in the theme's colours: `accent` track and `accentInk` knob when on, `surf3` and `mute` when off. A
 * disabled one that is on stays `accent` at the disabled fade. It only draws: the row around it is the switch
 * TalkBack and the finger use. The knob slides on a change, never on first composition.
 */
@Composable
fun HhSwitch(checked: Boolean, tag: String, enabled: Boolean = true) {
    val c = Culvery.colors
    val knobX by animateDpAsState(
        if (checked) SwitchTokens.width - SwitchTokens.knob - SwitchTokens.inset else SwitchTokens.inset,
        label = "switchKnob",
    )
    val track = when {
        !checked -> c.surf3
        enabled -> c.accent
        else -> c.accent.copy(alpha = ControlTokens.DISABLED_ALPHA)
    }
    Box(
        Modifier
            .testTag(tag)
            .size(SwitchTokens.width, SwitchTokens.height)
            .alpha(if (enabled || checked) 1f else ControlTokens.DISABLED_ALPHA)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(knobX.roundToPx(), 0) }
                .size(SwitchTokens.knob)
                .background(if (checked) c.accentInk else c.mute, CircleShape),
        )
    }
}

/**
 * A value in [range], reported as it moves ([onDrag]) and again when let go ([onLetGo]); [label], beside it, shows the
 * value under the thumb. Spoken "[spokenName], [spokenState]": "Kitchen volume, 30 percent". Not [enabled], it keeps
 * [percent] on `surf3` over `surf2`, ignores touch and offers TalkBack no way to move it; [spokenState] then says why
 * ("Off"). A [trackHeight] thickens the track. The value let go stays until [percent] changes. With a [holdCeilingMs] it
 * stays until [percent] reaches it instead, or that long has passed: for a value whose own commands are reported back late.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HhPercentSlider(
    name: String,
    percent: Int,
    tag: String,
    modifier: Modifier = Modifier,
    range: IntRange = 0..PERCENT_MAX,
    steps: Int = 0,
    spokenName: String = name,
    spokenState: (Int) -> String = { "$it percent" },
    label: (@Composable (Int) -> Unit)? = null,
    onDrag: ((Int) -> Unit)? = null,
    enabled: Boolean = true,
    trackHeight: Dp? = null,
    holdCeilingMs: Long = 0,
    onLetGo: (Int) -> Unit,
) {
    val c = Culvery.colors
    // A check mid-drag can re-read [percent]; the drag is kept apart so it survives that.
    var dragging by remember { mutableStateOf<Float?>(null) }
    // The value let go stays until [percent] changes, so the thumb doesn't jump back while the command is on its way.
    var held by remember(if (holdCeilingMs > 0) null else percent) { mutableStateOf<Float?>(null) }
    if (holdCeilingMs > 0) {
        LaunchedEffect(held) {
            if (held != null) {
                delay(holdCeilingMs)
                held = null
            }
        }
        LaunchedEffect(percent, held) { if (held?.roundToInt() == percent) held = null }
    }
    val shown = dragging ?: held ?: percent.toFloat()
    val colors = SliderDefaults.colors(
        thumbColor = c.accent,
        activeTrackColor = c.accent,
        inactiveTrackColor = c.surf3,
        disabledThumbColor = c.surf3,
        disabledActiveTrackColor = c.surf3,
        disabledInactiveTrackColor = c.surf2,
    )
    Slider(
        value = shown,
        onValueChange = {
            dragging = it
            onDrag?.invoke(it.roundToInt())
        },
        onValueChangeFinished = {
            dragging?.let {
                held = it
                onLetGo(it.roundToInt())
            }
            dragging = null
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = steps,
        enabled = enabled,
        colors = colors,
        track = {
            SliderDefaults.Track(
                sliderState = it,
                modifier = if (trackHeight != null) Modifier.height(trackHeight) else Modifier,
                enabled = enabled,
                colors = colors,
            )
        },
        modifier = if (enabled) {
            modifier.testTag(tag).semantics {
                contentDescription = spokenName
                stateDescription = spokenState(shown.roundToInt())
            }
        } else {
            // Material leaves its SetProgress on a disabled slider; TalkBack would still move it.
            modifier.clearAndSetSemantics {
                testTag = tag
                contentDescription = spokenName
                stateDescription = spokenState(shown.roundToInt())
                progressBarRangeInfo = ProgressBarRangeInfo(shown, range.first.toFloat()..range.last.toFloat(), steps)
                disabled()
            }
        },
    )
    label?.invoke(shown.roundToInt())
}

private const val PERCENT_MAX = 100
