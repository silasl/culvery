package uk.co.siland.culvery.provider.calendar_google

import java.time.LocalDate
import java.time.OffsetDateTime
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent

/** The tablet's tags in extendedProperties.private (parent spec §6). */
internal const val PERSON_KEY = "culvery.person"
internal const val CREATED_BY_KEY = "culvery.createdBy"

/** As Google shows an event with no title. */
internal const val NO_TITLE = "(No title)"

private const val CANCELLED = "cancelled"
private const val WORKING_LOCATION = "workingLocation"
private const val DECLINED = "declined"

/**
 * Deleted; a working location, which isn't an event people plan around; or an invitation the account itself declined
 * (3a design §3.5).
 */
internal val GoogleEvent.isGone: Boolean
    get() = status == CANCELLED || eventType == WORKING_LOCATION ||
        attendees.orEmpty().any { it.self == true && it.responseStatus == DECLINED }

/** A start or end: a dateTime with Google's offset, or an all-day date (Google's end date is already exclusive). */
internal fun GoogleTime.toEventTime(): EventTime? = runCatching {
    when {
        dateTime != null -> EventTime.Timed(OffsetDateTime.parse(dateTime).toInstant())
        date != null -> EventTime.AllDay(LocalDate.parse(date))
        else -> null
    }
}.getOrNull()

/** The event as the contract has it, with its series' [rule]; null when it has no start or end the tablet can read. */
internal fun GoogleEvent.toRemoteEvent(rule: String?): RemoteEvent? {
    val from = start?.toEventTime() ?: return null
    val to = end?.toEventTime() ?: return null
    val tags = extendedProperties?.privateProperties.orEmpty()
    return RemoteEvent(
        remoteId = id,
        title = summary?.takeIf { it.isNotBlank() } ?: NO_TITLE,
        start = from,
        end = to,
        recurring = recurringEventId != null,
        forPerson = tags[PERSON_KEY],
        createdBy = tags[CREATED_BY_KEY],
        recurrenceRule = rule,
    )
}
