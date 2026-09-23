package uk.co.siland.culvery.provider.calendar_fake

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest
import uk.co.siland.culvery.core.plugin.Connection

class FakeCalendarProviderContractTest : CalendarProviderContractTest() {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val fake = FakeCalendarProvider(Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone))

    override fun provider() = fake
    override fun connection() = Connection("c1", FakeCalendarProvider.ID, "Sample calendar", emptyMap())
    override fun range() = DateRange(today.minusDays(1), today.plusDays(15), zone)
    override fun sourceWithEvents() = CalendarSource(FakeCalendarProvider.SOURCE_MIA, "Mia", writable = false)
    override fun outOfRangeEventTitle() = "School trip"
    override fun recurringTitle() = "Piano"
    override fun simulateAuthFailure() = { fake.failNextWith(NeedsSignInException("expired")) }
    override fun simulateUnreachable() = { fake.failNextWith(UnreachableException("offline")) }
}
