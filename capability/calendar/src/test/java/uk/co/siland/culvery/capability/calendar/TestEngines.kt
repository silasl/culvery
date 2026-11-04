package uk.co.siland.culvery.capability.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

// Every calendar test builds its editor and its engine here, so a constructor that grows touches one place. Calls
// stay on the test's dispatcher unless a test passes io; each gets its own write lock unless it shares one.

internal fun testEditor(
    store: CalendarStore,
    writers: Set<CalendarWriter>,
    access: AccessControl,
    toaster: Toaster,
    zone: HouseholdZone,
    clock: WallClock,
    scope: CoroutineScope,
    requestSync: () -> Unit = {},
    io: CoroutineContext = EmptyCoroutineContext,
    writeLock: CalendarWriteLock = CalendarWriteLock(),
    personOf: suspend (PersonId) -> Person? = { null },
    serviceOf: (providerId: String) -> String? = { null },
    order: suspend () -> List<PersonId> = { emptyList() },
    undone: UndoneKeys = UndoneKeys(),
    shown: () -> WeeksShown = { WeeksShown.EIGHT },
    updates: UpdateRequests = UpdateRequests(),
): CalendarEditor =
    CalendarEditor(store, writers, access, toaster, zone, clock, scope, requestSync, io, WRITE_ATTEMPT_MS, writeLock, personOf, serviceOf, order, undone, shown, updates)

/** The Calendar's setting, held in memory and read at once; [shown] is what it starts at. */
internal fun testCalendarSettings(shown: WeeksShown = WeeksShown.EIGHT): CalendarSettings {
    val store = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
    return CalendarSettings(store, CoroutineScope(Dispatchers.Unconfined)).also { runBlocking { it.setShown(shown) } }
}

internal fun testSync(
    store: CalendarStore,
    providers: Set<CalendarProvider>,
    zone: HouseholdZone,
    clock: WallClock,
    writers: Set<CalendarWriter>,
    toaster: Toaster,
    io: CoroutineContext = EmptyCoroutineContext,
    timeoutMillis: Long = PROVIDER_TIMEOUT_MS,
    writeLock: CalendarWriteLock = CalendarWriteLock(),
    refresher: SourceRefresher = SourceRefresher(store, { emptyList() }, clock, toaster, io, timeoutMillis),
    undone: UndoneKeys = UndoneKeys(),
): CalendarSync = CalendarSync(store, providers, zone, clock, io, timeoutMillis, writers, toaster, writeLock, refresher, undone)
