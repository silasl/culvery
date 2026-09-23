package uk.co.siland.culvery.capability.calendar_testkit.fixtures

import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest
import uk.co.siland.culvery.core.plugin.Connection

abstract class TinyContract(private val tiny: TinyProvider) : CalendarProviderContractTest() {
    override fun provider() = tiny
    override fun connection() = Connection("c", "calendar.tiny", "Tiny", emptyMap())
    override fun range() = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
    override fun sourceWithEvents() = TinyProvider.SOURCE
    override fun outOfRangeEventTitle() = "Far away"
    override fun recurringTitle() = "Yoga"
    override fun simulateAuthFailure() = { tiny.failNextWith(NeedsSignInException("expired")) }
    override fun simulateUnreachable() = { tiny.failNextWith(UnreachableException("offline")) }
}

class GoodContract : TinyContract(TinyProvider())
class LeakingContract : TinyContract(TinyProvider(leakOutOfRange = true))
class PartialFirstSyncContract : TinyContract(TinyProvider(partialFirstSync = true))
class RepeatingContract : TinyContract(TinyProvider(repeatOnCursor = true))
class RawErrorContract : TinyContract(TinyProvider(rawNetworkErrors = true))
class InclusiveAllDayEndContract : TinyContract(TinyProvider(inclusiveAllDayEnd = true))
class RawAuthErrorContract : TinyContract(TinyProvider(rawAuthErrors = true))
