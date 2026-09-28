package uk.co.siland.culvery.provider.calendar_google

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** calendarList's access roles, weakest first, for minAccessRole. */
private val ACCESS_ROLES = listOf("freeBusyReader", "reader", "writer", "owner")

/** Google's rule for an event id the client chooses: base32hex (a–v, 0–9), 5 to 1024 characters. */
private val EVENT_ID = Regex("[a-v0-9]{5,1024}")

/**
 * Google Calendar API v3 on a MockWebServer (3a design §7): calendars and their events in memory, sync tokens, pages,
 * cancelled (deleted) events, extended properties and PATCH's merge. All-day dates are read in [zone], the calendar's
 * own zone. Tests seed it, queue failures, hold writes, and read what it was sent.
 */
internal class FakeGoogleServer(private val zone: ZoneId = ZoneId.of("Europe/London")) : Dispatcher() {
    private class Stored(var json: JsonObject, var version: Long)

    private class Failure(val matches: (RecordedRequest) -> Boolean, val response: MockResponse)

    private val server = MockWebServer()
    private val calendars = CopyOnWriteArrayList<JsonObject>()
    private val events = ConcurrentHashMap<String, LinkedHashMap<String, Stored>>()
    private val version = AtomicLong(0)
    private val failures = ConcurrentLinkedQueue<Failure>()
    private val stopped = AtomicBoolean(false)
    @Volatile private var primaryId: String? = null

