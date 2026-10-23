package uk.co.siland.culvery.capability.shopping

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.plugin.WallClock

class SampleShoppingProviderTest {
    private var now = Instant.parse("2026-10-23T10:00:00Z")
    private val clock = WallClock { now.toEpochMilli() }
    private val conn = Connection("c1", SAMPLE_DESCRIPTOR.id, SAMPLE_DESCRIPTOR.displayName, emptyMap())

    private suspend fun SampleShoppingProvider.texts() = items(conn).map { it.text }

    private suspend inline fun <reified E : Exception> thrown(block: () -> Unit): E {
        try {
            block()
        } catch (e: Exception) {
            if (e is E) return e
            throw e
        }
        throw AssertionError("Expected ${E::class.simpleName}")
    }

    @Test
    fun itStartsWithTheOpenItemsInOrderWithIdsFromG1() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk", "Bread"), ticked = listOf("Eggs"), clock = clock)
        assertThat(sample.items(conn).map { it.id to it.text }).containsExactly("g1" to "Milk", "g2" to "Bread").inOrder()
    }

    @Test
    fun anAddGoesFirst() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk"), clock = clock)
        val added = sample.add(conn, "Bread", "k1")
        assertThat(added.text).isEqualTo("Bread")
        assertThat(added.id).isEqualTo("g2")
        assertThat(sample.texts()).containsExactly("Bread", "Milk").inOrder()
    }

    @Test
    fun tickAndUntickMoveAnItemBetweenTheLists() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk", "Bread"), clock = clock)
        sample.tick(conn, "g1")
        assertThat(sample.texts()).containsExactly("Bread")
        assertThat(sample.history(conn, Instant.EPOCH).map { it.text }).containsExactly("Milk")
        sample.untick(conn, "g1")
        assertThat(sample.texts()).containsExactly("Milk", "Bread").inOrder()
        assertThat(sample.history(conn, Instant.EPOCH)).isEmpty()
    }

    @Test
    fun tickingWhatIsNotThereIsGone() = runTest {
        val sample = SampleShoppingProvider(clock = clock)
        thrown<ItemGoneException> { sample.tick(conn, "g7") }
    }

    @Test
    fun historyHoldsOnlyItemsTickedSinceWithTheirOwnDates() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Butter"), clock = clock)
        sample.tickedOn("Tea bags", Instant.parse("2026-10-01T09:00:00Z"))
        sample.tickedOn("Apples", Instant.parse("2026-10-20T09:00:00Z"))
        now = Instant.parse("2026-10-23T11:00:00Z")
        sample.tick(conn, "g1")

        val since = sample.history(conn, Instant.parse("2026-10-15T00:00:00Z"))
        assertThat(since.map { it.text to it.changedAt }).containsExactly(
            "Apples" to Instant.parse("2026-10-20T09:00:00Z"),
            "Butter" to Instant.parse("2026-10-23T11:00:00Z"),
        )
    }

    @Test
    fun itemsTickedAtStartAreDatedFromTheClock() = runTest {
        val sample = SampleShoppingProvider(ticked = listOf("Eggs"), clock = clock)
        assertThat(sample.history(conn, Instant.EPOCH).map { it.changedAt }).containsExactly(now)
    }

    @Test
    fun failAfterWriteAppliesThenThrows() = runTest {
        val sample = SampleShoppingProvider(clock = clock)
        sample.failAfterWrite = true
        thrown<UnreachableException> { sample.add(conn, "Milk", "k1") }
        assertThat(sample.texts()).containsExactly("Milk")
        assertThat(sample.failAfterWrite).isFalse()
    }

    @Test
    fun failNextFailsOneCallAndFailAllEveryCall() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk"), clock = clock)
        sample.failNext = ShoppingRejectedException()
        thrown<ShoppingRejectedException> { sample.items(conn) }
        assertThat(sample.texts()).containsExactly("Milk")

        sample.failAll = UnreachableException()
        thrown<UnreachableException> { sample.items(conn) }
        thrown<UnreachableException> { sample.items(conn) }
    }

    @Test
    fun aTickWaitsOnItsGate() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk"), clock = clock)
        val gate = CompletableDeferred<Unit>()
        sample.tickGate = gate
        val ticking = async { sample.tick(conn, "g1") }
        runCurrent()
        assertThat(sample.texts()).containsExactly("Milk")
        assertThat(ticking.isCompleted).isFalse()
        gate.complete(Unit)
        ticking.await()
        assertThat(sample.texts()).isEmpty()
    }

    @Test
    fun remakingTheListClearsFailAllAndStartsEmpty() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk"), ticked = listOf("Eggs"), clock = clock)
        sample.failAll = ListGoneException()
        val remade = sample.remakeList(conn)
        assertThat(remade.id).isEqualTo(conn.id)
        assertThat(remade.config).containsEntry("list", "new")
        assertThat(sample.failAll).isNull()
        assertThat(sample.texts()).isEmpty()
        assertThat(sample.history(conn, Instant.EPOCH)).isEmpty()
    }

    @Test
    fun aPhoneAddShowsOnTheNextRead() = runTest {
        val sample = SampleShoppingProvider(clock = clock)
        sample.addFromAPhone("Eggs")
        assertThat(sample.texts()).containsExactly("Eggs")
    }

    @Test
    fun callsAreRecordedInOrder() = runTest {
        val sample = SampleShoppingProvider(open = listOf("Milk"), clock = clock)
        sample.items(conn)
        sample.add(conn, "Bread", "k1")
        sample.tick(conn, "g1")
        sample.untick(conn, "g1")
        sample.history(conn, Instant.EPOCH)
        assertThat(sample.calls).containsExactly("items", "add", "tick g1", "untick g1", "history").inOrder()
    }

    @Test
    fun itOffersNoAccountToPickAndNamesItsFix() = runTest {
        val sample = SampleShoppingProvider(clock = clock)
        assertThat(sample.accounts.first()).isEqualTo(ProviderAccounts.Ready(emptyList()))
        assertThat(sample.accountGoneLine).isNotEmpty()
        assertThat(sample.descriptor).isEqualTo(SAMPLE_DESCRIPTOR)
        assertThat(SAMPLE_DESCRIPTOR.userConnectable).isFalse()
    }
}
