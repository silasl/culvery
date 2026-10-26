package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OfflineException
import uk.co.siland.culvery.core.plugin.TOAST_WITH_ACTION_MS
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
        // Production connecting takes ShoppingLock, so it waits for the row in flight; this goes round it to test the stop.
        rig.store.connect(Connection("c2", rig.conn.providerId, rig.conn.label, emptyMap()))
        // Connecting clears the queue, so a row made for the new connection is what the old pass must leave alone.
        rig.enqueue(QueuedKind.TICK, "t3", "Eggs", itemId = "g3")
        gate.complete(Unit)
        pass.await()
        assertThat(rig.provider.calls).containsExactly("tick g1")
        assertThat(rig.store.queueNow().map { it.itemId }).containsExactly("g3")
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

    /**
     * Another pass ticks Milk after this pass read the list; without one pass at a time, the add queued later matches
     * the stale Milk and is lost. The test holds the lock between rows to line the passes up.
     */
    @Test
    fun twoPassesAtOnceDoNotLoseAnAdd() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.connect()
        rig.enqueue(QueuedKind.ADD, "kb", "Bread")
        rig.enqueue(QueuedKind.TICK, "t1", "Milk", itemId = "g1")
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val holdFirst = async { rig.lock.withLock { first.await() } }
        runCurrent()
        val a = async { rig.sync.drain() }
        runCurrent()
        val b = async { rig.sync.drain() }
        runCurrent()
        val holdSecond = async { rig.lock.withLock { second.await() } }
        runCurrent()
        first.complete(Unit)
        runCurrent()
        // Every row that could be sent before the second hold has been; the late add arrives now.
        rig.enqueue(QueuedKind.ADD, "km", "Milk")
        second.complete(Unit)
        listOf(holdFirst, a, b, holdSecond).forEach { it.await() }
        assertThat(rig.openTexts()).containsExactly("Milk", "Bread")
        assertThat(rig.store.queueNow()).isEmpty()
    }

    /** The same lining up, with the add landing but failing: a stale pass would not see it and add a second Milk. */
    @Test
    fun twoPassesAtOnceDoNotDoubleAnAdd() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.failAfterWrite = true
        // The first row is dropped (the item is gone) without a write, so the cut-off write is the add's.
        rig.enqueue(QueuedKind.TICK, "t1", "Eggs", itemId = "g9")
        rig.enqueue(QueuedKind.ADD, "km", "Milk")
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val holdFirst = async { rig.lock.withLock { first.await() } }
        runCurrent()
        val a = async { rig.sync.drain() }
        runCurrent()
        val b = async { rig.sync.drain() }
        runCurrent()
        val holdSecond = async { rig.lock.withLock { second.await() } }
        runCurrent()
        first.complete(Unit)
        runCurrent()
        advanceTimeBy(30_000)
        second.complete(Unit)
        listOf(holdFirst, a, b, holdSecond).forEach { it.await() }
        advanceTimeBy(60_000)
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
    }

    @Test
    fun aTickWaitingForItsAddBehindItBacksOffInsteadOfSpinning() = runTest {
        val rig = rig()
        rig.connect()
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        assertThat(rig.sync.drain()).isEqualTo(30_000)
        assertThat(rig.provider.calls).isEmpty()
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

    /** A check's read can take a while when the internet is half up; the wall's taps must not wait for it. */
    @Test
    fun tapsDoNotWaitForACheckToRead() = runTest {
        val rig = rig(open = listOf("Bread", "Eggs"))
        rig.connect()
        rig.sync.check()
        val actions = rig.actions()
        advanceTimeBy(1_000)
        val gate = CompletableDeferred<Unit>()
        rig.provider.itemsGate = gate
        val check = async { rig.sync.check() }
        runCurrent()
        rig.provider.itemsGate = null

        val add = async { actions.add("Milk") }
        val tick = async { actions.tick(ShownItem("g2", "Eggs", ticked = false, queued = false)) }
        runCurrent()
        assertThat(add.isCompleted).isTrue()
        assertThat(tick.isCompleted).isTrue()
        val undo = async { actions.undo(actions.undoable.value!!) }
        runCurrent()
        assertThat(undo.isCompleted).isTrue()
        assertThat(check.isActive).isTrue()

        // Milk is sent while the read is out, so that read is not kept: it would take Milk off the wall.
        rig.sync.drain()
        gate.complete(Unit)
        check.await()
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Milk", "Bread", "Eggs").inOrder()
        assertThat(rig.store.connectionNow()!!.lastReadMillis).isEqualTo(0)

        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.store.items.first().map { it.text }).containsExactly("Milk", "Bread", "Eggs").inOrder()
        assertThat(rig.store.connectionNow()!!.lastReadMillis).isEqualTo(currentTime)
    }

    @Test
    fun aReadOfAListStoppedAndConnectedAgainMeanwhileIsNotKept() = runTest {
        val rig = rig(open = listOf("Bread"))
        rig.connect()
        val gate = CompletableDeferred<Unit>()
        rig.provider.itemsGate = gate
        val check = async { rig.sync.check() }
        runCurrent()
        rig.lock.withLock {
            rig.store.connect(rig.conn)
            rig.sync.connectionChanged()
        }
        gate.complete(Unit)
        check.await()
        assertThat(rig.store.connectionNow()!!.lastReadMillis).isNull()
        assertThat(rig.store.connectionNow()!!.seeded).isFalse()
    }

    @Test
    fun aFailedFirstReadLeavesTheSeedingToTheNextCheck() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.tickedOn("Milk", Instant.ofEpochMilli(currentTime))
        rig.provider.failNext = UnreachableException()
        rig.sync.check()
        assertThat(rig.store.connectionNow()!!.seeded).isFalse()
        assertThat(rig.store.learned.first()).isEmpty()
        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.store.connectionNow()!!.seeded).isTrue()
        assertThat(rig.store.learned.first().associate { it.key to it.count }).containsExactly("milk", 1)
    }

    @Test
    fun anAddHeldByAnOutageGoesAtTheNextGoodCheck() = runTest {
        val rig = rig()
        rig.connect()
        rig.sync.check()
        rig.provider.failAll = UnreachableException()
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        repeat(3) { advanceTimeBy(rig.sync.drain()!! - currentTime) }
        rig.sync.drain()
        assertThat(rig.store.queueNow().single().nextAtMillis - currentTime).isEqualTo(300_000)
        rig.provider.failAll = null
        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(rig.openTexts()).containsExactly("Milk")
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.OK)
    }

    @Test
    fun aRetriedAddMatchedInTheTickedItemsIsNotShownAsOpen() = runTest {
        val rig = rig()
        rig.connect()
        rig.sync.check()
        rig.provider.failAfterWrite = true
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.sync.drain()
        rig.provider.tick(rig.conn, "g1")
        advanceTimeBy(30_000)
        rig.sync.drain()
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(rig.store.itemsNow()).isEmpty()
        assertThat(rig.db.shoppingDao().allSeen().map { it.id }).contains("g1")
    }

    @Test
    fun aRefusedAddSaysSoUntilARetryGoesThrough() = runTest {
        val rig = rig(fileName = "refused.db", open = listOf("Bread"))
        rig.connect()
        rig.provider.refusing = "Milk"
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.ADD, "k2", "Eggs")
        val view = ShoppingView(rig.store, rig.actions(), setOf(rig.provider), backgroundScope)
        rig.sync.check()
        runCurrent()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.REFUSED)
        assertThat(view.state.value!!.problem).isEqualTo("the sample list refused one item. Tick it off on the list to send the rest.")

        // A good read doesn't clear it while the refused add is queued, nor does a restart.
        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.REFUSED)
        rig.reopen()
        advanceTimeBy(60_000)
        rig.sync.check()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.REFUSED)
        assertThat(rig.store.queueNow().map { it.text }).containsExactly("Milk", "Eggs").inOrder()

        // The refusal may have been passing: it is tried again on the five-minute step.
        rig.provider.refusing = null
        advanceTimeBy(300_000)
        rig.sync.check()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.OK)
        assertThat(rig.openTexts()).containsExactly("Eggs", "Milk", "Bread").inOrder()
    }

    @Test
    fun tickingTheRefusedItemOffSendsTheRest() = runTest {
        val rig = rig(open = listOf("Bread"))
        rig.connect()
        rig.provider.refusing = "Milk"
        val actions = rig.actions()
        actions.add("Milk")
        actions.add("Eggs")
        rig.sync.check()
        val milk = shown(rig.store.itemsNow(), rig.store.queueNow(), null).first { it.text == "Milk" }
        actions.tick(milk)
        // The tick waits out its Undo before both go.
        rig.sync.drain()
        assertThat(rig.store.queueNow().map { it.kind to it.text }).containsExactly("ADD" to "Milk", "ADD" to "Eggs", "TICK" to "Milk").inOrder()
        advanceTimeBy(TOAST_WITH_ACTION_MS)
        rig.sync.drain()
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.OK)
        assertThat(rig.openTexts()).containsExactly("Eggs", "Bread").inOrder()
        assertThat(rig.provider.calls.count { it == "add" }).isEqualTo(2)
    }

    @Test
    fun undoingTheTickOfTheRefusedItemBeforeItIsDroppedKeepsItWaiting() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.refusing = "Milk"
        val actions = rig.actions()
        actions.add("Milk")
        actions.add("Eggs")
        rig.sync.check()
        actions.tick(shown(rig.store.itemsNow(), rig.store.queueNow(), null).first { it.text == "Milk" })
        rig.sync.drain()
        actions.undo(actions.undoable.value!!)
        assertThat(rig.store.queueNow().map { it.kind to it.text }).containsExactly("ADD" to "Milk", "ADD" to "Eggs").inOrder()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.REFUSED)
    }

    @Test
    fun anUndoThatTakesBackTheRefusedAddClearsTheRefusal() = runTest {
        val rig = rig(open = listOf("Bread"))
        rig.connect()
        rig.sync.check()
        val actions = rig.actions()
        actions.tick(shown(rig.store.itemsNow(), rig.store.queueNow(), null).first { it.text == "Bread" })
        rig.provider.refusing = "Bread"
        actions.add("Bread")
        rig.sync.drain()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.REFUSED)
        // Putting the tick back drops the re-add, so nothing refused is left waiting.
        actions.undo(actions.undoable.value!!)
        assertThat(rig.store.queueNow().map { it.kind to it.text }).containsExactly("UNTICK" to "Bread")
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.OK)
    }

    @Test
    fun aRefusedAddTickedBeforeItWasSentIsDroppedWithItsTick() = runTest {
        val rig = rig()
        rig.connect()
        rig.provider.refusing = "Milk"
        rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        rig.enqueue(QueuedKind.TICK, "k1", "Milk")
        rig.enqueue(QueuedKind.ADD, "k2", "Eggs")
        advanceTimeBy(TOAST_WITH_ACTION_MS)
        assertThat(rig.sync.drain()).isNull()
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.OK)
        assertThat(rig.openTexts()).containsExactly("Eggs")
    }
}
