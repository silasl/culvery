package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Control values from the hand-off (§7 chips, the title field, person chips) and 4a's colour swatches. */
object ControlTokens {
    // Choice chips: 48 dp, padding 0 18, radius 24, 8 apart; a 12 dp dot or 20 dp icon 8 from the label.
    val chipHeight = 48.dp
    val chipPaddingH = 18.dp
    val chipRadius = 24.dp
    val chipGap = 8.dp
    val chipIconGap = 8.dp
    val chipDot = 12.dp
    val chipIcon = 20.dp

    /** Hand-off §7: a disabled chip is drawn at 38% and stays tappable; a chip's secondary text is at 72%. */
    const val DISABLED_ALPHA = 0.38f
    const val SECONDARY_ALPHA = 0.72f

    // Text field (the hand-off's title field): 64 dp, radius 18, `surf`, padding 0 20, a 2 dp `accent` border while focused.
    val fieldHeight = 64.dp
    val fieldRadius = 18.dp
    val fieldPaddingH = 20.dp
    val fieldBorder = 2.dp

    // The sheets' footer button (hand-off §7): 60 dp, radius 30, padding 0 26, a 24 dp icon 8 from the label.
    val buttonHeight = 60.dp
    val buttonRadius = 30.dp
    val buttonPaddingH = 26.dp
    val buttonIcon = 24.dp
    val buttonIconGap = 8.dp

    // A confirmation, as the calendar's delete confirmation: `dangerSoft`, radius 24, padding 20, 16 between blocks,
    // buttons 12 apart.
    val confirmRadius = 24.dp
    val confirmPadding = 20.dp
    val confirmGap = 16.dp
    val confirmButtonGap = 12.dp

    // Colour swatch (4a; not in the spec): a 44 dp circle, 12 apart; chosen, a 3 dp `ink` ring 3 dp outside it; taken,
    // at 38% with a 2 dp `ink` line across it.
    val swatch = 44.dp
    val swatchGap = 12.dp
    val swatchRing = 3.dp
    val swatchRingGap = 3.dp
    val swatchStrike = 2.dp

    /** Where the taken line starts and ends, as a share of the swatch: on the circle, corner to corner. */
    const val STRIKE_INSET = 0.15f
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

    /** 18 sp / 700: a confirmation's question. */
    val confirmTitle = HhType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.W700)
}

/**
 * Hand-off §7 chip: 48 dp, padding 0 18, radius 24, 16 sp / 600; `surf`/`ink`, or [selectedColor]/[selectedInk] when
 * selected. A disabled chip is drawn at 38% but stays tappable, so a tap can explain why.
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
    secondary: String? = null,
    leading: (@Composable (ink: Color) -> Unit)? = null,
) {
    val c = Culvery.colors
    val ink = if (selected) selectedInk else c.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.selected = selected }
            .alpha(if (enabled) 1f else ControlTokens.DISABLED_ALPHA)
            .height(ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(if (selected) selectedColor else c.surf)
            .clickable(onClick = onClick)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        leading?.invoke(ink)
        Text(label, style = ControlType.chip, color = ink, maxLines = 1)
        if (secondary != null) {
            Text(secondary, style = ControlType.chipSecondary, color = ink.copy(alpha = ControlTokens.SECONDARY_ALPHA), maxLines = 1)
        }
    }
}

/** 64 dp, `surf`, 22 sp / 600, one line; [placeholder] while empty; the keyboard's Done calls [onDone] and never saves. */
@Composable
fun HhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onDone: () -> Unit = {},
) {
    val c = Culvery.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ControlTokens.fieldRadius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = readOnly,
        textStyle = ControlType.field.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .testTag(tag)
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
                    .then(if (focused) Modifier.border(ControlTokens.fieldBorder, c.accent, shape) else Modifier)
                    .padding(horizontal = ControlTokens.fieldPaddingH),
            ) {
                if (value.isEmpty()) Text(placeholder, style = ControlType.field, color = c.mute, maxLines = 1)
                inner()
            }
        },
    )
}

/** Hand-off §7 person chip: a chip's size and shape, `surf`, a 12 dp dot in the person's [color] 8 from their [name]. */
@Composable
fun HhPersonChip(name: String, color: Color, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(ControlTokens.chipHeight)
            .clip(RoundedCornerShape(ControlTokens.chipRadius))
            .background(c.surf)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ControlTokens.chipPaddingH),
    ) {
        Box(Modifier.size(ControlTokens.chipDot).clip(CircleShape).background(color))
        Text(name, style = ControlType.chip, color = c.ink, maxLines = 1)
    }
}

/**
 * [Primary] `accent`; [Plain] `surf2`; [Quiet] `surf` (Keep, on a `dangerSoft` card); [Danger] `surf2` with `danger`
 * text; [Destroy] `danger` with `dangerInk`.
 */
