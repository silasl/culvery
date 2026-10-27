package uk.co.siland.culvery.capability.plugs

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Role

class PlugsPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), PlugsPermissionSource()))

    /** 12 §4.4: Adult or Admin with a fresh PIN each time, so a Child's PIN is refused. Fails if an id, label, role or freshPin differs. */
    @Test
    fun bothAreForAdultsAndAdminsWithAFreshPin() {
        assertThat(PlugsPermissions.SWITCH).isEqualTo("plugs.switch")
        assertThat(PlugsPermissions.MANAGE).isEqualTo("plugs.manage")
        val labels = mapOf(
            PlugsPermissions.SWITCH to "Switch plugs that need a PIN",
            PlugsPermissions.MANAGE to "Add, remove and set up plugs",
        )
        for ((id, label) in labels) {
            assertThat(Role.entries.filter { registry.isGranted(it, id) }).containsExactly(Role.ADMIN, Role.ADULT)
            assertThat(registry.isGranted(Role.CHILD, id)).isFalse()
            val def = registry.require(id)
            assertThat(def.label).isEqualTo(label)
            assertThat(def.freshPin).isTrue()
        }
    }
}
