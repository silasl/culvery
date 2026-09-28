package uk.co.siland.culvery

import android.util.Log
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private class SeedPerson(val name: String, val color: Long, val role: Role, val pin: String, val source: String)

private val SEED_PEOPLE = listOf(
    SeedPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234", FakeCalendarProvider.SOURCE_ALEX),
    SeedPerson("Sam", 0xFF5B9BE0, Role.ADULT, "2468", FakeCalendarProvider.SOURCE_SAM),
    SeedPerson("Mia", 0xFFE07BA8, Role.CHILD, "1357", FakeCalendarProvider.SOURCE_MIA),
)

private const val DEBUG_CONNECTION_ID = "debug-sample"

/**
 * Debug builds only: the hand-off's family and a sample calendar, so the app is usable before the setup wizard
 * exists. People are added only when there is no active Admin, so a second set is never added. Every start
 * re-tags the sample week with the people's ids (the fake keeps them in memory) and, once, makes the sample
 * "Family calendar" the writable master (2b-1 design D5).
 */
suspend fun seedDebugData(
    household: HouseholdRepository,
    pins: PinManager,
    calendar: CalendarSetup,
    providers: Set<CalendarProvider>,
) {
    if (household.credentials().none { it.isActiveAdmin }) {
        SEED_PEOPLE.forEach { p ->
            val person = household.addPerson(p.name, p.color, p.role)
            pins.setPin(person.id, p.pin)
        }
    }
    val byName = household.people.first().associate { it.name to it.id }
    val fake = providers.filterIsInstance<FakeCalendarProvider>().singleOrNull()
    fake?.tagSamples(byName.mapValues { it.value.value })
    if (!calendar.hasConnections()) {
        val mapping = SEED_PEOPLE.associate { p ->
            p.source to SourceMapping(byName[p.name] ?: PersonId.FAMILY, visible = true)
        } + mapOf(
            FakeCalendarProvider.SOURCE_FAMILY to SourceMapping(PersonId.FAMILY, visible = true),
            FakeCalendarProvider.SOURCE_SCHOOL to SourceMapping(PersonId.FAMILY, visible = true),
        )
        calendar.connect(Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()), mapping)
    }
    if (fake != null && calendar.master() == null && DEBUG_CONNECTION_ID in calendar.connectionIds().first()) {
        try {
            calendar.setMaster(DEBUG_CONNECTION_ID, FakeCalendarProvider.SOURCE_FAMILY)
        } catch (e: IllegalArgumentException) {
            Log.w("Culvery", "Couldn't make the sample Family calendar the master", e)
        }
    }
    // The tags may have changed after the start-up sync ran.
    calendar.syncSoon()
}

/**
 * Debug builds only (3a design D5, §3.9): once any other connection exists, the sample connection goes, with its events,
 * sync state and queue, so sample and real events never mix; with a connection, no later start seeds it again.
 */
suspend fun removeSampleWhenReplaced(calendar: CalendarSetup) {
    calendar.connectionIds().collect { ids ->
        if (DEBUG_CONNECTION_ID in ids && ids.any { it != DEBUG_CONNECTION_ID }) calendar.removeConnection(DEBUG_CONNECTION_ID)
    }
}
