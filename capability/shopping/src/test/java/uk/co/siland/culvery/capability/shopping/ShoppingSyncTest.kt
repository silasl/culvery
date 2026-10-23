package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OfflineException
import uk.co.siland.culvery.core.plugin.UnreachableException

/** 10 §4.4, §4.5: the queue is sent in order, an add never lands twice or goes missing, and the read follows the writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingSyncTest : ShoppingTestBase() {
    private val day = 24 * 60 * 60_000L

    @Test
    fun aDrainSendsInOrder() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.ADD, "k2", "Bread")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        assertThat(rig.sync.drain()).isNull()
        assertThat(rig.provider.calls).containsExactly("items", "add", "add", "tick g1").inOrder()
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun aRetriedAddThatAlreadyLandedAddsNothing() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAfterWrite = true
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        assertThat(rig.sync.drain()).isEqualTo(30_000)
        advanceTimeBy(30_000)
        assertThat(rig.sync.drain()).isNull()
        assertThat(rig.provider.calls.count { it == "add" }).isEqualTo(1)
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(rig.openTexts()).containsExactly("Milk")
    }

    @Test
    fun aRetriedAddAPhoneTickedAddsNothing() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAfterWrite = true
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.sync.drain()
        rig.provider.tick(rig.conn, "g1")
        advanceTimeBy(30_000)
        rig.sync.drain()
        assertThat(rig.provider.calls.count { it == "add" }).isEqualTo(1)
        assertThat(rig.store.queueNow()).isEmpty()
        // The add was sent with the ticked item's id, so the tick that followed it named g1.
        assertThat(rig.provider.calls.count { it == "tick g1" }).isEqualTo(2)
        assertThat(rig.openTexts()).isEmpty()
    }

    @Test
    fun aRetriedAddIsLearnedOnce() = runTest {
        val rig = rig()
        rig.connect()
        rig.learning.added("Milk")
        rig.provider.failAfterWrite = true
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.sync.drain()
        advanceTimeBy(30_000)
        rig.sync.drain()
        advanceTimeBy(2 * day)
        rig.sync.check()
        assertThat(rig.store.learned.first().single().count).isEqualTo(1)
    }

    @Test
    fun aTickThenAReAddInOnePassAddsAgain() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.connect()
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g1")
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.sync.drain()
        assertThat(rig.provider.calls).containsExactly("items", "tick g1", "add").inOrder()
        assertThat(rig.provider.items(rig.conn).map { it.id }).containsExactly("g2")
    }

    @Test
    fun aBreadAddIsMatchedWhileMilkIsTickedAndAddedAgain() = runTest {
        val rig = rig(open = listOf("Bread", "Milk"))
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "k1", "Bread")
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g2")
        rig.enqueue(QueuedKind.ADD, "k2", "Milk")
        rig.sync.drain()
        assertThat(rig.provider.calls).containsExactly("items", "tick g2", "add").inOrder()
        assertThat(rig.provider.items(rig.conn).map { it.text to it.id }).containsExactly("Milk" to "g3", "Bread" to "g1")
    }

    /** The add is queued after the tick, so it must not take the item the tick is putting off the list. */
    @Test
    fun anAddQueuedAfterATickNeverTakesTheTickedItem() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.connect()
        rig.store.saveRead(rig.provider.items(rig.conn), ShoppingHealth.OK, 0)
        val actions = rig.actions()
        actions.tick(ShownItem("g1", "Milk", ticked = false, queued = false))
        assertThat(actions.add("Milk")).isEqualTo(AddResult.Added)
        assertThat(rig.store.queueNow().last().excludeIds).isEqualTo("g1")
        val gate = CompletableDeferred<Unit>()
        rig.provider.tickGate = gate
        val pass = async { rig.sync.drain() }
        runCurrent()
        rig.provider.failNext = UnreachableException()
        gate.complete(Unit)
        pass.await()
        advanceTimeBy(30_000)
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
        assertThat(rig.provider.items(rig.conn).single().id).isEqualTo("g2")
    }

    /** The tick of a queued add is only known once the add is sent; a later add of the same text must still not take it. */
    @Test
    fun anAddBehindATickOfAQueuedAddNeverTakesTheTickedItem() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.enqueue(QueuedKind.ADD, "k2", "Milk")
        val gate = CompletableDeferred<Unit>()
        rig.provider.tickGate = gate
        val pass = async { rig.sync.drain() }
        runCurrent()
        rig.provider.failNext = UnreachableException()
        gate.complete(Unit)
        pass.await()
        assertThat(rig.store.queueNow().single().excludeIds).isEqualTo("g1")
        advanceTimeBy(30_000)
        rig.sync.drain()
        assertThat(rig.provider.items(rig.conn).map { it.id }).containsExactly("g2")
    }

    @Test
    fun aReadItemMatchingAQueuedAddIsNotLearnedYet() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.connect()
        rig.learning.added("Milk")
        val seq = rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.store.failed(seq, 60_000)
        rig.sync.check()
        assertThat(rig.store.learned.first().single().count).isEqualTo(1)
        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.provider.calls.count { it == "add" }).isEqualTo(0)
        assertThat(rig.store.queueNow()).isEmpty()
        advanceTimeBy(2 * day)
        rig.sync.check()
        assertThat(rig.store.learned.first().single().count).isEqualTo(1)
    }

    @Test
    fun anOfflineAddAndTickSurviveARestartAndDrainInOrder() = runTest {
        val rig = rig(fileName = "restart.db")
        rig.connect()
        rig.provider.failAll = OfflineException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.sync.drain()
        val queue = rig.store.queueNow()
        assertThat(queue.map { it.kind }).containsExactly("ADD", "TICK").inOrder()
        assertThat(queue.first().attempts).isEqualTo(1)
        assertThat(queue.first().nextAtMillis).isEqualTo(currentTime + 30_000)

        rig.reopen()
        rig.provider.failAll = null
        val before = rig.provider.calls.size
        advanceTimeBy(30_000)
        rig.sync.drain()
        assertThat(rig.provider.calls.drop(before)).containsExactly("items", "history", "add", "tick g1").inOrder()
        assertThat(rig.openTexts()).isEmpty()
        assertThat(rig.provider.history(rig.conn, Instant.EPOCH).map { it.text }).containsExactly("Milk")
    }

    @Test
    fun theBackoffStepsAndStaysAtFiveMinutes() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAll = UnreachableException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        val waits = (1..5).map {
            val start = currentTime
            val next = rig.sync.drain()!!
            advanceTimeBy(next - start)
            next - start
        }
        assertThat(waits).containsExactly(30_000L, 60_000L, 120_000L, 300_000L, 300_000L).inOrder()
    }

    @Test
    fun aHeadThatIsNotDueIsNotTried() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAll = UnreachableException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.sync.drain()
        val calls = rig.provider.calls.size
        advanceTimeBy(29_999)
        assertThat(rig.sync.drain()).isEqualTo(30_000)
        assertThat(rig.provider.calls).hasSize(calls)
    }

    @Test
    fun anAddIsNeverDropped() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAll = ShoppingRejectedException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        repeat(50) {
            rig.sync.drain()
            advanceTimeBy(3 * day / 50)
        }
        val row = rig.store.queueNow().single()
        assertThat(row.kind).isEqualTo("ADD")
        assertThat(row.attempts).isEqualTo(50)
    }

    @Test
    fun aTickWhoseItemHasGoneIsDropped() = runTest {
        val rig = rig(open = listOf("Milk", "Bread"))
        rig.connect()
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g1")
        rig.enqueue(QueuedKind.TICK, "t2", "Bread", itemId = "g2")
        rig.provider.failNext = ItemGoneException()
        assertThat(rig.sync.drain()).isNull()
        assertThat(rig.provider.calls).containsExactly("tick g1", "tick g2").inOrder()
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun aDeletedListWaitsWithTheQueueKept() = runTest {
        val rig = rig()
        rig.connect()
        rig.store.saveRead(listOf(ShoppingItem("g9", "Eggs", Instant.EPOCH)), ShoppingHealth.OK, 5_000)
        rig.provider.failAll = ListGoneException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.sync.drain()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.LIST_GONE)
        assertThat(rig.store.queueNow().map { it.kind }).containsExactly("ADD", "TICK").inOrder()
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Eggs")
        assertThat(rig.store.connectionNow()!!.lastReadMillis).isEqualTo(5_000)
    }

    /** The items went with the list, so a tick for one has nothing left to do. */
    @Test
    fun aTickOnADeletedListIsDropped() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.connect()
        rig.provider.failAll = ListGoneException()
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g1")
        rig.sync.drain()
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.LIST_GONE)
    }

    @Test
    fun needsPermissionAndAccountGoneKeepTheQueue() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.provider.failAll = NeedsSignInException()
        rig.sync.drain()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.NEEDS_PERMISSION)
        assertThat(rig.store.queueNow()).hasSize(1)
        advanceTimeBy(30_000)
        rig.provider.failAll = AccountGoneException()
        rig.sync.drain()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.ACCOUNT_GONE)
        assertThat(rig.store.queueNow()).hasSize(1)
        advanceTimeBy(60_000)
        rig.provider.failAll = null
        rig.sync.drain()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.OK)
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun aCheckDrainsBeforeItReadsWithoutDeadlock() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.sync.check()
        val calls = rig.provider.calls
        assertThat(calls.indexOf("add")).isLessThan(calls.lastIndexOf("items"))
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Milk")
        assertThat(rig.store.connectionNow()!!.lastReadMillis).isEqualTo(currentTime)
    }

    @Test
    fun aFailedCheckKeepsTheLastRead() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.connect()
        rig.sync.check()
        rig.provider.failAll = UnreachableException()
        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.CANT_REACH)
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Milk")
        assertThat(rig.store.connectionNow()!!.lastReadMillis).isEqualTo(0)
    }

    @Test
    fun aSentChangeIsInTheReadAtOnce() = runTest {
        val rig = rig(open = listOf("Milk", "Bread"))
        rig.connect()
        rig.sync.check()
        rig.enqueue(QueuedKind.ADD, "k1", "Eggs")
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g1")
        rig.sync.drain()
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Eggs", "Bread").inOrder()
        rig.enqueue(QueuedKind.UNTICK, "u1", "Milk", itemId = "g1")
        rig.sync.drain()
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Milk", "Eggs", "Bread").inOrder()
    }

    @Test
    fun aPassStopsWhenTheConnectionChanges() = runTest {
        val rig = rig(open = listOf("Milk", "Bread"))
        rig.connect()
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g1")
        rig.enqueue(QueuedKind.TICK, "t2", "Bread", itemId = "g2")
        val gate = CompletableDeferred<Unit>()
        rig.provider.tickGate = gate
        val pass = async { rig.sync.drain() }
        runCurrent()
        rig.store.connect(Connection("c2", rig.conn.providerId, rig.conn.label, emptyMap()))
        gate.complete(Unit)
        pass.await()
        assertThat(rig.provider.calls).containsExactly("tick g1")
        assertThat(rig.store.queueNow().map { it.itemId }).containsExactly("g2")
    }

    @Test
    fun theFirstCheckSeedsFromAYearOfHistory() = runTest {
        val rig = rig()
        rig.connect()
        advanceTimeBy(800 * day)
        val now = currentTime
        rig.provider.tickedOn("Milk", Instant.ofEpochMilli(now - 1 * day))
        rig.provider.tickedOn("Milk", Instant.ofEpochMilli(now - 2 * day))
        rig.provider.tickedOn("Milk", Instant.ofEpochMilli(now - 3 * day))
        rig.provider.tickedOn("Bread", Instant.ofEpochMilli(now - 10 * day))
        rig.provider.tickedOn("Tea bags", Instant.ofEpochMilli(now - 370 * day))
        rig.provider.tickedOn("Apples", Instant.ofEpochMilli(now - 360 * day))
        rig.sync.check()
        val learned = rig.store.learned.first().associate { it.key to it.count }
        assertThat(learned).containsExactly("milk", 3, "bread", 1, "apples", 1)
        assertThat(rig.store.connectionNow()!!.seeded).isTrue()

        // The next check asks from 10 minutes before the last one, not from a year back.
        advanceTimeBy(60_000)
        val lastRead = rig.store.connectionNow()!!.lastHistoryMillis!!
        rig.provider.tickedOn("Eggs", Instant.ofEpochMilli(lastRead - 11 * 60_000))
        rig.provider.tickedOn("Butter", Instant.ofEpochMilli(lastRead - 9 * 60_000))
        rig.sync.check()
        assertThat(rig.store.learned.first().map { it.key }).containsAtLeast("butter", "milk")
        assertThat(rig.store.learned.first().map { it.key }).doesNotContain("eggs")
    }

    @Test
    fun aTickQueuedBeforeItsAddWaits() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAll = UnreachableException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.sync.drain()
        assertThat(rig.provider.calls.none { it.startsWith("tick") }).isTrue()
        assertThat(rig.store.queueNow()).hasSize(2)
    }

    @Test
    fun aTickWhoseAddIsGoneIsDropped() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.enqueue(QueuedKind.ADD, "k2", "Bread")
        rig.sync.drain()
        assertThat(rig.provider.calls).containsExactly("items", "add").inOrder()
        assertThat(rig.store.queueNow()).isEmpty()
    }
}
