package uk.co.siland.culvery.provider.calendar_google

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SourceGoneException
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.Toaster

private const val TAG = "GoogleCalendar"
private const val PAGE_SIZE = "250"
private val WRITE_ROLES = setOf("owner", "writer")

/**
 * calendarList's query (3a design §3.5): hidden calendars too, so hiding one in Google hides it rather than deleting it
 * and its mapping; none the account can see only as free/busy, whose events it can't read.
 */
private val CALENDAR_LIST_QUERY = mapOf("showHidden" to "true", "minAccessRole" to "reader")

/**
 * Google Calendar API v3 (3a design §3.1–§3.7). The connection's config holds only the account's email; every call
 * takes a fresh token from Play services. Each series' RRULE is fetched once and kept in memory per calendar until
 * that calendar's next full sync.
 */
@Singleton
class GoogleCalendarProvider @Inject constructor(
    private val api: GoogleApi,
    private val authorizer: Authorizer,
    private val toaster: Toaster,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(GOOGLE_PROVIDER_ID, GOOGLE_DISPLAY_NAME, "calendar_month", setOf(Feature.READ))

    // By connection and calendar: each series' RRULE, "" for a series with none.
    private val rules = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    private class Listed(val items: List<GoogleEvent>, val syncToken: String?)

    /**
     * Runs the account chooser and Google's consent through Play services (3a design §3.2). CalendarConnectHost draws
     * the card around it; this draws nothing and only follows the flow. An account already granted needs no screens.
     */
    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        val flow = remember { GoogleConnectFlow(authorizer, api, toaster) }
        val scope = rememberCoroutineScope()
        val connected by rememberUpdatedState(onConnected)
        val cancelled by rememberUpdatedState(onCancel)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // Backed out of the chooser or the consent screen: the card closes with nothing said (3a design §3.2).
            if (result.resultCode != Activity.RESULT_OK) {
                cancelled()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                // Play services has its answer now; screens asked for a second time read as a stop.
                when (val step = flow.afterScreens(existing, result.data)) {
                    is ConnectStep.Done -> connected(step.connection)
                    else -> cancelled()
                }
            }
        }
        LaunchedEffect(existing) {
            when (val step = flow.start(existing)) {
                is ConnectStep.Done -> connected(step.connection)
                is ConnectStep.ShowScreens -> launcher.launch(IntentSenderRequest.Builder(step.intent.intentSender).build())
                ConnectStep.Stopped -> cancelled()
            }
        }
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> {
        val account = accountOf(conn)
        val sources = mutableListOf<CalendarSource>()
        var pageToken: String? = null
        do {
            val url = api.url("users", "me", "calendarList", query = CALENDAR_LIST_QUERY + ("pageToken" to pageToken))
            val page = api.send(account, "GET", url)
                .readOrUnreachable("calendarList.list")
                .decode(CalendarListPage.serializer(), "calendarList.list")
            page.items.forEach { e ->
                sources += CalendarSource(
                    id = e.id,
                    name = e.summaryOverride ?: e.summary ?: e.id,
                    writable = e.accessRole in WRITE_ROLES,
                    shown = e.selected == true && e.hidden != true,
                    primary = e.primary == true,
                )
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        // Google always lists the account's own calendar: a list without it is a bad answer, never "every calendar was
        // deleted", which would remove them all with their events and queued changes (3a design §3.4).
        if (sources.none { it.primary }) {
            Log.w(TAG, "calendarList.list: the list came back without the primary calendar; treating it as a failed read")
            throw UnreachableException("Google Calendar listed no primary calendar")
        }
        return sources
    }

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        val account = accountOf(conn)
        // 410 Gone: Google expired the sync token, so the provider runs a full sync itself (3a design §3.5).
        if (cursor != null) incremental(conn, account, source, cursor)?.let { return it }
        return full(conn, account, source, range)
    }

    private suspend fun full(conn: Connection, account: String, source: CalendarSource, range: DateRange): SyncResult {
        rulesFor(conn, source).clear()
        val window = mapOf("timeMin" to range.startInstant.toString(), "timeMax" to range.endInstant.toString())
        val listed = list(account, source, window) ?: throw UnreachableException("Google Calendar expired a full sync of a calendar")
        val failed = mutableSetOf<String>()
        val upserts = listed.items.filterNot { it.isGone }.mapNotNull { it.toRemoteEvent(ruleOf(conn, account, source, it, failed)) }
        return SyncResult(upserts, emptyList(), listed.syncToken?.let(::SyncCursor), fullReplace = true)
    }

    private suspend fun incremental(conn: Connection, account: String, source: CalendarSource, cursor: SyncCursor): SyncResult? {
        val listed = list(account, source, mapOf("syncToken" to cursor.value)) ?: return null
        val upserts = mutableListOf<RemoteEvent>()
        val removed = mutableListOf<String>()
        val failed = mutableSetOf<String>()
        listed.items.forEach { event ->
            if (event.isGone) {
                removed += event.id
            } else {
                event.toRemoteEvent(ruleOf(conn, account, source, event, failed))?.let { upserts += it }
            }
        }
        return SyncResult(upserts, removed, listed.syncToken?.let(::SyncCursor) ?: cursor, fullReplace = false)
    }

    /**
     * Every page of events.list with [query]; null for 410 Gone. A deleted or forbidden calendar is SourceGone (§3.5).
     * Logs and messages name no calendar (P8): its id and name are often the account's email.
     */
    private suspend fun list(account: String, source: CalendarSource, query: Map<String, String>): Listed? {
        val items = mutableListOf<GoogleEvent>()
        var pageToken: String? = null
        while (true) {
            val url = api.url(
                "calendars", source.id, "events",
                query = query + mapOf("singleEvents" to "true", "maxResults" to PAGE_SIZE, "pageToken" to pageToken),
            )
            val answer = api.send(account, "GET", url)
            if (answer.code == 410) return null
            if (answer.code == 404 || answer.code == 403) {
                Log.w(TAG, "events.list: Google Calendar answered ${answer.code} (${answer.reason})")
                throw SourceGoneException("A calendar isn't in Google Calendar any more")
            }
            val page = answer.readOrUnreachable("events.list").decode(EventsPage.serializer(), "events.list")
            // Every real page carries one or the other; one with neither (a bare {}) is a bad answer (3a design §3.7).
            if (page.nextPageToken == null && page.nextSyncToken == null) {
                Log.w(TAG, "events.list: Google Calendar sent a page with no page or sync token")
                throw UnreachableException("Google Calendar sent an events page the tablet can't follow")
            }
            items += page.items
            pageToken = page.nextPageToken ?: return Listed(items, page.nextSyncToken)
        }
    }

    /**
     * An instance's series rule (3a design D12): fetched once per series. A failed fetch is null, doesn't fail the
     * sync, and is remembered in [failed] for the rest of this sync, so the series' other instances don't ask again.
     */
    private suspend fun ruleOf(
        conn: Connection,
        account: String,
        source: CalendarSource,
        event: GoogleEvent,
        failed: MutableSet<String>,
    ): String? {
        val series = event.recurringEventId ?: return null
        if (series in failed) return null
        val cache = rulesFor(conn, source)
        cache[series]?.let { return it.ifEmpty { null } }
        val rule = try {
            api.send(account, "GET", api.url("calendars", source.id, "events", series))
                .readOrUnreachable("events.get series")
                .decode(GoogleEvent.serializer(), "events.get series")
                .recurrence
                ?.firstOrNull { it.startsWith("RRULE:") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not the exception: only its type, in case its message quotes Google's answer (P8).
            Log.w(TAG, "events.get series: couldn't read a series' rule (${e::class.simpleName}); its Repeats row says Yes")
            failed += series
            return null
        }
        cache[series] = rule.orEmpty()
        return rule
    }

    private fun rulesFor(conn: Connection, source: CalendarSource) = rules.getOrPut("${conn.id}\u0000${source.id}") { ConcurrentHashMap() }

    private fun accountOf(conn: Connection): String =
        conn.config[CONFIG_ACCOUNT] ?: throw NeedsSignInException("The Google connection ${conn.id} has no account")
}
