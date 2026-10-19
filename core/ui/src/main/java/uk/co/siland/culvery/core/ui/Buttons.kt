package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

internal data class ButtonColors(val fill: Color, val ink: Color)

/** Opacity of a disabled button's `mute` label and icon. */
const val DISABLED_INK_ALPHA = 0.5f

/** A button's fill and label colour. Disabled, every tone is the quiet fill with a `mute` label and icon at [DISABLED_INK_ALPHA]. */
internal fun buttonColors(tone: ButtonTone, enabled: Boolean, on: ButtonSurface, colors: HhColors): ButtonColors {
    val quiet = quietFill(on, colors)
    if (!enabled) return ButtonColors(quiet, colors.mute.copy(alpha = DISABLED_INK_ALPHA))
    return when (tone) {
        ButtonTone.Primary -> ButtonColors(colors.accent, colors.accentInk)
        ButtonTone.Danger -> ButtonColors(quiet, colors.danger)
        ButtonTone.Destroy -> ButtonColors(colors.danger, colors.dangerInk)
        ButtonTone.Quiet -> ButtonColors(quiet, colors.ink)
    }
}

/** A button's spoken name, kept apart from other descriptions so the shared test helper can check it starts with the label. */
val SpokenNameKey = SemanticsPropertyKey<String>("SpokenName")

/**
 * The one button (4d-1 §4.1): a pill at least [size]'s height, a one-line label, an optional icon before it. Disabled,
 * every [tone] is the quiet fill with a faded `mute` label and does nothing. [spokenName] names a button whose label repeats
 * along a list: it starts with [text] and adds the subject ("Disconnect Google Calendar, alex@example.com"). [compact]
 * narrows a Regular button's side padding so its label fits a crowded row. [clickLabel] is what TalkBack offers when
 * the name doesn't say what a tap does; [secondLine] shows a state under the label ("Holiday mode", "Off"), and
 * [spokenName] then reads both as one ("Holiday mode, off").
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
) {
    val (fill, ink) = buttonColors(tone, enabled, on, Culvery.colors)
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
        else -> ButtonTokens.regularPaddingH
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (large) ButtonTokens.largeIconGap else ButtonTokens.regularIconGap,
            Alignment.CenterHorizontally,
        ),
        modifier = modifier
            .heightIn(min = if (large) ButtonTokens.largeHeight else ButtonTokens.regularHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
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
            .hhClickable(onClick, label = clickLabel, enabled = enabled)
            .padding(start = start, end = end),
    ) {
        if (icon != null) HhIcon(icon, size = ButtonTokens.icon, tint = ink)
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
    }
}
