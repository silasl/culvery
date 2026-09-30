package uk.co.siland.culvery.capability.calendar

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.plugin.WallClock

private object NobodyMay : AccessControl {
    override val session: StateFlow<Identified?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? = null

    override fun lock() = Unit

    override fun beginSetupSession(person: Identified) = Unit

    override fun endSetupSession() = Unit

    override fun touch() = Unit
}

/**
 * An editor that can't change anything, for tests that only need the hosts to compose and open sheets. [clock] is
 * "now" for an add sheet the test opens.
 */
internal fun stubEditor(store: CalendarStore, zone: HouseholdZone, clock: WallClock = WallClock { 0L }): CalendarEditor =
    testEditor(store, emptySet(), NobodyMay, RecordingToaster(), zone, clock, CoroutineScope(Dispatchers.Unconfined))

/** Connections that nobody may make, for tests that only need the capability's screens to compose. */
internal fun stubConnections(store: CalendarStore): CalendarConnections = CalendarConnections(
    store,
    CalendarSetup(store, emptySet(), { emptyList() }, RecordingToaster(), WallClock { 0L }, EmptyCoroutineContext),
    NobodyMay,
    emptySet(),
    CoroutineScope(Dispatchers.Unconfined),
)
