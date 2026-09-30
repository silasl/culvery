package uk.co.siland.culvery

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.setup.SampleHousehold
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private class SamplePerson(val name: String, val color: Long, val role: Role, val pin: String, val source: String)

/** The hand-off's family, in the palette's first three colours. */
private val SAMPLE_PEOPLE = listOf(
    SamplePerson("Alex", PersonPalette.colors[0], Role.ADMIN, "1234", FakeCalendarProvider.SOURCE_ALEX),
    SamplePerson("Sam", PersonPalette.colors[1], Role.ADULT, "2468", FakeCalendarProvider.SOURCE_SAM),
    SamplePerson("Mia", PersonPalette.colors[2], Role.CHILD, "1357", FakeCalendarProvider.SOURCE_MIA),
)

internal val SAMPLE_HOME = HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London")

internal const val DEBUG_CONNECTION_ID = "debug-sample"

/**
 * Debug builds only, at every start (4a design D11): once the sample calendar is connected, re-tags its week with the
 * people's ids, which the fake keeps only in memory. It creates nobody: a fresh install runs the real wizard.
 */
suspend fun seedDebugData(household: HouseholdRepository, calendar: CalendarSetup, providers: Set<CalendarProvider>) {
    val fake = providers.filterIsInstance<FakeCalendarProvider>().singleOrNull() ?: return
    if (DEBUG_CONNECTION_ID !in calendar.connectionIds().first()) return
    fake.tagSamples(household.people.first().associate { it.name to it.id.value })
    // The tags may have changed after the start-up sync ran.
    calendar.syncSoon()
}

/**
 * Welcome's **Use a sample household** (4a design D11): Alex (Admin, 1234), Sam (Adult, 2468) and Mia (Child, 1357),
 * London, and the sample calendar with each person's calendar mapped to them and the Family calendar as the master; then
 * setup is complete, so Home opens.
 */
class DebugSampleHousehold(
    private val household: HouseholdRepository,
    private val pins: PinManager,
    private val calendar: CalendarSetup,
    private val providers: Set<CalendarProvider>,
    private val markComplete: suspend () -> Unit,
) : SampleHousehold {
    @Inject
    constructor(
        household: HouseholdRepository,
        pins: PinManager,
        calendar: CalendarSetup,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        state: SetupState,
    ) : this(household, pins, calendar, providers, state::markComplete)

    override suspend fun create() {
        val fake = providers.filterIsInstance<FakeCalendarProvider>().single()
        val ids = SAMPLE_PEOPLE.associate { p -> p.name to pins.addPerson(p.name, p.color, p.role, p.pin).id }
        household.setLocation(SAMPLE_HOME)
        fake.tagSamples(ids.mapValues { it.value.value })
        val mapping = SAMPLE_PEOPLE.associate { p -> p.source to SourceMapping(ids.getValue(p.name), visible = true) } + mapOf(
            FakeCalendarProvider.SOURCE_FAMILY to SourceMapping(PersonId.FAMILY, visible = true),
            FakeCalendarProvider.SOURCE_SCHOOL to SourceMapping(PersonId.FAMILY, visible = true),
        )
        calendar.connect(Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()), mapping)
        try {
            calendar.setMaster(DEBUG_CONNECTION_ID, FakeCalendarProvider.SOURCE_FAMILY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The sample still opens; events can be added once a master is chosen in Settings › Calendars.
            Log.w("Culvery", "Couldn't make the sample Family calendar the master (${e::class.simpleName})")
        }
        markComplete()
    }
}

/**
 * Debug builds only (3a design D5, §3.9): once any other connection exists, the sample connection goes, with its events,
 * sync state and queue, so sample and real events never mix.
 */
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) {
    calendar.connectionIds().collect { ids ->
        if (DEBUG_CONNECTION_ID in ids && ids.any { it != DEBUG_CONNECTION_ID }) calendar.removeConnection(DEBUG_CONNECTION_ID)
    }
}
