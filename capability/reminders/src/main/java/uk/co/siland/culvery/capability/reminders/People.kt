package uk.co.siland.culvery.capability.reminders

import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** Those of [ids] still in [household], in its order; Family, or nobody left, is Family alone. */
internal fun forWho(ids: List<PersonId>, household: List<Person>): List<Person> {
    if (PersonId.FAMILY in ids) return listOf(Person.Family)
    return household.filter { it.id in ids }.ifEmpty { listOf(Person.Family) }
}
