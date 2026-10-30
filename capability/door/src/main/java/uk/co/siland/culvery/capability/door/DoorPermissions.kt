package uk.co.siland.culvery.capability.door

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object DoorPermissions {
    const val CLEAR = "door.clear"
}

/** Deleting door history, by clearing it or keeping less of it, needs an Adult or Admin PIN each time (15a §3.2). */
class DoorPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(DoorPermissions.CLEAR, "Clear door history", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
    )
}
