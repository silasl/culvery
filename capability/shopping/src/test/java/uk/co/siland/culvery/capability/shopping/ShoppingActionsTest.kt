package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.TOAST_WITH_ACTION_MS

/** 10 §4.6, §4.8, §4.11: add, tick and Undo, and what each refuses. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingActionsTest : ShoppingTestBase() {
    private val milk = ShownItem("g1", "Milk", ticked = false, queued = false)

    private suspend fun ShoppingRig.readTheList() {
        connect()
        store.saveRead(provider.items(conn), ShoppingHealth.OK, 0)
    }

    @Test
    fun twoAddsOfTheSameTextAtOnceQueueOne() = runTest {
        val rig = rig()
        rig.connect()
        val actions = rig.actions()
        val results = listOf(async { actions.add("Milk") }, async { actions.add("milk") }).awaitAll()
        assertThat(results.count { it == AddResult.Added }).isEqualTo(1)
        assertThat(results.filterIsInstance<AddResult.AlreadyThere>()).hasSize(1)
        assertThat(rig.store.queueNow()).hasSize(1)
    }

    @Test
    fun addingTextAlreadyOnTheListFlashesIt() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        var checks = 0
        val watching = rig.checks { checks++ }.also {
            it.start()
            it.watch()
        }
        runCurrent()
        assertThat(checks).isEqualTo(1)
        val actions = rig.actions(checks = watching)
        assertThat(actions.add(" MILK ")).isEqualTo(AddResult.AlreadyThere("g1"))
        assertThat(rig.store.queueNow()).isEmpty()
        advanceTimeBy(FLASH_MS - 1)
        assertThat(actions.flashing.value).isEqualTo("g1")
        advanceTimeBy(2)
        assertThat(actions.flashing.value).isNull()
        assertThat(checks).isEqualTo(2)
    }

    @Test
    fun addingATextAlreadyQueuedFlashesTheQueuedRow() = runTest {
        val rig = rig()
        rig.connect()
        val actions = rig.actions()
        actions.add("Milk")
        val key = "q:" + rig.store.queueNow().single().clientKey
        assertThat(actions.add("milk")).isEqualTo(AddResult.AlreadyThere(key))
    }

    @Test
    fun addingATickedItemAgainAdds() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(milk)
        assertThat(actions.add("Milk")).isEqualTo(AddResult.Added)
        assertThat(rig.store.queueNow().map { it.kind }).containsExactly("TICK", "ADD").inOrder()
    }

    @Test
    fun anAddIsLearnedWhenQueued() = runTest {
        val rig = rig()
        rig.connect()
        rig.actions().add("Milk")
        assertThat(rig.learning.learned.first().map { it.key }).containsExactly("milk")
    }

    @Test
    fun emptyTextAddsNothing() = runTest {
        val rig = rig()
        rig.connect()
        assertThat(rig.actions().add("   ")).isEqualTo(AddResult.Empty)
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun textIsCappedAtOneHundred() = runTest {
        val rig = rig()
        rig.connect()
        rig.actions().add("a".repeat(150))
        assertThat(rig.store.queueNow().single().text).isEqualTo("a".repeat(100))
    }

    @Test
    fun addsAreRateLimited() = runTest {
        val rig = rig()
        rig.connect()
        val actions = rig.actions()
        assertThat(actions.add("Milk")).isEqualTo(AddResult.Added)
        assertThat(actions.add("Bread")).isEqualTo(AddResult.Added)
        assertThat(actions.add("Eggs")).isEqualTo(AddResult.Added)
        advanceTimeBy(999)
        assertThat(actions.add("Butter")).isEqualTo(AddResult.TooFast)
        advanceTimeBy(2)
        assertThat(actions.add("Butter")).isEqualTo(AddResult.Added)
        // Nineteen more would make twenty in the minute; spread out so the per-second limit never bites.
        for (i in 5..20) {
            advanceTimeBy(1_001)
            assertThat(actions.add("Item $i")).isEqualTo(AddResult.Added)
        }
        advanceTimeBy(1_001)
        assertThat(actions.add("Item 21")).isEqualTo(AddResult.TooFast)
        advanceTimeBy(60_001 - currentTime)
        assertThat(actions.add("Item 22")).isEqualTo(AddResult.Added)
    }

    @Test
    fun aFullQueueRefusesWhileTheListIsGone() = runTest {
        val rig = rig(open = listOf("Bread"))
        rig.connect()
        repeat(QUEUE_MAX) { rig.enqueue(QueuedKind.ADD, "k$it", "Item $it") }
        rig.store.setHealth(ShoppingHealth.LIST_GONE)
        val actions = rig.actions()
        val view = ShoppingView(rig.store, actions, setOf(rig.provider), backgroundScope)
        runCurrent()
        assertThat(actions.add("Eggs")).isEqualTo(AddResult.Full)
        runCurrent()
        assertThat(view.state.value!!.problem).isEqualTo("Shopping can’t hold more until it’s fixed in Settings › Shopping.")

        rig.store.setHealth(ShoppingHealth.CANT_REACH)
        assertThat(actions.add("Eggs")).isEqualTo(AddResult.Added)
    }

    @Test
    fun aTickShowsTheUndoToast() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val toasts = ToastLog()
        rig.actions(toasts).tick(milk)
        assertThat(toasts.messages).containsExactly("Ticked Milk")
        assertThat(toasts.actions.single()!!.label).isEqualTo("Undo")
    }

    @Test
    fun undoingFromTheToastTakesTheTickBack() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val toasts = ToastLog()
        rig.actions(toasts).tick(milk)
        toasts.actions.single()!!.onClick()
        runCurrent()
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun aTickOfAQueuedAddWaitsForTheAdd() = runTest {
        val rig = rig()
        rig.connect()
        val actions = rig.actions()
        actions.add("Milk")
        val clientKey = rig.store.queueNow().single().clientKey
        actions.tick(ShownItem("q:$clientKey", "Milk", ticked = false, queued = true))
        val tick = rig.store.queueNow().last()
        assertThat(tick.kind).isEqualTo("TICK")
        assertThat(tick.clientKey).isEqualTo(clientKey)
        assertThat(tick.itemId).isNull()
        rig.sync.drain()
        assertThat(rig.provider.calls).containsExactly("items", "add", "tick g1").inOrder()
    }

    @Test
    fun undoBeforeTheTickIsSentDropsIt() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(milk)
        actions.undo(actions.undoable.value!!)
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(actions.undoable.value).isNull()
    }

    @Test
    fun undoAfterTheTickIsSentUnticks() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(milk)
        rig.sync.drain()
        assertThat(rig.openTexts()).isEmpty()
        actions.undo(actions.undoable.value!!)
        assertThat(rig.store.queueNow().map { it.kind }).containsExactly("UNTICK")
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
    }

    @Test
    fun undoWhileTheTickIsInFlightUnticks() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(milk)
        val gate = CompletableDeferred<Unit>()
        rig.provider.tickGate = gate
        val drain = async { rig.sync.drain() }
        runCurrent()
        val undo = async { actions.undo(actions.undoable.value!!) }
        runCurrent()
        gate.complete(Unit)
        drain.await()
        undo.await()
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun onlyTheLatestTickIsUndoable() = runTest {
        val rig = rig(open = listOf("Milk", "Bread"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(ShownItem("g1", "Milk", ticked = false, queued = false))
        actions.tick(ShownItem("g2", "Bread", ticked = false, queued = false))
        assertThat(actions.undoable.value!!.text).isEqualTo("Bread")
        val first = rig.store.queueNow().first()
        actions.undo(Ticked("g1", first.clientKey, "Milk", 0))
        assertThat(rig.store.queueNow()).hasSize(2)
        advanceTimeBy(TOAST_WITH_ACTION_MS - 1)
        assertThat(actions.undoable.value!!.text).isEqualTo("Bread")
        advanceTimeBy(2)
        assertThat(actions.undoable.value).isNull()
    }

    @Test
    fun undoOfATickOnAQueuedAddStillUntickstAfterBothAreSent() = runTest {
        val rig = rig()
        rig.connect()
        val actions = rig.actions()
        actions.add("Milk")
        actions.tick(ShownItem("q:" + rig.store.queueNow().single().clientKey, "Milk", ticked = false, queued = true))
        rig.sync.drain()
        assertThat(rig.openTexts()).isEmpty()
        actions.undo(actions.undoable.value!!)
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
        assertThat(rig.store.queueNow()).isEmpty()
    }

    @Test
    fun anAddAfterATickOfASentQueuedAddNeverTakesItsItem() = runTest {
        val rig = rig()
        rig.connect()
        val actions = rig.actions()
        actions.add("Milk")
        actions.tick(ShownItem("q:" + rig.store.queueNow().single().clientKey, "Milk", ticked = false, queued = true))
        rig.sync.drain()
        advanceTimeBy(1_001)
        assertThat(actions.add("Milk")).isEqualTo(AddResult.Added)
        assertThat(rig.store.queueNow().single().excludeIds).isEqualTo("g1")
    }

    @Test
    fun undoBeforeTheTickIsSentAlsoDropsTheReAdd() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(milk)
        actions.add("Milk")
        actions.undo(actions.undoable.value!!)
        assertThat(rig.store.queueNow()).isEmpty()
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
    }

    @Test
    fun undoAfterTheTickIsSentAlsoDropsTheReAdd() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(milk)
        rig.sync.drain()
        actions.add("Milk")
        actions.undo(actions.undoable.value!!)
        assertThat(rig.store.queueNow().map { it.kind }).containsExactly("UNTICK")
        rig.sync.drain()
        assertThat(rig.openTexts()).containsExactly("Milk")
    }

    @Test
    fun tickingARowTheListHasMovedPastDoesNothing() = runTest {
        val rig = rig(open = listOf("Milk"))
        rig.readTheList()
        val actions = rig.actions()
        actions.tick(ShownItem("g9", "Eggs", ticked = false, queued = false))
        assertThat(rig.store.queueNow()).isEmpty()
        assertThat(actions.undoable.value).isNull()
    }

}
