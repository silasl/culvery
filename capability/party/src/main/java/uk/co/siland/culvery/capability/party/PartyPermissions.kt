package uk.co.siland.culvery.capability.party

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role

/** An Adult or Admin PIN each time, as Holiday mode's (13 §2.7). */
class PartyPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(HousePermissions.PARTY_MODE, "Turn Party mode on and off", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
    )
}
