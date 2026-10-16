package uk.co.siland.culvery.capability.lights

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Role

class LightsPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), LightsPermissionSource()))

    private fun rolesFor(permission: String) = Role.entries.filter { registry.isGranted(it, permission) }

    /** 7 §2.8, §3.1: Adult or Admin, a fresh PIN every time. Fails if freshPin is false. */
    @Test
    fun holidayModeIsForAdultsAndAdminsWithAFreshPin() {
        assertThat(rolesFor(LightsPermissions.HOLIDAY)).containsExactly(Role.ADMIN, Role.ADULT)
        val def = registry.require(LightsPermissions.HOLIDAY)
        assertThat(def.label).isEqualTo("Turn Holiday mode on and off")
        assertThat(def.freshPin).isTrue()
    }
}
