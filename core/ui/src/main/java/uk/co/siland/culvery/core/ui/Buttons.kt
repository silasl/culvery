package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Button values (hand-off §7; 4d-1 §4.1). Both sizes are pills: the radius is half the height. */
object ButtonTokens {
    val regularHeight = 48.dp
    val regularPaddingH = 20.dp
    val regularIconPaddingStart = 14.dp
    val regularIconGap = 6.dp

    // A Regular button in a crowded row (the Home music card's controls).
    val compactPaddingH = 10.dp

    val largeHeight = 60.dp
    val largePaddingH = 26.dp
    val largeIconGap = 8.dp

    val icon = 24.dp
    val roundIcon = 28.dp
    val disabledOutline = 1.dp

    // A footer's reason line sits 8 dp above its button (9 §4.8); under it, 9 dp below with a 16 dp `info` 5 dp before the text.
    val footerReasonGap = 8.dp
    val footerNoteGap = 9.dp
    val footerNoteIcon = 16.dp
    val footerNoteIconGap = 5.dp

    // A round button held down steps once after this long, then again every interval (9 §4.8).
    const val HOLD_DELAY_MILLIS = 500L
    const val HOLD_INTERVAL_MILLIS = 100L
}

/**
 * [Primary] `accent` with `accentInk`; [Quiet] the quiet fill with `ink`; [Danger] the quiet fill with `danger`
 * (Delete); [Destroy] `danger` with `dangerInk`, only a confirm card's confirming button.
 */
enum class ButtonTone { Primary, Quiet, Danger, Destroy }

/** [Regular] 48 dp with a 15 sp / 700 label; [Large] 60 dp with a 17 sp / 700 label (sheet footers, pickers). */
enum class ButtonSize { Regular, Large }

/** What a button sits on; its quiet fill is one step above it (4d-1 Decision 9). Sheets are [Bg]. */
enum class ButtonSurface { Bg, Surf, DangerSoft }

/** The quiet and disabled fill on [on]. On light `bg` it is `surf`: `surf2` there is 1.04:1 and the button vanishes. */
fun quietFill(on: ButtonSurface, colors: HhColors): Color = when (on) {
    ButtonSurface.Bg -> if (colors.isLight) colors.surf else colors.surf2
    ButtonSurface.Surf -> colors.surf2
    ButtonSurface.DangerSoft -> colors.surf
}

/** [outline] is null for an enabled button. */
internal data class ButtonColors(val fill: Color, val ink: Color, val outline: Color?)

/** A button's fill, label colour and outline. Disabled, every tone is an outline with a `mute` label and icon (9 §6.4). */
internal fun buttonColors(tone: ButtonTone, enabled: Boolean, on: ButtonSurface, colors: HhColors): ButtonColors {
    val quiet = quietFill(on, colors)
    if (!enabled) return ButtonColors(Color.Transparent, colors.mute, colors.surf3)
    return when (tone) {
        ButtonTone.Primary -> ButtonColors(colors.accent, colors.accentInk, null)
        ButtonTone.Danger -> ButtonColors(quiet, colors.danger, null)
        ButtonTone.Destroy -> ButtonColors(colors.danger, colors.dangerInk, null)
        ButtonTone.Quiet -> ButtonColors(quiet, colors.ink, null)
    }
}

internal const val BUSY_STATE = "Saving"

/** A button's spoken name, kept apart from other descriptions so the shared test helper can check it starts with the label. */
val SpokenNameKey = SemanticsPropertyKey<String>("SpokenName")

