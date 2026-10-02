package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** The wizard's Connect step once a calendar is connected. */
internal const val CONNECTED = "Connected"

/**
 * Takes the Today slot until a calendar is connected (spec §9.2), laid out like the hand-off's Holiday tile. With a
 * provider the household can connect, its button connects it (3a design §4.2); otherwise it opens Settings.
 */
@Composable
fun ConnectCalendarCard(connectService: String?, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_connect"),
        radius = CalendarDimens.cardRadius,
        padding = PaddingValues(horizontal = CalendarDimens.connectPaddingH, vertical = CalendarDimens.connectPaddingV),
    ) {
        HhIcon(Icons.CALENDAR_ADD_ON, size = CalendarDimens.connectIconSize, tint = c.accent)
        Spacer(Modifier.weight(1f))
        Text("Connect a calendar", style = HhType.cardTitle, color = c.ink)
        Spacer(Modifier.height(CalendarDimens.connectSubtitleTop))
        Text("Connect your family's calendar to see it here.", style = HhType.secondary, color = c.mute)
        Spacer(Modifier.height(CalendarDimens.connectButtonTop))
        if (connectService != null) {
            HhPillButton("Connect $connectService", onClick = onConnect, primary = true)
        } else {
            HhPillButton("Open settings", onClick = navigator::openSettings, primary = true)
        }
    }
}

/** The Connect card once calendars are connected: each "{Service} · {account}" with Connected, and no button. */
@Composable
internal fun ConnectedCalendarCard(rows: List<CalendarRow>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_connected"),
        radius = CalendarDimens.cardRadius,
        padding = PaddingValues(horizontal = CalendarDimens.connectPaddingH, vertical = CalendarDimens.connectPaddingV),
    ) {
        HhIcon(Icons.EVENT_AVAILABLE, size = CalendarDimens.connectIconSize, tint = c.accent)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.connectButtonTop)) {
            rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.connectSubtitleTop)) {
                    Text(serviceAndAccount(row), style = HhType.cardTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(CONNECTED, style = HhType.secondary, color = c.mute)
                }
            }
        }
    }
}

/** The Connect card with the first provider the household can connect. */
@Composable
internal fun ConnectCardHost(connections: CalendarConnections) {
    val connectable by connections.connectable.collectAsState(initial = emptyList())
    val connector = rememberConnector(connections)
    val first = connectable.firstOrNull()
    ConnectCalendarCard(first?.descriptor?.displayName, onConnect = { first?.let { connector.connect(it.descriptor.id) } })
}
