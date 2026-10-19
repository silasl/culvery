package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import uk.co.siland.culvery.core.plugin.HomeLayout

/** Today on the left at a fixed width; the right cards share the rest, each half the column when two (9 §4.2, §6.1). */
@Composable
fun HomeGrid(layout: HomeLayout, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ShellDimens.gridGap)) {
        layout.left?.let { card ->
            key(card.id) { Box(Modifier.width(ShellDimens.leftColumn).fillMaxHeight()) { card.content() } }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ShellDimens.gridGap)) {
            layout.right.forEach { card ->
                key(card.id) { Box(Modifier.fillMaxWidth().weight(1f)) { card.content() } }
            }
        }
    }
}
