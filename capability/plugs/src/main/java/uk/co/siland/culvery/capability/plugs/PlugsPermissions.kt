package uk.co.siland.culvery.capability.plugs

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object PlugsPermissions {
    const val SWITCH = "plugs.switch"
    const val MANAGE = "plugs.manage"
}

/** Both need an Adult or Admin PIN each time (12 §4.4). */
class PlugsPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(PlugsPermissions.SWITCH, "Switch plugs that need a PIN", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
        PermissionDef(PlugsPermissions.MANAGE, "Choose what modes do with plugs", setOf(Role.ADMIN, Role.ADULT), freshPin = true),
    )
}