/**
 * The one button (4d-1 §4.1): a pill at least [size]'s height, a one-line label, an optional icon before it and an optional
 * [trailingIcon] after it ("Door history ›"). Disabled, every tone is an outline with a `mute` label (9 §6.4) and does nothing. [spokenName] names a button whose label repeats
 * along a list: it starts with [text] and adds the subject ("Disconnect Google Calendar, alex@example.com"). [compact]
 * narrows a Regular button's side padding so its label fits a crowded row. [clickLabel] is what TalkBack offers when
 * the name doesn't say what a tap does; [secondLine] shows a state under the label ("Holiday mode", "Off"), and
 * [spokenName] then reads both as one ("Holiday mode, off"). [busy] changes only what is drawn inside: its icon becomes
 * `schedule` and its state "Saving", so the button keeps its size and place; the caller still disables it.
 */
@Composable
fun HhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Quiet,
    size: ButtonSize = ButtonSize.Regular,
    icon: String? = null,
    enabled: Boolean = true,
    on: ButtonSurface,
    spokenName: String? = null,
    compact: Boolean = false,
    clickLabel: String? = null,
    secondLine: String? = null,
    trailingIcon: String? = null,
    busy: Boolean = false,
) {
    val (fill, ink, outline) = buttonColors(tone, enabled, on, Culvery.colors)
    val large = size == ButtonSize.Large
    val start = when {
        large -> ButtonTokens.largePaddingH
        compact -> ButtonTokens.compactPaddingH
        icon != null -> ButtonTokens.regularIconPaddingStart
        else -> ButtonTokens.regularPaddingH
    }
    val end = when {
        large -> ButtonTokens.largePaddingH
        compact -> ButtonTokens.compactPaddingH
        trailingIcon != null -> ButtonTokens.regularIconPaddingStart
        else -> ButtonTokens.regularPaddingH
    }
    val pill = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (large) ButtonTokens.largeIconGap else ButtonTokens.regularIconGap,
            Alignment.CenterHorizontally,
        ),
        modifier = modifier
            .heightIn(min = if (large) ButtonTokens.largeHeight else ButtonTokens.regularHeight)
            .buttonFill(fill, outline, pill)
            .then(
                if (spokenName != null) {
                    Modifier.semantics {
                        contentDescription = spokenName
                        // Two lines are spoken as one sentence ("Holiday mode, off"), not as the label then the line.
                        if (secondLine == null) this[SpokenNameKey] = spokenName
                    }
                } else {
                    Modifier
                },
            )
            .then(if (busy) Modifier.semantics { stateDescription = BUSY_STATE } else Modifier)
            .hhClickable(onClick, label = clickLabel, enabled = enabled)
            .padding(start = start, end = end),
    ) {
        if (icon != null && !busy) HhIcon(icon, size = ButtonTokens.icon, tint = ink)
        // The busy glyph is drawn over the idle one's space, so the button keeps its width.
        if (icon != null && busy) {
            Box(contentAlignment = Alignment.Center) {
                HhIcon(icon, size = ButtonTokens.icon, tint = Color.Transparent)
                HhIcon(Icons.SCHEDULE, size = ButtonTokens.icon, tint = ink, modifier = Modifier.matchParentSize())
            }
        }
        val shown = Modifier.weight(1f, fill = false).then(if (spokenName != null) Modifier.shownNotSpoken() else Modifier)
        val style = if (large) ControlType.button else HhType.buttonLabel
        if (secondLine == null) {
            Text(text, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = shown)
        } else {
            Column(shown) {
                Text(text, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(secondLine, style = HhType.secondary, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingIcon != null) HhIcon(trailingIcon, size = ButtonTokens.icon, tint = ink)
    }
}

/**
 * A round [size] button in [tone]'s colours, named by [spoken] (9 §6.4); disabled it is the outline and does nothing.
 * Tag it through [modifier]. With [repeatWhileHeld] a tap steps once and a press held for 500 ms steps again, then every
 * 100 ms until let go; the release after a hold adds none, and TalkBack's click is one step.
 */
@Composable
fun HhRoundButton(
    icon: String,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Quiet,
    enabled: Boolean = true,
    on: ButtonSurface,
    size: Dp = ButtonTokens.regularHeight,
    iconSize: Dp = ButtonTokens.roundIcon,
    repeatWhileHeld: Boolean = false,
) {
    val (fill, ink, outline) = buttonColors(tone, enabled, on, Culvery.colors)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .buttonFill(fill, outline, CircleShape)
            .semantics { contentDescription = spoken }
            .then(if (repeatWhileHeld) Modifier.heldClickable(onClick, enabled) else Modifier.hhClickable(onClick, enabled = enabled)),
    ) {
        HhIcon(icon, size = iconSize, tint = ink)
    }
}

/** A button click that repeats while the press is held; the click that follows a hold is swallowed. */
@Composable
private fun Modifier.heldClickable(onClick: () -> Unit, enabled: Boolean): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val step by rememberUpdatedState(onClick)
    val fired = remember { BooleanArray(1) }
    // A hold that ended in a cancel must not swallow the next tap.
    LaunchedEffect(source) { source.interactions.collect { if (it is PressInteraction.Press) fired[0] = false } }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(ButtonTokens.HOLD_DELAY_MILLIS)
        fired[0] = true
        while (true) {
            step()
            delay(ButtonTokens.HOLD_INTERVAL_MILLIS)
        }
    }
    return clickable(source, LocalIndication.current, enabled = enabled, role = Role.Button) {
        if (fired[0]) fired[0] = false else step()
    }
}

