package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

class RemindersPermissionsTest {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), RemindersPermissionSource()))
    private val alex = Identified(Person(PersonId("alex"), "Alex", 0xFF4CB387), Role.ADMIN)
    private val mia = Identified(Person(PersonId("mia"), "Mia", 0xFFE07BA8), Role.CHILD)

    private fun rolesFor(permission: String) = Role.entries.filter { registry.isGranted(it, permission) }

    @Test
    fun theFivePermissionsTheirLabelsAndRoles() {
        val everyone = Role.entries.toSet()
        val grownUps = setOf(Role.ADMIN, Role.ADULT)
        assertThat(RemindersPermissionSource().permissions.map { Triple(it.id, it.label, it.defaultRoles) }).containsExactly(
            Triple("reminders.add", "Add reminders for anyone", grownUps),
            Triple("reminders.add.self", "Add your own reminders", everyone),
            Triple("reminders.change", "Change any reminder", grownUps),
            Triple("reminders.change.own", "Change reminders you made", everyone),
            Triple("reminders.bins", "Change the bins reminder", grownUps),
        ).inOrder()
        assertThat(rolesFor(RemindersPermissions.ADD)).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(rolesFor(RemindersPermissions.ADD_SELF)).containsExactlyElementsIn(everyone)
        assertThat(rolesFor(RemindersPermissions.CHANGE)).containsExactly(Role.ADMIN, Role.ADULT)
        assertThat(rolesFor(RemindersPermissions.CHANGE_OWN)).containsExactlyElementsIn(everyone)
        assertThat(rolesFor(RemindersPermissions.BINS)).containsExactly(Role.ADMIN, Role.ADULT)
    }

    @Test
    fun noReminderPermissionNeedsAFreshPin() {
        RemindersPermissionSource().permissions.forEach { assertThat(it.freshPin).isFalse() }
    }

    @Test
    fun anAdultChangesAnyAndAChildOnlyTheirOwn() {
        val own = setOf(RemindersPermissions.CHANGE_OWN)
        assertThat(mayChange(own, mia, madeBy = mia.person.id)).isTrue()
        assertThat(mayChange(own, mia, madeBy = alex.person.id)).isFalse()
        assertThat(mayChange(setOf(RemindersPermissions.CHANGE, RemindersPermissions.CHANGE_OWN), alex, madeBy = mia.person.id)).isTrue()
        assertThat(mayChange(emptySet(), alex, madeBy = alex.person.id)).isFalse()
    }

    @Test
    fun aRemovedMakersReminderIsForAdultsOnly() {
        val gone = PersonId("removed")
        assertThat(mayChange(setOf(RemindersPermissions.CHANGE_OWN), mia, madeBy = gone)).isFalse()
        assertThat(mayChange(setOf(RemindersPermissions.CHANGE, RemindersPermissions.CHANGE_OWN), alex, madeBy = gone)).isTrue()
    }

    @Test
    fun aChildAddsOnlyForThemselves() {
        val self = setOf(RemindersPermissions.ADD_SELF)
        assertThat(mayAddFor(self, mia, listOf(mia.person.id))).isTrue()
        assertThat(mayAddFor(self, mia, listOf(alex.person.id))).isFalse()
        assertThat(mayAddFor(self, mia, listOf(mia.person.id, alex.person.id))).isFalse()
        assertThat(mayAddFor(self, mia, listOf(PersonId.FAMILY))).isFalse()
        assertThat(mayAddFor(self, mia, emptyList())).isFalse()
        assertThat(mayAddFor(setOf(RemindersPermissions.ADD, RemindersPermissions.ADD_SELF), alex, listOf(PersonId.FAMILY))).isTrue()
    }

    @Test
    fun refusalWordingFollowsTheCalendarsPattern() {
        assertThat(cannotChangeOthers("Mia")).isEqualTo("Mia can only change reminders they made.")
        assertThat(cannotAddForOthers("Mia")).isEqualTo("Mia can add reminders for Mia only.")
        assertThat(PICK_A_LATER_TIME).isEqualTo("Pick a time that hasn’t passed.")
        assertThat(LIST_FULL).isEqualTo("The list is full. Delete a reminder to add another.")
    }
}