    /** Every request, in order. */
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** The JSON body of every request that had one, in order (a RecordedRequest's body can be read only once). */
    val bodies = CopyOnWriteArrayList<JsonObject>()

    /** Items per page for both lists. */
    @Volatile var pageSize = 250

    /** Sync tokens older than this get 410 Gone, as when Google expires one. */
    @Volatile var oldestValidToken = 0L

    /** While set, every write waits for it before answering (the R9 check). */
    @Volatile var writeHold: CountDownLatch? = null

    /** Counted down when a write reaches the server, so a test cancels a call that is really in flight. */
    val writeReached = CountDownLatch(1)

    /** The sync token a list would end with now. */
    fun currentSyncToken(): String = "t${version.get()}"

    /** Starts the server and returns the base URL a GoogleApi uses. */
    fun start(): HttpUrl {
        server.dispatcher = this
        server.start()
        return server.url("/calendar/v3/")
    }

    /** Dispatcher's own hook, which MockWebServer also calls while shutting down: hence the guard. */
    override fun shutdown() {
        writeHold?.countDown()
        if (stopped.compareAndSet(false, true)) server.shutdown()
    }

    fun addCalendar(
        id: String,
        summary: String,
        accessRole: String = "owner",
        selected: Boolean = true,
        hidden: Boolean = false,
        primary: Boolean = false,
        summaryOverride: String? = null,
    ) {
        calendars += buildJsonObject {
            put("id", id)
            put("summary", summary)
            summaryOverride?.let { put("summaryOverride", it) }
            put("accessRole", accessRole)
            put("selected", selected)
            if (hidden) put("hidden", true)
            if (primary) put("primary", true)
        }
        events[id] = LinkedHashMap()
        if (primary) primaryId = id
    }

    /** Adds or replaces [event] in [calendarId], as a phone would. */
    fun putEvent(calendarId: String, event: JsonObject) {
        val stored = events.getValue(calendarId)
        synchronized(stored) { stored[event.id] = Stored(event, version.incrementAndGet()) }
    }

    /** Deletes an event as a phone would: it stays, cancelled. */
    fun cancel(calendarId: String, eventId: String) {
        val stored = events.getValue(calendarId)
        synchronized(stored) {
            val e = stored.getValue(eventId)
            e.json = JsonObject(e.json + ("status" to JsonPrimitive("cancelled")))
            e.version = version.incrementAndGet()
        }
    }

    fun event(calendarId: String, eventId: String): JsonObject? {
        val stored = events[calendarId] ?: return null
        return synchronized(stored) { stored[eventId]?.json }
    }

    /** The next request [matches] gets [status] with Google's error body for [reason]. */
    fun failNext(status: Int, reason: String? = null, matches: (RecordedRequest) -> Boolean = { true }) {
        failures += Failure(matches, error(status, reason ?: "backendError"))
    }

    fun failNextWith(response: MockResponse, matches: (RecordedRequest) -> Boolean = { true }) {
        failures += Failure(matches, response)
    }

    fun timed(id: String, summary: String?, start: Instant, end: Instant, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("id", id)
            put("status", "confirmed")
            summary?.let { put("summary", it) }
            putJsonObject("start") { put("dateTime", start.toString()) }
            putJsonObject("end") { put("dateTime", end.toString()) }
            extra()
        }

    fun allDay(id: String, summary: String?, start: LocalDate, endExclusive: LocalDate, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
        buildJsonObject {
            put("id", id)
            put("status", "confirmed")
            summary?.let { put("summary", it) }
            putJsonObject("start") { put("date", start.toString()) }
            putJsonObject("end") { put("date", endExclusive.toString()) }
            extra()
        }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val body = request.body.readUtf8().takeIf { it.isNotBlank() }?.let { GoogleJson.parseToJsonElement(it).jsonObject }
        body?.let { bodies += it }
        failures.firstOrNull { it.matches(request) }?.let {
            failures.remove(it)
            return it.response
        }
        val url = request.requestUrl ?: return error(400, "badRequest")
        // After "calendar/v3".
        val path = url.pathSegments.drop(2)
        val method = request.method
        return when {
            path == listOf("users", "me", "calendarList") && method == "GET" -> calendarList(url)
            path.size == 2 && path[0] == "calendars" && method == "GET" -> calendar(path[1])
            path.size == 3 && path[0] == "calendars" && path[2] == "events" && method == "GET" -> list(path[1], url)
            path.size == 3 && path[0] == "calendars" && path[2] == "events" && method == "POST" -> held { insert(path[1], body ?: JsonObject(emptyMap())) }
            path.size == 4 && path[0] == "calendars" && path[2] == "events" && method == "GET" -> get(path[1], path[3])
            path.size == 4 && path[0] == "calendars" && path[2] == "events" && method == "PATCH" -> held { patch(path[1], path[3], body ?: JsonObject(emptyMap())) }
            path.size == 4 && path[0] == "calendars" && path[2] == "events" && method == "DELETE" -> held { delete(path[1], path[3]) }
            else -> error(404, "notFound")
        }
    }

    private fun held(answer: () -> MockResponse): MockResponse {
        writeReached.countDown()
        writeHold?.await()
        return answer()
    }

    /** As Google: hidden calendars only with showHidden=true, and none weaker than minAccessRole. */
    private fun calendarList(url: HttpUrl): MockResponse {
        val showHidden = url.queryParameter("showHidden") == "true"
        val weakest = url.queryParameter("minAccessRole")?.let(ACCESS_ROLES::indexOf) ?: 0
        val listed = calendars.filter { c ->
            (showHidden || c["hidden"] == null) && ACCESS_ROLES.indexOf(c.string("accessRole")) >= weakest
        }
        val from = url.queryParameter("pageToken")?.toInt() ?: 0
        val page = listed.drop(from).take(pageSize)
        val next = from + pageSize
        return ok(
            buildJsonObject {
                put("items", JsonArray(page))
                if (next < listed.size) put("nextPageToken", next.toString())
            },
        )
    }

    private fun calendar(id: String): MockResponse {
        val found = if (id == "primary") primaryId else calendars.firstOrNull { it.id == id }?.id
        return found?.let { ok(buildJsonObject { put("id", it) }) } ?: error(404, "notFound")
    }

    private fun list(calendarId: String, url: HttpUrl): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        val syncToken = url.queryParameter("syncToken")
        val timeMin = url.queryParameter("timeMin")
        val timeMax = url.queryParameter("timeMax")
        // Google refuses a sync token with a time range.
        if (syncToken != null && (timeMin != null || timeMax != null)) return error(400, "invalid")
        val since = syncToken?.let { t -> t.removePrefix("t").toLongOrNull()?.takeIf { it >= oldestValidToken } ?: return error(410, "fullSyncRequired") }
        val all = synchronized(stored) { stored.values.toList() }
            // singleEvents=true: a series' master isn't listed, only its instances.
            .filter { it.json["recurrence"] == null }
            .filter { s -> if (since != null) s.version > since else s.json.status != "cancelled" && overlaps(s.json, timeMin, timeMax) }
        val from = url.queryParameter("pageToken")?.toInt() ?: 0
        val next = from + pageSize
        return ok(
            buildJsonObject {
                put("items", JsonArray(all.drop(from).take(pageSize).map { it.json }))
                if (next < all.size) put("nextPageToken", next.toString()) else put("nextSyncToken", currentSyncToken())
            },
        )
    }

