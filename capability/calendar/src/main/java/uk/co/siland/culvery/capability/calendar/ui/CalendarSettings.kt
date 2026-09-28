package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.syncedLabel
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** 3a design §4.1. */
internal const val NEEDS_RECONNECTING = "Needs reconnecting"
internal const val SOMETHING_WENT_WRONG = "Something went wrong"

/** A row's health in words: "Synced 5 min ago", "Can't reach Google Calendar", "Needs reconnecting" or "Something went wrong". */
internal fun healthWords(row: CalendarRow, nowMillis: Long): String = when (row.health) {
    ConnectionHealth.Ok -> syncedLabel(row.lastSyncMillis, nowMillis).replaceFirstChar { it.uppercase() }
    ConnectionHealth.Unreachable -> "Can't reach ${row.service}"
    ConnectionHealth.NeedsSignIn -> NEEDS_RECONNECTING
    is ConnectionHealth.Error -> SOMETHING_WENT_WRONG
}

@Composable
internal fun CalendarSettingsHost(connections: CalendarConnections, clock: WallClock) {
    val rows by connections.rows.collectAsState(initial = emptyList())
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    CalendarSettings(
        rows = rows,
        connectable = connectable.map { it.descriptor },
        nowMillis = rememberNowMillis(clock),
        onReconnect = connector::reconnect,
        onConnect = { connector.connect(it.id) },
    )
}

/**
 * Settings' Calendars block (3a design §4.1): a row per connection with its health, Reconnect when it needs signing in,
 * and Connect for each provider that can be connected. No disconnect, mapping or master controls (Plan 4).
 */
@Composable
internal fun CalendarSettings(
    rows: List<CalendarRow>,
    connectable: List<ProviderDescriptor>,
    nowMillis: Long,
    onReconnect: (Connection) -> Unit,
    onConnect: (ProviderDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    Column(modifier.testTag("settings_calendars")) {
        Text("Calendars", style = CalendarType.blockTitle, color = c.ink)
        Spacer(Modifier.height(CalendarDimens.settingsTitleGap))
        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsRowGap)) {
            rows.forEach { row -> ConnectionRow(row, nowMillis, onReconnect) }
            connectable.forEach { d -> AddButton("Connect ${d.displayName}", "settings_connect_${d.id}") { onConnect(d) } }
        }
    }
}

@Composable
private fun ConnectionRow(row: CalendarRow, nowMillis: Long, onReconnect: (Connection) -> Unit) {
    val c = Culvery.colors
    val needsReconnect = row.health == ConnectionHealth.NeedsSignIn
    val account = row.connection.config[CONFIG_ACCOUNT]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.settingsIconGap),
        modifier = Modifier
            .testTag("settings_row_${row.connection.id}")
            .width(CalendarDimens.settingsRowWidth)
            .clip(RoundedCornerShape(CalendarDimens.settingsRowRadius))
            .background(c.surf)
            .padding(horizontal = CalendarDimens.settingsRowPaddingH, vertical = CalendarDimens.settingsRowPaddingV),
    ) {
        HhIcon(row.icon, size = CalendarDimens.settingsIcon, tint = c.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CalendarDimens.settingsStatusTop)) {
            Text(
                if (account == null) row.service else "${row.service} · $account",
                style = CalendarType.settingsRowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(healthWords(row, nowMillis), style = CalendarType.subtitle, color = if (needsReconnect) c.danger else c.mute, maxLines = 1)
        }
        // 3a design §4.1: 48 dp, `accent`: the Add event pill without its icon.
        if (needsReconnect) AddButton("Reconnect", "settings_reconnect", icon = null) { onReconnect(row.connection) }
    }
}
