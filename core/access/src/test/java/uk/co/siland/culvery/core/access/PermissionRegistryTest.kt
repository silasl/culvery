package uk.co.siland.culvery.core.access

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.core.household.Role

class PermissionRegistryTest {
    private val calendar = object : PermissionSource {
        override val permissions = listOf(
            PermissionDef("calendar.event.create", "Add events", setOf(Role.ADMIN, Role.ADULT)),
            PermissionDef("calendar.event.create.self", "Add your own events", Role.entries.toSet()),
        )
    }
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), calendar))

    @Test
    fun corePermissionsAreAdminOnly() {
        for (id in listOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.PEOPLE_MANAGE, CorePermissions.KIOSK_EXIT)) {
            assertThat(registry.isGranted(Role.ADMIN, id)).isTrue()
            assertThat(registry.isGranted(Role.ADULT, id)).isFalse()
            assertThat(registry.isGranted(Role.CHILD, id)).isFalse()
        }
    }

    @Test
    fun kioskExitAndPeopleNeedAFreshPin() {
        assertThat(registry.require(CorePermissions.KIOSK_EXIT).freshPin).isTrue()
        assertThat(registry.require(CorePermissions.PEOPLE_MANAGE).freshPin).isTrue()
        assertThat(registry.require(CorePermissions.SETTINGS_MANAGE).freshPin).isFalse()
    }

    @Test
    fun capabilityPermissionsUseTheirDefaultRoles() {
        assertThat(registry.isGranted(Role.CHILD, "calendar.event.create")).isFalse()
        assertThat(registry.isGranted(Role.CHILD, "calendar.event.create.self")).isTrue()
    }

    @Test
    fun unknownPermissionFailsLoudly() {
        assertThrows(IllegalArgumentException::class.java) { registry.isGranted(Role.ADMIN, "nope") }
    }

    @Test
    fun duplicatePermissionIdsAreRejected() {
        val dup = object : PermissionSource {
            override val permissions = listOf(PermissionDef(CorePermissions.KIOSK_EXIT, "x", emptySet()))
        }
        assertThrows(IllegalArgumentException::class.java) { PermissionRegistry(setOf(CorePermissionSource(), dup)) }
    }

    @Test
    fun allListsEveryPermission() {
        assertThat(registry.all()).hasSize(6)
    }
}
