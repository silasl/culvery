package uk.co.siland.culvery.core.access

import javax.inject.Inject
import uk.co.siland.culvery.core.household.Role

data class PermissionDef(
    val id: String,
    val label: String,
    val defaultRoles: Set<Role>,
    val freshPin: Boolean = false,
)

/** Each capability contributes one of these via `@IntoSet`. */
interface PermissionSource {
    val permissions: List<PermissionDef>
}

object CorePermissions {
    const val SETTINGS_MANAGE = "settings.manage"
    const val PEOPLE_MANAGE = "people.manage"
    const val KIOSK_EXIT = "kiosk.exit"
    const val CONNECTIONS_MANAGE = "connections.manage"
}

class CorePermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(CorePermissions.SETTINGS_MANAGE, "Change settings", setOf(Role.ADMIN)),
        PermissionDef(CorePermissions.PEOPLE_MANAGE, "Add and change people", setOf(Role.ADMIN), freshPin = true),
        PermissionDef(CorePermissions.KIOSK_EXIT, "Use other apps", setOf(Role.ADMIN), freshPin = true),
        // 4c design D5: Google's account chooser can lead out of the app; so can Android's notification access (5 §3.4).
        PermissionDef(CorePermissions.CONNECTIONS_MANAGE, "Connect calendars and services", setOf(Role.ADMIN), freshPin = true),
    )
}
