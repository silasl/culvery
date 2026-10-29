package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.readAsOne
import uk.co.siland.culvery.core.ui.shownNotSpoken
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.TabItem

private val HomeTab = TabItem(HOME_TAB_ID, "Home", Icons.HOME)

// 5 §5.3: a count in a `danger` pill or a 10 dp dot at the icon's top end; past 9, "9+".
private const val BADGE_MAX = 9

/** The full-height rail: the mark at its top, the tabs, then Settings. Who is signed in shows in the top bar, not here. */
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
        modifier = Modifier
            .readAsOne()
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            .drawBehind {
                val x = size.width - ShellDimens.railLine.toPx() / 2
                drawLine(c.line, Offset(x, 0f), Offset(x, size.height), ShellDimens.railLine.toPx())
            }
            .padding(top = (RAIL_WIDTH - MARK_WIDTH) / 2, bottom = ShellDimens.railBottom),
    ) {
        // Padded like the rail's top, so both insets round to the same pixel.
        Box(Modifier.align(Alignment.Start).padding(start = (RAIL_WIDTH - MARK_WIDTH) / 2).testTag("culvery_mark").semantics { hideFromAccessibility() }) {
            Culvery.look?.mark?.invoke(MARK_WIDTH) ?: CulveryMark(MARK_WIDTH)
        }
        Spacer(Modifier.height(ShellDimens.markGapBelow))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShellDimens.navItemGap),
            modifier = Modifier.selectableGroup(),
        ) {
            (listOf(HomeTab) + tabs).forEach { tab ->
                RailItem(tab, selected = tab.id == selectedId) { onSelect(tab.id) }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShellDimens.settingsGap, Alignment.CenterVertically),
            modifier = Modifier
                .testTag("rail_settings")
                .size(ShellDimens.settingsTile)
                .clip(RoundedCornerShape(ShellDimens.settingsTileRadius))
                .background(c.surf)
                .hhClickable(onOpenSettings),
        ) {
            HhIcon(Icons.SETTINGS, size = ShellDimens.settingsIcon, tint = c.ink)
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
            .width(ShellDimens.navItemWidth)
            .clip(RoundedCornerShape(16.dp))
            .then(tab.badge?.let { badge -> Modifier.semantics { contentDescription = "${tab.label}, ${badge.spoken}" } } ?: Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = ShellDimens.navPillWidth, height = ShellDimens.navPillHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (selected) c.accentSoft else Color.Transparent),
            ) {
                HhIcon(tab.icon, size = ShellDimens.navIcon, filled = selected, tint = if (selected) c.ink else c.mute)
            }
            tab.badge?.let { RailBadgeMark(it, Modifier.align(Alignment.TopEnd).testTag("rail_${tab.id}_badge")) }
        }
        Spacer(Modifier.height(ShellDimens.navPillToLabel))
        // A badged tab is read by its description, which already starts with the label.
        Text(
            tab.label,
            style = HhType.label.copy(fontSize = ShellDimens.navLabel, fontWeight = if (selected) FontWeight.W600 else FontWeight.W500),
            color = if (selected) c.ink else c.mute,
            modifier = if (tab.badge != null) Modifier.shownNotSpoken() else Modifier,
        )
    }
}

/** The count is a `danger` pill at least [ShellDimens.badgeMin] high inside a ring in the page colour, so it reads over the icon's pill. */
@Composable
private fun RailBadgeMark(badge: RailBadge, modifier: Modifier) {
    val c = Culvery.colors
    when (badge) {
        is RailBadge.Count -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .shownNotSpoken()
                .clip(CircleShape)
                .background(c.bg)
                .padding(ShellDimens.badgeRing)
                .clip(CircleShape)
                .background(c.danger)
                .defaultMinSize(ShellDimens.badgeMin, ShellDimens.badgeMin)
                .padding(horizontal = ShellDimens.badgePaddingH),
        ) {
            Text(
                if (badge.count > BADGE_MAX) "$BADGE_MAX+" else badge.count.toString(),
                style = HhType.labelSmall.copy(fontSize = ShellDimens.badgeText),
                color = c.dangerInk,
            )
        }
        is RailBadge.Dot -> Box(modifier.size(ShellDimens.badgeDot).clip(CircleShape).background(c.danger))
    }
}