enum class ButtonTone { Primary, Plain, Quiet, Danger, Destroy }

/** The sheets' footer button (hand-off §7): 60 dp, radius 30, 17 sp / 700, an optional icon. Disabled, `surf2` and `mute`. */
@Composable
fun HhSheetButton(
    text: String,
    tone: ButtonTone,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
) {
    val c = Culvery.colors
    val (background, ink) = if (!enabled) {
        c.surf2 to c.mute
    } else {
        when (tone) {
            ButtonTone.Primary -> c.accent to c.accentInk
            ButtonTone.Plain -> c.surf2 to c.ink
            ButtonTone.Quiet -> c.surf to c.ink
            ButtonTone.Danger -> c.surf2 to c.danger
            ButtonTone.Destroy -> c.danger to c.dangerInk
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.buttonIconGap, Alignment.CenterHorizontally),
        modifier = modifier
            .testTag(tag)
            .height(ControlTokens.buttonHeight)
            .clip(RoundedCornerShape(ControlTokens.buttonRadius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ControlTokens.buttonPaddingH),
    ) {
        if (icon != null) HhIcon(icon, size = ControlTokens.buttonIcon, tint = ink)
        Text(text, style = ControlType.button, color = ink, maxLines = 1)
    }
}

/**
 * A confirmation card on `dangerSoft`: [question] above two buttons. Keep, in [keepTone], sits on the left where the
 * action that opened the card was, so a double tap on it is harmless; the confirming button is [ButtonTone.Destroy].
 */
@Composable
fun HhConfirmCard(
    tag: String,
    keep: String,
    keepTone: ButtonTone,
    keepTag: String,
    confirm: String,
    confirmTag: String,
    busy: Boolean,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
    confirmIcon: String? = null,
    question: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ControlTokens.confirmGap),
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ControlTokens.confirmRadius))
            .background(Culvery.colors.dangerSoft)
            .padding(ControlTokens.confirmPadding),
    ) {
        question()
        Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.confirmButtonGap), modifier = Modifier.fillMaxWidth()) {
            HhSheetButton(keep, keepTone, enabled = !busy, tag = keepTag, onClick = onKeep, modifier = Modifier.weight(1f))
            HhSheetButton(
                confirm, ButtonTone.Destroy, enabled = !busy, tag = confirmTag, onClick = onConfirm,
                modifier = Modifier.weight(1f), icon = confirmIcon,
            )
        }
    }
}

/** A person colour to pick (4a design §4.4): ringed when [chosen]; [taken] by someone else, struck through and disabled. */
@Composable
fun HhSwatch(color: Color, chosen: Boolean, taken: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    val outside = ControlTokens.swatchRing + ControlTokens.swatchRingGap
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .semantics { selected = chosen }
            .size(ControlTokens.swatch + outside * 2)
            .then(if (chosen) Modifier.border(ControlTokens.swatchRing, c.ink, CircleShape) else Modifier)
            .clip(CircleShape)
            .clickable(enabled = !taken, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(ControlTokens.swatch)
                // Before the alpha, so the line stays solid over the faded colour.
                .drawWithContent {
                    drawContent()
                    if (taken) {
                        val inset = size.width * ControlTokens.STRIKE_INSET
                        drawLine(c.ink, Offset(inset, inset), Offset(size.width - inset, size.height - inset), ControlTokens.swatchStrike.toPx())
                    }
                }
                .alpha(if (taken) ControlTokens.DISABLED_ALPHA else 1f)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** The person colours to pick from, wrapping, 12 apart; swatch i is tagged [tagPrefix] + i. */
@Composable
fun HhSwatchGrid(chosen: Long?, taken: Set<Long>, tagPrefix: String, onPick: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
        verticalArrangement = Arrangement.spacedBy(ControlTokens.swatchGap),
    ) {
        PersonPalette.colors.forEachIndexed { i, colour ->
            HhSwatch(Color(colour), chosen = chosen == colour, taken = colour in taken, tag = "$tagPrefix$i") { onPick(colour) }
        }
    }
}

/** A Show-style switch in the theme's colours: `accent` when on; a disabled one that is on stays faded `accent`. */
@Composable
fun HhSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String, enabled: Boolean = true) {
    val c = Culvery.colors
    val faded = c.accent.copy(alpha = ControlTokens.DISABLED_ALPHA)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.testTag(tag),
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.accentInk,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.mute,
            uncheckedTrackColor = c.surf2,
            uncheckedBorderColor = c.mute,
            disabledCheckedThumbColor = c.accentInk,
            disabledCheckedTrackColor = faded,
            disabledCheckedBorderColor = faded,
        ),
    )
}
