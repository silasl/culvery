package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** The people a `culvery.person` tag names, in the order written; Family only when it stands alone (9 §3.3). */
fun peopleIn(forPerson: String?): List<PersonId> {
    val ids = forPerson.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().map(::PersonId)
    return if (ids.size > 1) ids - PersonId.FAMILY else ids
}

/** The tag for [people], in the household's [order]; ids not in it go last, as given. */
fun tagFor(people: List<PersonId>, order: List<PersonId>): String {
    require(people.isNotEmpty()) { "A tag names at least one person" }
    require(PersonId.FAMILY !in people || people.size == 1) { "Family is never mixed with people" }
    return people.distinct().sortedBy(order::rankOf).joinToString(",") { it.value }
}

/**
 * Who an event is for, in household [order] and never empty: the tag's people still in [people] (9 §3.4). A shared
 * tag with nobody left is Family; no tag, or one unknown id, reads as before, as the calendar's person (9 §3.3).
 */
fun resolvePeople(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>, order: List<PersonId>): List<Person> {
    val ids = peopleIn(forPerson)
    val known = ids.mapNotNull { people[it] ?: if (it == PersonId.FAMILY) Person.Family else null }
    return when {
        known.isNotEmpty() -> known.sortedBy { order.rankOf(it.id) }
        ids.size > 1 -> listOf(Person.Family)
        else -> listOf(people[sourcePerson] ?: Person.Family)
    }
}

/** No tag at all, or one that names nobody. */
fun isUntagged(forPerson: String?): Boolean = peopleIn(forPerson).isEmpty()

private fun List<PersonId>.rankOf(id: PersonId): Int = indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE

/** "Sam", "Sam + Mia", "Alex, Sam +1". */
fun whoLine(people: List<Person>): String = when (people.size) {
    0, 1 -> people.firstOrNull()?.name.orEmpty()
    2 -> "${people[0].name} + ${people[1].name}"
    else -> "${people[0].name}, ${people[1].name} +${people.size - 2}"
}

/** "Sam and Mia", "Alex, Sam and Mia": how TalkBack names who an event is for. */
fun spokenWho(people: List<Person>): String = spokenNames(people.map { it.name })

/** "Sam", "Sam and Mia", "Alex, Sam and Mia". */
fun spokenNames(names: List<String>): String =
    if (names.size < 2) names.firstOrNull().orEmpty() else names.dropLast(1).joinToString(", ") + " and " + names.last()
