package uk.co.siland.culvery.provider.calendar_midsussex

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.UnreadableException
import uk.co.siland.culvery.capability.calendar.UpdateRequests
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

@RunWith(RobolectricTestRunner::class)
class MidSussexBinsProviderTest {
    @get:Rule
    val dir = TemporaryFolder(File("C:/t9").apply { mkdirs() })

    private val today = LocalDate.of(2026, 11, 4)
    private val council = FakeCouncil()
    private val base = council.start()
    private val wall = FakeWall(today, LocalTime.of(8, 0))
    private val updates = UpdateRequests()
    private val reads by lazy { testReads(dir.root) }
    private val range = DateRange(today.minusDays(1), today.plusDays(127), LONDON)

    private fun provider() = MidSussexBinsProvider(testCouncil(base), reads, wall, updates)

    private val subject by lazy { provider() }

    @After
    fun tearDown() = council.shutdown()

    private suspend fun MidSussexBinsProvider.read(conn: Connection = binsConnection(), within: DateRange = range): SyncResult =
        sync(conn, SOURCE_BINS, within, null)

    private suspend fun MidSussexBinsProvider.failure(): Throwable? =
        try {
            read()
            null
        } catch (e: Exception) {
            e
        }

    private suspend fun MidSussexBinsProvider.unreadable(): UnreadableException {
        val e = failure()
        assertWithMessage("$e").that(e).isInstanceOf(UnreadableException::class.java)
        return e as UnreadableException
    }

    private fun SyncResult.days(): List<LocalDate> = upserts.map { (it.start as EventTime.AllDay).date }

    @Test
    fun oneReadADay() = runTest {
        repeat(3) {
            subject.read()
            wall.plusMinutes(5)
        }
        assertThat(council.requests).isEqualTo(3)

        wall.at(today.plusDays(1), LocalTime.of(0, 5))
        repeat(3) {
            subject.read()
            wall.plusMinutes(5)
        }
        assertThat(council.requests).isEqualTo(6)
    }

