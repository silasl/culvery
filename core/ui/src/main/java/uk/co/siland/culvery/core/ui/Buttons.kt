package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Button values (hand-off §7; 4d-1 §4.1). Both sizes are pills: the radius is half the height. */
object ButtonTokens {
    val regularHeight = 48.dp
    val regularPaddingH = 20.dp
    val regularIconPaddingStart = 14.dp
    val regularIconGap = 6.dp

    val largeHeight = 60.dp
    val largePaddingH = 26.dp
    val largeIconGap = 8.dp

    val icon = 24.dp
}

/** [Regular] 48 dp with a 15 sp / 700 label; [Large] 60 dp with a 17 sp / 700 label (sheet footers, pickers). */
enum class ButtonSize { Regular, Large }

/** What a button sits on; its quiet fill is one step above it (4d-1 Decision 9). Sheets are [Bg]. */
enum class ButtonSurface { Bg, Surf, DangerSoft }

/** The quiet and disabled fill on [on]. On light `bg` it is `surf`: `surf2` there is 1.04:1 and the button vanishes. */
internal fun quietFill(on: ButtonSurface, colors: HhColors): Color = when (on) {
    ButtonSurface.Bg -> if (colors.isLight) colors.surf else colors.surf2
    ButtonSurface.Surf -> colors.surf2
    ButtonSurface.DangerSoft -> colors.surf
}

internal data class ButtonColors(val fill: Color, val ink: Color)

/** A button's fill and label colour. Disabled, every tone is the quiet fill with a `mute` label (4d-1 Decision 8). */
internal fun buttonColors(tone: ButtonTone, enabled: Boolean, on: ButtonSurface, colors: HhColors): ButtonColors {
    val quiet = quietFill(on, colors)
    if (!enabled) return ButtonColors(quiet, colors.mute)
    return when (tone) {
        ButtonTone.Primary -> ButtonColors(colors.accent, colors.accentInk)
        ButtonTone.Danger -> ButtonColors(quiet, colors.danger)
        ButtonTone.Destroy -> ButtonColors(colors.danger, colors.dangerInk)
        ButtonTone.Plain, ButtonTone.Quiet -> ButtonColors(quiet, colors.ink)
    }
}

/**
 * The one button (4d-1 §4.1): a pill at least [size]'s height, a one-line label, an optional icon before it. Disabled,
 * every [tone] is the quiet fill with a `mute` label and does nothing. [on] is what the button sits on.
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
) {
    val (fill, ink) = buttonColors(tone, enabled, on, Culvery.colors)
    val large = size == ButtonSize.Large
    val start = when {
        large -> ButtonTokens.largePaddingH
        icon != null -> ButtonTokens.regularIconPaddingStart
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
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = start, end = if (large) ButtonTokens.largePaddingH else ButtonTokens.regularPaddingH),
    ) {
        if (icon != null) HhIcon(icon, size = ButtonTokens.icon, tint = ink)
        Text(
            text,
            style = if (large) ControlType.button else HhType.buttonLabel,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
