package uk.co.siland.culvery.provider.calendar_google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [items] has no default: an answer without it is a bad read, never "the account has no calendars" (review H2). */
@Serializable
internal data class CalendarListPage(val items: List<CalendarListEntry>, val nextPageToken: String? = null)

@Serializable
internal data class CalendarListEntry(
    val id: String,
    val summary: String? = null,
    val summaryOverride: String? = null,
    val accessRole: String? = null,
    val selected: Boolean? = null,
    val hidden: Boolean? = null,
    val primary: Boolean? = null,
)

/** An empty page may leave out [items]; one with neither token is refused by the provider as a bad read (review H2). */
@Serializable
internal data class EventsPage(
    val items: List<GoogleEvent> = emptyList(),
    val nextPageToken: String? = null,
    val nextSyncToken: String? = null,
)

@Serializable
internal data class GoogleEvent(
    val id: String,
    val status: String? = null,
    val summary: String? = null,
    val start: GoogleTime? = null,
    val end: GoogleTime? = null,
    val recurringEventId: String? = null,
    val recurrence: List<String>? = null,
    val eventType: String? = null,
    val extendedProperties: ExtendedProperties? = null,
    val colorId: String? = null,
    val attendees: List<GoogleAttendee>? = null,
)

/** [self]: this attendee is the account itself, whose answer ([responseStatus]) decides a declined invitation. */
@Serializable
internal data class GoogleAttendee(val self: Boolean? = null, val responseStatus: String? = null)

@Serializable
internal data class GoogleTime(val dateTime: String? = null, val date: String? = null, val timeZone: String? = null)

@Serializable
internal data class ExtendedProperties(@SerialName("private") val privateProperties: Map<String, String>? = null)

@Serializable
internal data class CalendarResource(val id: String)
