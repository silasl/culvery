package uk.co.siland.culvery.capability.shopping

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** 10 §4.4, §4.6: the last read with what is still queued laid over it. */
class ShoppingViewTest {
    private fun item(id: String, text: String) = ShoppingItem(id, text, Instant.EPOCH)

    private fun milkTicked(index: Int = 0) = Ticked("g1", "t1", "Milk", index)

    @Test
    fun queuedAddsShowFirstNewestFirst() {
        val rows = shown(
            listOf(item("g2", "Bread")),
            listOf(queued(1, QueuedKind.ADD, "k1", "Milk"), queued(2, QueuedKind.ADD, "k2", "Eggs")),
            null,
        )
        assertThat(rows.map { it.key }).containsExactly("q:k2", "q:k1", "g2").inOrder()
        assertThat(rows.map { it.queued }).containsExactly(true, true, false).inOrder()
    }

    @Test
    fun aReadItemMatchingAQueuedAddIsNotListedTwice() {
        val rows = shown(listOf(item("g5", "Milk")), listOf(queued(1, QueuedKind.ADD, "k1", "milk ")), null)
        assertThat(rows.map { it.key }).containsExactly("q:k1")
    }

    @Test
    fun aQueuedTickHidesItsRowUnlessUndoable() {
        val read = listOf(item("g1", "Milk"), item("g2", "Bread"))
        val tick = listOf(queued(1, QueuedKind.TICK, "t1", "Milk", itemId = "g1"))
        assertThat(shown(read, tick, null).map { it.text }).containsExactly("Bread")
        val rows = shown(read, tick, milkTicked())
        assertThat(rows.map { it.text }).containsExactly("Milk", "Bread").inOrder()
        assertThat(rows.map { it.ticked }).containsExactly(true, false).inOrder()
    }

    @Test
    fun aSentTickStaysUntilTheReadFollowsWhileUndoable() {
        val rows = shown(listOf(item("g1", "Milk")), emptyList(), milkTicked())
        assertThat(rows.single().ticked).isTrue()
    }

    @Test
    fun anUndoableRowStaysAtItsPlaceAfterTheRead() {
        val rows = shown(listOf(item("g2", "Bread")), emptyList(), milkTicked(index = 0))
        assertThat(rows.map { it.text }).containsExactly("Milk", "Bread").inOrder()
        assertThat(rows.map { it.ticked }).containsExactly(true, false).inOrder()
        val later = shown(listOf(item("g2", "Bread"), item("g3", "Eggs")), emptyList(), milkTicked(index = 1))
        assertThat(later.map { it.text }).containsExactly("Bread", "Milk", "Eggs").inOrder()
    }

    @Test
    fun aTickedQueuedAddIsHiddenUnlessUndoable() {
        val queue = listOf(queued(1, QueuedKind.ADD, "k1", "Milk"), queued(2, QueuedKind.TICK, "k1", "Milk"))
        assertThat(shown(emptyList(), queue, null)).isEmpty()
        val undoable = Ticked(null, "k1", "Milk", 0)
        assertThat(shown(emptyList(), queue, undoable).single().ticked).isTrue()
    }

    @Test
    fun anUntickWhoseItemIsNotInTheReadShowsAtTheTop() {
        val rows = shown(
            listOf(item("g2", "Bread")),
            listOf(queued(1, QueuedKind.UNTICK, "u1", "Milk", itemId = "g1")),
            null,
        )
        assertThat(rows.map { it.key }).containsExactly("g1", "g2").inOrder()
        assertThat(shown(listOf(item("g1", "Milk")), listOf(queued(1, QueuedKind.UNTICK, "u1", "Milk", itemId = "g1")), null))
            .hasSize(1)
    }

    @Test
    fun anUntickIsHiddenWhileATickFollowsItAndTickedWhileUndoable() {
        val queue = listOf(
            queued(1, QueuedKind.UNTICK, "u1", "Milk", itemId = "g1"),
            queued(2, QueuedKind.TICK, "t1", "Milk", itemId = "g1"),
        )
        assertThat(shown(emptyList(), queue, null)).isEmpty()
        val untick = listOf(queued(1, QueuedKind.UNTICK, "u1", "Milk", itemId = "g1"))
        assertThat(shown(emptyList(), untick, milkTicked()).single().ticked).isTrue()
    }

    @Test
    fun aPhonesDuplicateShowsAsTwoRows() {
        val rows = shown(listOf(item("g1", "Milk"), item("g5", "Milk")), emptyList(), null)
        assertThat(rows.map { it.key }).containsExactly("g1", "g5").inOrder()
    }

    @Test
    fun theCountLeavesOutTickedRows() {
        val rows = shown(listOf(item("g1", "Milk"), item("g2", "Bread")), emptyList(), milkTicked())
        assertThat(rows.count { !it.ticked }).isEqualTo(1)
    }

    @Test
    fun noTextInAToString() {
        assertThat(ShownItem("g1", "Bin bags", ticked = false, queued = false).toString()).doesNotContain("Bin bags")
        assertThat(Ticked("g1", "t1", "Bin bags", 0).toString()).doesNotContain("Bin bags")
    }
}
