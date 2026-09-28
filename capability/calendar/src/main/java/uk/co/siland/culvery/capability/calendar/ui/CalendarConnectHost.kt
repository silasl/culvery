package uk.co.siland.culvery.capability.calendar.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import uk.co.siland.culvery.capability.calendar.CalendarConnections
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** 3a design §4.4. */
internal fun connectingTitle(service: String): String = "Connecting to $service…"

internal const val CONNECTING_LINE = "Choose the family's Google account and allow access."

/** What the connecting card is for: a new connection to [provider], or reconnecting [existing]. */
internal data class ConnectRequest(val provider: CalendarProvider, val existing: Connection?)

internal fun OverlayHost.showConnect(request: ConnectRequest, connections: CalendarConnections): Unit = show {
    CalendarConnectHost(request, connections, onClose = { dismiss() })
}

/** Opens the connecting card for a new connection or a reconnect (3a design §3.3). */
internal class Connector(private val overlay: OverlayHost, private val connections: CalendarConnections) {
    fun connect(providerId: String) {
        val provider = installed(providerId) ?: return
        overlay.showConnect(ConnectRequest(provider, existing = null), connections)
    }

    fun reconnect(connection: Connection) {
        val provider = installed(connection.providerId) ?: return
        overlay.showConnect(ConnectRequest(provider, existing = connection), connections)
    }

    private fun installed(providerId: String): CalendarProvider? =
        connections.provider(providerId).also { if (it == null) Log.w(TAG, "No calendar provider $providerId in this build; nothing to connect") }

    private companion object {
        const val TAG = "CalendarConnect"
    }
}

@Composable
internal fun rememberConnector(connections: CalendarConnections): Connector {
    val overlay = LocalOverlayHost.current
    return remember(overlay, connections) { Connector(overlay, connections) }
}

/**
 * The connecting card (3a design §3.3): an Admin check (settings.manage), then the card with the provider's connect
 * screen inside it, which runs the system's account chooser and consent over it. A connection is finished on the
 * application scope; Cancel, or backing out of the system screens, closes with nothing said.
 */
@Composable
internal fun CalendarConnectHost(request: ConnectRequest, connections: CalendarConnections, onClose: () -> Unit) {
    val allowed: Boolean? by produceState<Boolean?>(null, request) { value = connections.mayConnect() }
    when (allowed) {
        // The PIN pad is up.
        null -> Unit
        false -> LaunchedEffect(request) { onClose() }
        true -> ConnectingCard(request.provider.descriptor.displayName, onCancel = onClose) {
            request.provider.ConnectScreen(
                existing = request.existing,
                onConnected = { connection ->
                    connections.finish(connection, reconnecting = request.existing != null)
                    onClose()
                },
                onCancel = onClose,
            )
        }
    }
}

/** 3a design §4.4: centred over the scrim; [content] is the provider's connect screen. */
@Composable
internal fun ConnectingCard(service: String, onCancel: () -> Unit, content: @Composable () -> Unit = {}) {
    val c = Culvery.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PickerCard(CalendarDimens.connectingWidth, CalendarDimens.connectingGap, "connecting_card") {
            HhIcon("calendar_month", size = CalendarDimens.connectingIcon, tint = c.accent)
            Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.connectingTextGap)) {
                Text(connectingTitle(service), style = CalendarType.blockTitle, color = c.ink)
                Text(CONNECTING_LINE, style = CalendarType.subtitle, color = c.mute)
            }
            content()
            PickerButton(
                "Cancel", primary = false, tag = "connecting_cancel",
                height = CalendarDimens.pickerCancelHeight, radius = CalendarDimens.pickerCancelRadius,
                modifier = Modifier.fillMaxWidth(), onClick = onCancel,
            )
        }
    }
}
