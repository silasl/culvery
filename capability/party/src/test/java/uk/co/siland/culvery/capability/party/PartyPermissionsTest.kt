package uk.co.siland.culvery.capability.party

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role

class PartyPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), PartyPermissionSource()))

    /** 13 §2.7: fails if the label, roles or fresh PIN differ, or a child can start a party. */
    @Test
    fun partyModeIsForAdultsAndAdminsWithAFreshPin() = runTest {
        assertThat(Role.entries.filter { registry.isGranted(it, HousePermissions.PARTY_MODE) }).containsExactly(Role.ADMIN, Role.ADULT)
        val def = registry.require(HousePermissions.PARTY_MODE)
        assertThat(def.label).isEqualTo("Turn Party mode on and off")
        assertThat(def.freshPin).isTrue()
        assertThat(HousePermissions.PARTY_MODE).isEqualTo("party.mode")
        assertThat(TestAccess(Role.CHILD).authorise(HousePermissions.PARTY_MODE)).isNull()
    }
}
