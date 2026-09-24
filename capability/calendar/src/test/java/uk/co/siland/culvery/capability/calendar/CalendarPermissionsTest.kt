package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

class CalendarPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), CalendarPermissionSource()))

    private fun rolesFor(permission: String) = Role.entries.filter { registry.isGranted(it, permission) }

    @Test
    fun theSpecTableOfRolesAndPermissions() {
        assertThat(rolesFor(CalendarPermissions.CREATE)).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(rolesFor(CalendarPermissions.CREATE_SELF)).containsExactly(Role.ADMIN, Role.ADULT, Role.CHILD)
        assertThat(rolesFor(CalendarPermissions.EDIT)).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(rolesFor(CalendarPermissions.EDIT_OWN)).containsExactly(Role.ADMIN, Role.ADULT, Role.CHILD)
        assertThat(rolesFor(CalendarPermissions.ASSIGN)).containsExactly(Role.ADMIN, Role.ADULT)
    }

    @Test
    fun noCalendarPermissionNeedsAFreshPin() {
        CalendarPermissionSource().permissions.forEach { assertThat(it.freshPin).isFalse() }
    }

    private val mia = Identified(Person(PersonId("mia"), "Mia", 0xFFE07BA8), Role.CHILD)

    @Test
    fun editOwnCoversOnlyEventsThePersonCreated() {
        assertThat(mayChange(setOf(CalendarPermissions.EDIT_OWN), mia, createdBy = "mia")).isTrue()
        assertThat(mayChange(setOf(CalendarPermissions.EDIT_OWN), mia, createdBy = "alex")).isFalse()
        assertThat(mayChange(setOf(CalendarPermissions.EDIT_OWN), mia, createdBy = null)).isFalse()
    }

    @Test
    fun editCoversEveryEvent() {
        assertThat(mayChange(setOf(CalendarPermissions.EDIT, CalendarPermissions.EDIT_OWN), mia, createdBy = null)).isTrue()
    }

    @Test
    fun refusalWordingMatchesTheHandOff() {
        assertThat(cannotChangeOthers("Mia")).isEqualTo("Mia can only change events they created.")
        assertThat(ASK_AN_ADULT).isEqualTo("Ask an adult to assign this event.")
    }
}
