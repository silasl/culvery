package uk.co.siland.culvery

import kotlinx.coroutines.flow.first
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
 * exists. Guarded on an active Admin (not an empty household) so it never adds a second set of people.
 */
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager, calendar: CalendarSetup) {
    if (household.credentials().none { it.isActiveAdmin }) {
        SEED_PEOPLE.forEach { p ->
            val person = household.addPerson(p.name, p.color, p.role)
            pins.setPin(person.id, p.pin)
        }
    }
    if (!calendar.hasConnections()) {
        val byName = household.people.first().associate { it.name to it.id }
        val mapping = SEED_PEOPLE.associate { p ->
            p.source to SourceMapping(byName[p.name] ?: PersonId.FAMILY, visible = true)
        } + mapOf(
            FakeCalendarProvider.SOURCE_FAMILY to SourceMapping(PersonId.FAMILY, visible = true),
            FakeCalendarProvider.SOURCE_SCHOOL to SourceMapping(PersonId.FAMILY, visible = true),
        )
        calendar.connect(Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()), mapping)
    }
}
