package uk.co.siland.culvery.capability.reminders

import javax.inject.Inject
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

object RemindersPermissions {
    const val ADD = "reminders.add"
    const val ADD_SELF = "reminders.add.self"
    const val CHANGE = "reminders.change"
    const val CHANGE_OWN = "reminders.change.own"
    const val BINS = "reminders.bins"
}

/** Registered as the calendar's are (22 §4.6). */
class RemindersPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(RemindersPermissions.ADD, "Add reminders for anyone", setOf(Role.ADMIN, Role.ADULT)),
        PermissionDef(RemindersPermissions.ADD_SELF, "Add your own reminders", Role.entries.toSet()),
        PermissionDef(RemindersPermissions.CHANGE, "Change any reminder", setOf(Role.ADMIN, Role.ADULT)),
        PermissionDef(RemindersPermissions.CHANGE_OWN, "Change reminders you made", Role.entries.toSet()),
        PermissionDef(RemindersPermissions.BINS, "Change the bins reminder", setOf(Role.ADMIN, Role.ADULT)),
    )
}

/** Changing or deleting needs change, or change.own on one [who] made; one whose maker has left the household is the adults'. */
internal fun mayChange(granted: Set<String>, who: Identified, madeBy: PersonId): Boolean =
    RemindersPermissions.CHANGE in granted || (RemindersPermissions.CHANGE_OWN in granted && madeBy == who.person.id)

/** Adding needs add, or add.self for a reminder that is for exactly [who]. */
internal fun mayAddFor(granted: Set<String>, who: Identified, forPeople: List<PersonId>): Boolean =
    RemindersPermissions.ADD in granted || (RemindersPermissions.ADD_SELF in granted && forPeople == listOf(who.person.id))

fun cannotChangeOthers(name: String) = "$name can only change reminders they made."

fun cannotAddForOthers(name: String) = "$name can add reminders for $name only."