    private fun get(calendarId: String, eventId: String): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        // A deleted event comes back with status "cancelled", as Google returns it.
        return synchronized(stored) { stored[eventId] }?.let { ok(it.json) } ?: error(404, "notFound")
    }

    private fun insert(calendarId: String, body: JsonObject): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        if (!writable(calendarId)) return error(403, "forbidden")
        val given = body["id"]?.jsonPrimitive?.content
        if (given != null && !EVENT_ID.matches(given)) return error(400, "invalid")
        val id = given ?: "g${version.incrementAndGet()}"
        synchronized(stored) {
            if (id in stored) return error(409, "duplicate")
            val event = JsonObject(body + ("id" to JsonPrimitive(id)) + ("status" to JsonPrimitive("confirmed")))
            stored[id] = Stored(event, version.incrementAndGet())
            return ok(event)
        }
    }

    private fun patch(calendarId: String, eventId: String, body: JsonObject): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        if (!writable(calendarId)) return error(403, "forbidden")
        synchronized(stored) {
            val e = stored[eventId] ?: return error(404, "notFound")
            // As Google: a PATCH on a deleted event answers 200, and the event stays deleted (the body never sets status).
            e.json = merge(e.json, body)
            e.version = version.incrementAndGet()
            return ok(e.json)
        }
    }

    private fun delete(calendarId: String, eventId: String): MockResponse {
        val stored = events[calendarId] ?: return error(404, "notFound")
        if (!writable(calendarId)) return error(403, "forbidden")
        synchronized(stored) {
            val e = stored[eventId] ?: return error(404, "notFound")
            if (e.json.status == "cancelled") return error(410, "deleted")
            e.json = JsonObject(e.json + ("status" to JsonPrimitive("cancelled")))
            e.version = version.incrementAndGet()
            return MockResponse().setResponseCode(204)
        }
    }

    private fun writable(calendarId: String) = calendars.firstOrNull { it.id == calendarId }?.string("accessRole") in setOf("owner", "writer")

    private fun overlaps(event: JsonObject, timeMin: String?, timeMax: String?): Boolean {
        val start = instantOf(event["start"]?.jsonObject) ?: return false
        val end = instantOf(event["end"]?.jsonObject) ?: return false
        val after = timeMin?.let { Instant.parse(it) }
        val before = timeMax?.let { Instant.parse(it) }
        return (before == null || start < before) && (after == null || end > after)
    }

    private fun instantOf(time: JsonObject?): Instant? {
        time ?: return null
        time.string("dateTime")?.let { return OffsetDateTime.parse(it).toInstant() }
        return time.string("date")?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant() }
    }

    private fun ok(json: JsonObject) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(json.toString())

    private fun error(status: Int, reason: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(
            buildJsonObject {
                putJsonObject("error") {
                    put("code", status)
                    put("message", "Google's own words for $reason")
                    put("errors", buildJsonArray { add(buildJsonObject { put("reason", reason) }) })
                }
            }.toString(),
        )
}

private val JsonObject.id: String get() = getValue("id").jsonPrimitive.content

private val JsonObject.status: String? get() = string("status")

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Google's PATCH: a key set to null goes, an object merges into the one it replaces, anything else replaces it. */
private fun merge(into: JsonObject, patch: JsonObject): JsonObject {
    val out = into.toMutableMap()
    for ((key, value) in patch) {
        val old = out[key]
        when {
            value is JsonNull -> out.remove(key)
            value is JsonObject && old is JsonObject -> out[key] = merge(old, value)
            else -> out[key] = value
        }
    }
    return JsonObject(out)
}
