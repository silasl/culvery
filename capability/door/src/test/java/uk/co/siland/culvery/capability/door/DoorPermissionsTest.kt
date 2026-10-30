package uk.co.siland.culvery.capability.door

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Role

class DoorPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), DoorPermissionSource()))

    /** 15a §3.2: Adult or Admin with a fresh PIN each time, so a Child's PIN is refused. Fails if the id, label, role or freshPin differs. */
    @Test
    fun clearingIsForAdultsAndAdminsWithAFreshPin() {
        assertThat(DoorPermissions.CLEAR).isEqualTo("door.clear")
        assertThat(Role.entries.filter { registry.isGranted(it, DoorPermissions.CLEAR) }).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(registry.isGranted(Role.CHILD, DoorPermissions.CLEAR)).isFalse()
        val def = registry.require(DoorPermissions.CLEAR)
        assertThat(def.label).isEqualTo("Clear door history")
        assertThat(def.freshPin).isTrue()
    }
}
