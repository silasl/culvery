package uk.co.siland.culvery.capability.lights

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object LightsPermissions {
    const val HOLIDAY = "lights.holiday"
}

/** The lights are free for everyone; only Holiday lights, both ways, needs an Adult or Admin PIN each time (7 §2.8). */
class LightsPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(LightsPermissions.HOLIDAY, "Turn Holiday lights on and off", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
    )
}