/**
 * A sheet's footer: a Primary [HhButton] at [ButtonSize.Large] across the width, tagged [tag]. Disabled with a [reason], the
 * reason shows above it (`${tag}_reason`) and is the button's state for TalkBack, so it is heard once ("Add event, Add a
 * title first, disabled"). The reason is optional (9 §6.4). With [below], the reason sits under the button instead, with
 * the `info` icon, and while enabled the [hint] does (`${tag}_hint`, "Repeats are optional"). While [busy] the
 * button is drawn busy and the line stays as it was, so nothing under or beside it moves.
 */
@Composable
fun HhFooterButton(
    text: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    enabled: Boolean = true,
    reason: String? = null,
    below: Boolean = false,
    hint: String? = null,
    busy: Boolean = false,
) {
    Column(modifier) {
        if (!below && !enabled && reason != null) {
            Text(
                reason,
                style = HhType.body,
                color = Culvery.colors.mute,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ButtonTokens.footerReasonGap)
                    .testTag("${tag}_reason")
                    .shownNotSpoken(),
            )
        }
        HhButton(
            text,
            onClick,
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics { if (!busy && !enabled && reason != null) stateDescription = reason },
            tone = ButtonTone.Primary,
            size = ButtonSize.Large,
            icon = icon,
            enabled = enabled,
            on = ButtonSurface.Bg,
            busy = busy,
        )
        if (below) {
            when {
                !enabled && reason != null -> FooterNote(reason, "${tag}_reason", spoken = false)
                (enabled || busy) && hint != null -> FooterNote(hint, "${tag}_hint", spoken = true)
            }
        }
    }
}

/** A footer's line under its button; the reason isn't [spoken], since the button's state already says it. */
@Composable
private fun FooterNote(text: String, tag: String, spoken: Boolean) {
    val mute = Culvery.colors.mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ButtonTokens.footerNoteIconGap, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ButtonTokens.footerNoteGap)
            .testTag(tag)
            .then(if (spoken) Modifier.semantics(mergeDescendants = true) {} else Modifier.shownNotSpoken()),
    ) {
        HhIcon(Icons.INFO, size = ButtonTokens.footerNoteIcon, tint = mute, modifier = Modifier.testTag("${tag}_icon"))
        Text(text, style = HhType.secondary, color = mute, modifier = Modifier.weight(1f, fill = false))
    }
}

/** A button's [fill] in [shape], with the disabled [outline] when it has one. */
private fun Modifier.buttonFill(fill: Color, outline: Color?, shape: Shape): Modifier =
    clip(shape).background(fill).then(if (outline != null) Modifier.border(ButtonTokens.disabledOutline, outline, shape) else Modifier)
