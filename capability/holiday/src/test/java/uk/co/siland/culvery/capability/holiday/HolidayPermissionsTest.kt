package uk.co.siland.culvery.capability.holiday

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role

class HolidayPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), HolidayPermissionSource()))

    /** 11 §5.2: fails if the label, roles or fresh PIN differ; a weaker one would let the grant skip Holiday lights' own PIN. */
    @Test
    fun holidayModeIsForAdultsAndAdminsWithAFreshPin() = runTest {
        assertThat(Role.entries.filter { registry.isGranted(it, HousePermissions.HOLIDAY_MODE) }).containsExactly(Role.ADMIN, Role.ADULT)
        val def = registry.require(HousePermissions.HOLIDAY_MODE)
        assertThat(def.label).isEqualTo("Turn Holiday mode on and off")
        assertThat(def.freshPin).isTrue()
        assertThat(TestAccess(Role.CHILD).authorise(HousePermissions.HOLIDAY_MODE)).isNull()
    }
}
