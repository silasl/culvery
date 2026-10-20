package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.door.DOOR_ID
import uk.co.siland.culvery.capability.door.DoorAlerts
import uk.co.siland.culvery.core.plugin.LocalAlertHost
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator

/** The card on the alert layer, as [alerts] has it. Door history leaves the ring unseen: opening the Door tab marks it seen. */
@Composable
fun DoorCardHost(alerts: DoorAlerts) {
    val showing by alerts.showing.collectAsStateWithLifecycle()
    val lowered by LocalAlertHost.current.lowered.collectAsStateWithLifecycle()
    val overlay = LocalOverlayHost.current
    val navigator = LocalShellNavigator.current
    showing?.let { ring ->
        DoorCard(
            ring.card,
            onGotIt = { alerts.gotIt(ring.eventId) },
            lowered = lowered,
            onHistory = {
                overlay.dismiss()
                alerts.close(ring.eventId)
                navigator.openTab(DOOR_ID)
            },
        )
    }
}
