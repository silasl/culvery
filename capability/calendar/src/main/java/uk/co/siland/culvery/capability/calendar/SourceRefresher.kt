package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** How often a connection's sources are read again from the provider (3a design D7). */
const val SOURCE_REFRESH_MS = 24 * 60 * 60_000L

/** 3a design D7, 4a design §4.5: the master calendar went, or became read-only, so the add buttons hide until another is chosen. */
fun masterGone(serviceName: String): String = "$serviceName: can't find the master calendar — choose a new one in Settings › Calendars."

/**
 * Follows the calendars ticked in the service (3a design §3.4, D7): at the start of a connection's part of a pass,
 * when it hasn't been refreshed since the app started, when its last refresh is a day old, or after [flag].
 */
@Singleton
class SourceRefresher internal constructor(
    private val store: CalendarStore,
    private val people: suspend () -> List<Person>,
    private val clock: WallClock,
    private val toaster: Toaster,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
) {
    @Inject
    constructor(store: CalendarStore, household: HouseholdRepository, clock: WallClock, toaster: Toaster) :
        this(store, { household.people.first() }, clock, toaster, Dispatchers.IO, PROVIDER_TIMEOUT_MS)

    private val refreshed = ConcurrentHashMap.newKeySet<String>()

    // Per connection: the sources a sync found gone, waiting for a refresh.
    private val flagged = ConcurrentHashMap<String, MutableSet<String>>()

    // Per connection: sources found gone that a flagged refresh saw still listed. Their SourceGone is ignored until
    // the next scheduled refresh, or every pass would read the calendar list again.
    private val stillListed = ConcurrentHashMap<String, MutableSet<String>>()

    /** A sync found [sourceId] gone (SourceGoneException): refresh [connectionId] at the next pass. */
    fun flag(connectionId: String, sourceId: String) {
        if (stillListed[connectionId]?.contains(sourceId) == true) return
        flagged.getOrPut(connectionId) { ConcurrentHashMap.newKeySet() } += sourceId
    }

    /**
     * A failure to read the sources is logged and tried again at the next pass; the sync still runs on the sources
     * already stored. A list without the primary calendar counts as one (3a design §3.4): it is a bad answer, never
     * "every calendar was deleted", which would remove them all with their events and queued changes. A store failure
     * fails the pass, as any other.
     */
    suspend fun refreshIfDue(provider: CalendarProvider, stored: StoredConnection) {
        val id = stored.connection.id
        val now = clock.nowMillis()
        val checked = stored.sourcesCheckedMillis
        // A check in the future means the clock was set back.
        val scheduled = id !in refreshed || checked == null || checked > now || now - checked >= SOURCE_REFRESH_MS
        val gone = flagged[id].orEmpty().toSet()
        if (!scheduled && gone.isEmpty()) return
        // Logged by connection id only: a calendar's name or id can be the account's email.
        val sources = callReader(io, timeoutMillis) { provider.sources(stored.connection) }.getOrElse {
            Log.w(TAG, "$id: couldn't read its calendars; trying again next pass", it)
            return
        }
        if (sources.none { it.primary }) {
            Log.w(TAG, "$id: its calendar list has no primary calendar; treating it as a failed read")
            return
        }
        val household = people()
        val masterCleared = store.refreshSources(id, sources, now) { defaultMapping(it, household) }
        refreshed += id
        flagged[id]?.removeAll(gone)
        val listed = sources.mapTo(HashSet()) { it.id }
        val kept = stillListed.getOrPut(id) { ConcurrentHashMap.newKeySet() }
        if (scheduled) kept.clear()
        kept += gone.filter { it in listed }
        if (masterCleared) toaster.show(masterGone(provider.descriptor.displayName))
    }

    private companion object {
        const val TAG = "SourceRefresher"
    }
}
