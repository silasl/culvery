package uk.co.siland.culvery.capability.plugs

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Role

class PlugsPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), PlugsPermissionSource()))

    /** 12 §4.4: Adult or Admin with a fresh PIN each time, so a Child's PIN is refused. Fails if the id, label, roles or freshPin differ, or another permission is defined. */
    @Test
    fun switchIsForAdultsAndAdminsWithAFreshPin() {
        assertThat(PlugsPermissions.SWITCH).isEqualTo("plugs.switch")
        assertThat(PlugsPermissionSource().permissions.map { it.id }).containsExactly(PlugsPermissions.SWITCH)
        assertThat(Role.entries.filter { registry.isGranted(it, PlugsPermissions.SWITCH) }).containsExactly(Role.ADMIN, Role.ADULT)
        val def = registry.require(PlugsPermissions.SWITCH)
        assertThat(def.label).isEqualTo("Switch plugs that need a PIN")
        assertThat(def.freshPin).isTrue()
    }
}
