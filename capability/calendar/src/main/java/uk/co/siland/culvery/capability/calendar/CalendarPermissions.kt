package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object CalendarPermissions {
    const val CREATE = "calendar.event.create"
    const val CREATE_SELF = "calendar.event.create.self"
    const val EDIT = "calendar.event.edit"
    const val EDIT_OWN = "calendar.event.edit.own"
    const val ASSIGN = "calendar.event.assign"
}

/** Spec §8 as amended by the 2b-1 design §3.6. */
class CalendarPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(CalendarPermissions.CREATE, "Add events for anyone", setOf(Role.ADMIN, Role.ADULT)),
        PermissionDef(CalendarPermissions.CREATE_SELF, "Add your own events", Role.entries.toSet()),
        PermissionDef(CalendarPermissions.EDIT, "Change any event", setOf(Role.ADMIN, Role.ADULT)),
        PermissionDef(CalendarPermissions.EDIT_OWN, "Change your own events", Role.entries.toSet()),
        PermissionDef(CalendarPermissions.ASSIGN, "Assign events", setOf(Role.ADMIN, Role.ADULT)),
    )
}

/** Changing (deleting, and in 2b-2 editing) needs edit, or edit.own on an event this person created. */
internal fun mayChange(granted: Set<String>, who: Identified, createdBy: String?): Boolean =
    CalendarPermissions.EDIT in granted ||
        (CalendarPermissions.EDIT_OWN in granted && createdBy == who.person.id.value)

/** Hand-off §7 refusal wording. */
fun cannotChangeOthers(name: String): String = "$name can only change events they created."

const val ASK_AN_ADULT = "Ask an adult to assign this event."
