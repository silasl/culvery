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

/** 3a design D7: the master calendar went, or became read-only, so the add buttons hide. */
fun masterGone(serviceName: String): String = "$serviceName: can't find the master calendar, so new events can't be added"

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
    private val flagged = ConcurrentHashMap.newKeySet<String>()

    /** A sync found one of [connectionId]'s sources gone (SourceGoneException): refresh at the next pass. */
    fun flag(connectionId: String) {
        flagged += connectionId
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
        val due = id !in refreshed || id in flagged || checked == null || now - checked >= SOURCE_REFRESH_MS
        if (!due) return
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
        flagged -= id
        if (masterCleared) toaster.show(masterGone(provider.descriptor.displayName))
    }

    private companion object {
        const val TAG = "SourceRefresher"
    }
}
