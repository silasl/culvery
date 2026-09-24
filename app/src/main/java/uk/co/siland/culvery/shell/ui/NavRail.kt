package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.TabItem

private val HomeTab = TabItem(HOME_TAB_ID, "Home", "home")

/** Who is signed in now shows in the status bar (hand-off §7), not here. */
@Composable
fun NavRail(
    tabs: List<TabItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = Culvery.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(108.dp)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(c.line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            .padding(top = 16.dp, bottom = 20.dp),
    ) {
        (listOf(HomeTab) + tabs).forEach { tab ->
            RailItem(tab, selected = tab.id == selectedId) { onSelect(tab.id) }
        }
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            modifier = Modifier
                .testTag("rail_settings")
                .size(80.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surf2)
                .clickable(onClick = onOpenSettings),
        ) {
            HhIcon("settings", size = 30.dp, tint = c.ink)
            Text("Settings", style = HhType.labelSmall, color = c.ink)
        }
    }
}

@Composable
private fun RailItem(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("rail_${tab.id}")
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 64.dp, height = 38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(if (selected) c.accentSoft else Color.Transparent),
        ) {
            HhIcon(tab.icon, size = 26.dp, filled = selected, tint = if (selected) c.ink else c.mute)
        }
        Spacer(Modifier.height(6.dp))
        Text(tab.label, style = HhType.label, color = if (selected) c.ink else c.mute)
    }
}
