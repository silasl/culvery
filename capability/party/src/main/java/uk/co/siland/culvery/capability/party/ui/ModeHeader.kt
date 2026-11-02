package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.SettingsTokens
import uk.co.siland.culvery.core.ui.hhClickable

private val ICON_TILE = 64.dp
private val ICON_SIZE = 32.dp

/**
 * A mode's page head (15b §4.2): its [icon] and [name], then [trailing] at the far end. With [onIcon] and [onName] a tap
 * on either changes it; a header given neither, Holiday mode's, has nothing to tap.
 */
@Composable
fun ModeHeader(icon: String, name: String, onIcon: (() -> Unit)?, onName: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ModeTitle(icon, name, onIcon, onName, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun ModeTitle(icon: String, name: String, onIcon: (() -> Unit)?, onName: (() -> Unit)?, modifier: Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap),
        modifier = modifier.testTag("mode_header"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .testTag("mode_header_icon")
                .then(if (onIcon != null) Modifier.hhClickable(onIcon, label = ModesWords.changeIcon(name), spoken = "$name icon") else Modifier)
                .size(ICON_TILE)
                .clip(RoundedCornerShape(16.dp))
                .background(c.accentSoft),
        ) {
            HhIcon(icon, size = ICON_SIZE, tint = c.accent)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag("mode_header_name")
                .then(if (onName != null) Modifier.hhClickable(onName, label = ModesWords.renameMode(name)) else Modifier)
                .heightIn(min = ButtonTokens.regularHeight),
        ) {
            Text(name, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            if (onName != null) HhIcon(Icons.EDIT, size = 20.dp, tint = c.mute)
        }
    }
}
