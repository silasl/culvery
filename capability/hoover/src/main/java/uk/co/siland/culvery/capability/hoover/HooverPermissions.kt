package uk.co.siland.culvery.capability.hoover

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object HooverPermissions {
    const val START = "hoover.start"
}

/** Starting a clean needs an Adult or Admin PIN each time (11 §4.4). */
class HooverPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(HooverPermissions.START, "Start the hoover", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
    )
}
