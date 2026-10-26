package uk.co.siland.culvery.capability.hoover

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Role

class HooverPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), HooverPermissionSource()))

    /** 11 §4.4: Adult or Admin with a fresh PIN each time, so a Child's PIN is refused. Fails if the label, roles or freshPin differ. */
    @Test
    fun startIsForAdultsAndAdminsWithAFreshPin() {
        assertThat(HooverPermissions.START).isEqualTo("hoover.start")
        assertThat(Role.entries.filter { registry.isGranted(it, HooverPermissions.START) }).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(registry.isGranted(Role.CHILD, HooverPermissions.START)).isFalse()
        val def = registry.require(HooverPermissions.START)
        assertThat(def.label).isEqualTo("Start the hoover")
        assertThat(def.freshPin).isTrue()
    }
}
