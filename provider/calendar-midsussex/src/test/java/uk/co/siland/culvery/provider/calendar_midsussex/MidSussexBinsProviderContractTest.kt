package uk.co.siland.culvery.provider.calendar_midsussex

import java.time.LocalDate
import java.time.LocalTime
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.UpdateRequests
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest

/** The shared contract, against the council's pages from the fake server. */
@RunWith(RobolectricTestRunner::class)
class MidSussexBinsProviderContractTest : CalendarProviderContractTest() {
    @get:Rule
    val dir = TemporaryFolder()

    private val council = FakeCouncil()
    private val base = council.start()
    private val subject by lazy {
        MidSussexBinsProvider(testCouncil(base), testReads(dir.root), FakeWall(LocalDate.of(2031, 1, 1), LocalTime.NOON), UpdateRequests()) { LONDON }
    }

    @After
    fun tearDown() = council.shutdown()

    override fun provider() = subject
    override fun connection() = binsConnection()
    override fun range() = DateRange(LocalDate.of(2031, 1, 1), LocalDate.of(2031, 1, 31), LONDON)
    override fun sourceWithEvents() = SOURCE_BINS
    override fun simulateUnreachable() = { council.shutdown() }
}
