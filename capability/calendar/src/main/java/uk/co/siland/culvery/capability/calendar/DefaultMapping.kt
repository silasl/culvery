package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/**
 * How a newly found source is set up (3a design D4): the primary is Family, since the tablet's events go there; any
 * other calendar whose name names exactly one household person is theirs, otherwise Family; visible as the tablet
 * shows it ([visibleOnTablet]). Editing mappings is Plan 4's.
 */
fun defaultMapping(source: CalendarSource, people: List<Person>): SourceMapping {
    val person = if (source.primary) {
        PersonId.FAMILY
    } else {
        people.filter { !it.isFamily && namesPerson(source.name, it.name) }.singleOrNull()?.id ?: PersonId.FAMILY
    }
    return SourceMapping(person, visible = source.visibleOnTablet)
}

/** [name] as a whole word in [summary], ignoring case, a trailing "'s" allowed: "Mia's swimming" names Mia; "Samantha" doesn't name Sam. */
internal fun namesPerson(summary: String, name: String): Boolean =
    Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?:['’]s)?(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(summary)
