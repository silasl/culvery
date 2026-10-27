package uk.co.siland.culvery.capability.holiday

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role

/** An Adult or Admin PIN each time, as Holiday lights', which this grant also turns on (11 §5.2). */
class HolidayPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(HousePermissions.HOLIDAY_MODE, "Turn Holiday mode on and off", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
    )
}