    @Test
    fun aReadGivesOneAllDayEventPerCollectionDay() = runTest {
        val result = subject.read()

        assertThat(result.fullReplace).isTrue()
        assertThat(result.cursor).isNull()
        val mixed = result.upserts.single { it.remoteId == "bins-2026-11-16" }
        assertThat(mixed.title).isEqualTo("Bins: Food, Garden, Recycling, Waste")
        assertThat(mixed.bins).containsExactly("food", "garden", "recycling", "waste").inOrder()
        assertThat(mixed.start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 11, 16)))
        assertThat(mixed.end).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 11, 17)))
        assertThat(mixed.recurring).isFalse()
        assertThat(mixed.forPerson).isEqualTo("family")
        assertThat(result.upserts.map { it.remoteId }).containsExactly("bins-2026-11-09", "bins-2026-11-16", "bins-2026-11-23")
    }

    @Test
    fun onlyCollectionsInTheRangeAreReturned() = runTest {
        val result = subject.read(within = DateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 23), LONDON))

        assertThat(result.days()).containsExactly(LocalDate.of(2026, 11, 16))
    }

    @Test
    fun aStoredFailureIsThrownUntilAGoodRead() = runTest {
        subject.read()
        council.collections = "collections_missing.html"
        wall.at(today.plusDays(1), LocalTime.of(0, 5))

        assertThat(subject.unreadable().line).isEqualTo("Can’t read the bin dates.")
        val afterFailure = council.requests
        wall.plusMinutes(5)
        subject.unreadable()
        assertThat(provider().unreadable().line).isEqualTo("Can’t read the bin dates.")
        assertThat(council.requests).isEqualTo(afterFailure)

        council.collections = "collections.html"
        wall.plusMinutes(60)
        assertThat(subject.read().upserts).isNotEmpty()
    }

    @Test
    fun aGoodReadAfterAFailureClearsIt() = runTest {
        council.collections = "bad_date.html"
        subject.unreadable()

        council.collections = "collections.html"
        wall.plusMinutes(1)
        updates.request(wall.nowMillis())
        wall.plusMinutes(1)
        assertThat(subject.read().upserts).hasSize(3)
        wall.plusMinutes(5)
        assertThat(subject.read().upserts).hasSize(3)
        assertThat(provider().read().upserts).hasSize(3)
    }

    @Test
    fun retriesAnHourApartThreeTimes() = runTest {
        council.collections = "waste_hint.html"
        subject.unreadable()
        assertThat(council.requests).isEqualTo(3)

        wall.plusMinutes(30)
        subject.unreadable()
        assertThat(council.requests).isEqualTo(3)

        repeat(3) { retry ->
            wall.plusMinutes(30)
            subject.unreadable()
            assertWithMessage("retry ${retry + 1}").that(council.requests).isEqualTo(3 * (retry + 2))
            wall.plusMinutes(30)
            subject.unreadable()
        }
        assertThat(council.requests).isEqualTo(12)

        wall.plusMinutes(120)
        subject.unreadable()
        assertThat(council.requests).isEqualTo(12)

        wall.at(today.plusDays(1), LocalTime.of(0, 5))
        subject.unreadable()
        assertThat(council.requests).isEqualTo(15)
    }

    @Test
    fun updateNowReadsAtOnce() = runTest {
        subject.read()
        wall.plusMinutes(5)
        updates.request(wall.nowMillis())
        wall.plusMinutes(1)
        subject.read()
        assertThat(council.requests).isEqualTo(6)

        wall.plusMinutes(5)
        subject.read()
        assertThat(council.requests).isEqualTo(6)
    }

    @Test
    fun updateNowReadsAgainAfterTheDaysRetriesAreUsed() = runTest {
        council.collections = "collections_missing.html"
        repeat(4) {
            subject.unreadable()
            wall.plusMinutes(60)
        }
        subject.unreadable()
        assertThat(council.requests).isEqualTo(12)

        council.collections = "collections.html"
        updates.request(wall.nowMillis())
        wall.plusMinutes(1)
        assertThat(subject.read().upserts).hasSize(3)
    }

    @Test
    fun aRestartAnswersFromTheStoredRead() = runTest {
        subject.read()
        wall.plusMinutes(5)

        assertThat(provider().read().upserts).hasSize(3)
        assertThat(council.requests).isEqualTo(3)
    }

    @Test
    fun aGoodReadDropsACancelledCollection() = runTest {
        assertThat(subject.read().days()).contains(LocalDate.of(2026, 11, 16))

        council.collections = "collections_cancelled.html"
        wall.at(today.plusDays(1), LocalTime.of(0, 5))
        val next = subject.read()

        assertThat(next.fullReplace).isTrue()
        assertThat(next.days()).containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 23)).inOrder()
    }

    @Test
    fun anUnreachableReadPassesThroughAndWaitsAnHour() = runTest {
        council.status = 503
        assertThat(subject.failure()).isInstanceOf(UnreachableException::class.java)
        assertThat(council.requests).isEqualTo(1)

        council.status = null
        wall.plusMinutes(30)
        assertThat(subject.failure()).isInstanceOf(UnreachableException::class.java)
        assertThat(council.requests).isEqualTo(1)

        wall.plusMinutes(30)
        assertThat(subject.read().upserts).hasSize(3)
    }

    @Test
    fun aTimedOutReadWaitsAnHour() = runTest {
        council.slow = true
        // Real time, as the engine's timeout is: the test clock would jump past it while the store loads.
        assertThat(withContext(Dispatchers.Default) { withTimeoutOrNull(1_000) { subject.read() } }).isNull()
        council.slow = false
        val asked = council.requests

        wall.plusMinutes(5)
        assertThat(subject.failure()).isInstanceOf(UnreachableException::class.java)
        assertThat(council.requests).isEqualTo(asked)

        wall.plusMinutes(60)
        assertThat(subject.read().upserts).hasSize(3)
    }

    @Test
    fun anUnreachableReReadKeepsTheGoodRead() = runTest {
        subject.read()
        council.status = 503
        wall.at(today.plusDays(1), LocalTime.of(0, 5))

        assertThat(subject.failure()).isInstanceOf(UnreachableException::class.java)
        wall.plusMinutes(5)
        assertThat(subject.read().upserts).hasSize(3)
        assertThat(council.requests).isEqualTo(4)
    }

    @Test
    fun anUnreachableReadAfterAFailureStillReportsIt() = runTest {
        council.collections = "collections_missing.html"
        subject.unreadable()
        council.status = 503
        wall.plusMinutes(60)

        assertThat(subject.failure()).isInstanceOf(UnreachableException::class.java)
        wall.plusMinutes(5)
        subject.unreadable()
    }

    @Test
    fun aNewAddressReadsAgain() = runTest {
        subject.read()
        wall.plusMinutes(5)

        subject.read(binsConnection(house = "2 Example Road"))
        assertThat(council.requests).isEqualTo(6)
    }

    @Test
    fun twoPassesAtOnceReadOnce() = runTest {
        val first = async { subject.read() }
        val second = async { subject.read() }

        assertThat(first.await().upserts).isEqualTo(second.await().upserts)
        assertThat(council.requests).isEqualTo(3)
    }

    @Test
    fun theAddressIsNeverLogged() = runTest {
        subject.read()
        council.collections = "collections_missing.html"
        wall.plusMinutes(1)
        updates.request(wall.nowMillis())
        subject.unreadable()
        council.status = 503
        wall.plusMinutes(1)
        updates.request(wall.nowMillis())
        subject.failure()

        val lines = ShadowLog.getLogs().map { "${it.msg} ${it.throwable}" }
        assertThat(lines).contains("Bins read: 3 collections null")
        assertThat(lines).contains("Bins read failed: NO_COLLECTIONS null")
        lines.forEach { line ->
            assertWithMessage(line).that(line.lowercase()).doesNotContain("example")
            assertWithMessage(line).that(line.lowercase()).doesNotContain("zz99")
            assertWithMessage(line).that(line).doesNotContain("Track")
        }
    }

    @Test
    fun theSourceIsStaticAndAsksNothing() = runTest {
        assertThat(subject.sources(binsConnection())).containsExactly(SOURCE_BINS)
        assertThat(SOURCE_BINS.primary).isTrue()
        assertThat(SOURCE_BINS.writable).isFalse()
        assertThat(SOURCE_BINS.name).isEqualTo("Mid Sussex District Council")
        assertThat(council.requests).isEqualTo(0)
    }

    @Test
    fun theDescriptorIsReadOnly() {
        val d = subject.descriptor
        assertThat(d.id).isEqualTo("calendar.midsussex")
        assertThat(d.displayName).isEqualTo("Mid Sussex bins")
        assertThat(d.features).containsExactly(Feature.READ)
        assertThat(d.connectHint).isEqualTo("Enter the house name or number and the postcode, as on the council’s site.")
    }
}
