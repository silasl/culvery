package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.door.DoorAlerts

/** The card on the alert layer, as [alerts] has it. */
@Composable
fun DoorCardHost(alerts: DoorAlerts) {
    val showing by alerts.showing.collectAsStateWithLifecycle()
    showing?.let { ring -> DoorCard(ring.card, onGotIt = { alerts.gotIt(ring.eventId) }) }
}
