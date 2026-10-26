package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import uk.co.siland.culvery.core.plugin.HomeLayout

/**
 * Today on the left at a fixed width; the right cards share the rest, each half the column when two (9 §4.2, §6.1).
 * A utility card tops the column and the right cards share one row beneath it (10 §4.9); the One-tap tray shares the
 * top row with it, or stands at its left (11 §4.4).
 */
@Composable
fun HomeGrid(layout: HomeLayout, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ShellDimens.gridGap)) {
        layout.left?.let { card ->
            key(card.id) { Box(Modifier.width(ShellDimens.leftColumn).fillMaxHeight()) { card.content() } }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ShellDimens.gridGap)) {
            val utility = layout.utility
            if (utility != null || layout.tray.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ShellDimens.gridGap),
                ) {
                    if (utility != null) key(utility.id) { Box(Modifier.fillMaxHeight().weight(1f)) { utility.content() } }
                    if (layout.tray.isNotEmpty()) HomeTray(layout.tray, Modifier.width(ShellDimens.trayWidth).fillMaxHeight())
                }
                if (layout.right.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().height(ShellDimens.cardRow),
                        horizontalArrangement = Arrangement.spacedBy(ShellDimens.gridGap),
                    ) {
                        layout.right.forEach { card ->
                            key(card.id) { Box(Modifier.fillMaxHeight().weight(1f)) { card.content() } }
                        }
                    }
                }
            } else {
                layout.right.forEach { card ->
                    key(card.id) { Box(Modifier.fillMaxWidth().weight(1f)) { card.content() } }
                }
            }
        }
    }
}
