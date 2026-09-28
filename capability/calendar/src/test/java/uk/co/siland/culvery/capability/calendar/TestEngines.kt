package uk.co.siland.culvery.capability.calendar

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import uk.co.siland.culvery.core.access.AccessControl
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
    newKey: () -> String = ::newClientKey,
): CalendarEditor = CalendarEditor(store, writers, access, toaster, zone, clock, scope, requestSync, io, WRITE_ATTEMPT_MS, writeLock, newKey)

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
): CalendarSync = CalendarSync(store, providers, zone, clock, io, timeoutMillis, writers, toaster, writeLock)
