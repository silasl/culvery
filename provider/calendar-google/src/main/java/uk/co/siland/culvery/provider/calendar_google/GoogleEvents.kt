package uk.co.siland.culvery.provider.calendar_google

import android.util.Log
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
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
    val from = start?.toEventTime()
    val to = end?.toEventTime()
    if (from == null || to == null) {
        // Fixed words only: the event's id and title are the household's.
        Log.w(TAG, "events: an event's start or end couldn't be read, so the tablet leaves it out")
        return null
    }
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

/**
 * events.insert (3a design §3.6): the client key as the id, the tags (a null one left out), and the colour. Timed
 * times go as UTC with no timeZone, so Google shows them in the calendar's own zone; all-day as dates.
 */
internal fun insertBody(draft: EventDraft, clientKey: String): JsonObject = buildJsonObject {
    put("id", clientKey)
    put("summary", draft.title)
    put("start", timeJson(draft.start, forPatch = false))
    put("end", timeJson(draft.end, forPatch = false))
    putJsonObject("extendedProperties") {
        putJsonObject("private") {
            draft.forPerson?.let { put(PERSON_KEY, it) }
            draft.createdBy?.let { put(CREATED_BY_KEY, it) }
        }
    }
    draft.forPersonColor?.let { put("colorId", nearestColorId(it)) }
}

/**
 * A PATCH of [fields] only (3a design C3). Switching between timed and all-day sends the other key as null. Google
 * merges the keys of extendedProperties.private, so culvery.createdBy and anything else there is kept; it is never
 * sent. Family or untagged clears the colour.
 */
internal fun patchBody(draft: EventDraft, fields: Set<EventField>): JsonObject = buildJsonObject {
    if (EventField.TITLE in fields) put("summary", draft.title)
    if (EventField.TIMES in fields) {
        put("start", timeJson(draft.start, forPatch = true))
        put("end", timeJson(draft.end, forPatch = true))
    }
    if (EventField.FOR_PERSON in fields) {
        putJsonObject("extendedProperties") {
            putJsonObject("private") { put(PERSON_KEY, draft.forPerson?.let(::JsonPrimitive) ?: JsonNull) }
        }
        put("colorId", draft.forPersonColor?.let { JsonPrimitive(nearestColorId(it)) } ?: JsonNull)
    }
}

private fun timeJson(time: EventTime, forPatch: Boolean): JsonObject = buildJsonObject {
    when (time) {
        is EventTime.Timed -> {
            put("dateTime", time.instant.toString())
            if (forPatch) put("date", JsonNull)
        }
        is EventTime.AllDay -> {
            put("date", time.date.toString())
            if (forPatch) put("dateTime", JsonNull)
        }
    }
}
