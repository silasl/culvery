package uk.co.siland.culvery.capability.shopping

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.shopping.db.ItemEntity
import uk.co.siland.culvery.capability.shopping.db.LearnedEntity
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.capability.shopping.db.SeenEntity
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.plugin.Connection

/** 10 §4.5: the connection, the last read, the queue, and what is learned. */
@RunWith(AndroidJUnit4::class)
class ShoppingStoreTest {
    private lateinit var db: ShoppingDatabase
    private lateinit var store: ShoppingStore
    private val c1 = Connection("c1", "shopping.sample", "the sample list", mapOf("list" to "a"))
    private val at = Instant.ofEpochMilli(1_000)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShoppingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ShoppingStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun noConnectionToStartWith() = runTest {
        assertThat(store.connectionNow()).isNull()
        assertThat(store.connection.first()).isNull()
    }

    @Test
    fun aConnectionIsKeptAndReplaced() = runTest {
        store.connect(c1)
        assertThat(store.connectionNow()).isEqualTo(StoredShopping(c1, ShoppingHealth.OK, null, null))
        val c2 = Connection("c2", "shopping.sample", "the sample list", mapOf("list" to "b"))
        store.connect(c2)
        assertThat(store.connectionNow()?.connection).isEqualTo(c2)

        store.saveRead(emptyList(), ShoppingHealth.OK, 5_000)
        store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 6_000)
        val c2Again = c2.copy(config = mapOf("list" to "c"))
        store.replaceConnection(c2Again)
        val kept = store.connectionNow()!!
        assertThat(kept.connection).isEqualTo(c2Again)
        assertThat(kept.lastReadMillis).isEqualTo(5_000)
        assertThat(store.queueNow()).hasSize(1)
    }

    @Test
    fun connectingStartsAgainFromHealthyAndUnseeded() = runTest {
        store.connect(c1)
        store.saveRead(emptyList(), ShoppingHealth.CANT_REACH, 5_000)
        store.setHistoryRead(5_000)
        store.connect(c1)
        val stored = store.connectionNow()!!
        assertThat(stored.health).isEqualTo(ShoppingHealth.OK)
        assertThat(stored.seeded).isFalse()
    }

    /** Off means an empty queue, so neither the old read nor a leftover queue reaches a new connection. */
    @Test
    fun connectingClearsTheQueueAndTheItems() = runTest {
        store.connect(c1)
        store.saveRead(listOf(ShoppingItem("g1", "Milk", at)), ShoppingHealth.OK, 2_000)
        store.enqueue(QueuedKind.ADD, "k1", null, "Bread", 3_000)
        store.connect(Connection("c2", "shopping.sample", "the sample list", emptyMap()))
        assertThat(store.queueNow()).isEmpty()
        assertThat(store.items.first()).isEmpty()
    }

    @Test
    fun replacingAConnectionThatIsNotStoredFails() = runTest {
        store.connect(c1)
        val failure = runCatching { store.replaceConnection(c1.copy(id = "c9")) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun readItemsKeepTheirDates() = runTest {
        store.connect(c1)
        store.saveRead(listOf(ShoppingItem("g1", "Milk", Instant.ofEpochMilli(4_321))), ShoppingHealth.OK, 5_000)
        assertThat(store.items.first().single().changedAt).isEqualTo(Instant.ofEpochMilli(4_321))
    }

    @Test
    fun healthAndTheHistoryReadAreKept() = runTest {
        store.connect(c1)
        store.setHealth(ShoppingHealth.LIST_GONE)
        store.setHistoryRead(7_000)
        assertThat(store.connectionNow()).isEqualTo(StoredShopping(c1, ShoppingHealth.LIST_GONE, null, 7_000))
    }

    @Test
    fun aReadReplacesTheItemsInOrder() = runTest {
        store.connect(c1)
        store.saveRead(listOf(ShoppingItem("g1", "Milk", at), ShoppingItem("g2", "Bread", at)), ShoppingHealth.OK, 2_000)
        assertThat(store.items.first().map { it.text }).containsExactly("Milk", "Bread").inOrder()
        store.saveRead(listOf(ShoppingItem("g3", "Eggs", at), ShoppingItem("g1", "Milk", at)), ShoppingHealth.OK, 3_000)
        assertThat(store.items.first().map { it.text }).containsExactly("Eggs", "Milk").inOrder()
        assertThat(store.connectionNow()?.lastReadMillis).isEqualTo(3_000)
    }

    @Test
    fun theQueueKeepsItsOrderAndAnAddsIdReachesLaterRows() = runTest {
        store.connect(c1)
        val first = store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 1_000)
        store.enqueue(QueuedKind.TICK, "k1", null, "Milk", 2_000)
        store.enqueue(QueuedKind.ADD, "k2", null, "Bread", 3_000)
        store.sent(first, "g9")
        val queue = store.queueNow()
        assertThat(queue.map { it.kind to it.itemId }).containsExactly("TICK" to "g9", "ADD" to null).inOrder()
        assertThat(queue.map { it.clientKey }).containsExactly("k1", "k2").inOrder()
        assertThat(store.queue.first()).isEqualTo(queue)
    }

    @Test
    fun aSentRowWithNoIdLeavesTheOthersAlone() = runTest {
        val tick = store.enqueue(QueuedKind.TICK, "k1", "g1", "Milk", 1_000)
        store.enqueue(QueuedKind.UNTICK, "k1", null, "Milk", 2_000)
        store.sent(tick, null)
        assertThat(store.queueNow().map { it.itemId }).containsExactly(null)
    }

    @Test
    fun anAddCanCarryTheIdsItMustNeverMatch() = runTest {
        store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 1_000, excludeIds = "g1,g2")
        store.enqueue(QueuedKind.ADD, "k2", null, "Bread", 2_000)
        assertThat(store.queueNow().map { it.excludeIds }).containsExactly("g1,g2", null).inOrder()
    }

    @Test
    fun aFailureCountsAndWaits() = runTest {
        val seq = store.enqueue(QueuedKind.ADD, "k1", null, "Milk", 1_000)
        assertThat(store.queueNow().single().attempts).isEqualTo(0)
        store.failed(seq, 90_000)
        val row = store.queueNow().single()
        assertThat(row.attempts).isEqualTo(1)
        assertThat(row.nextAtMillis).isEqualTo(90_000)
        store.failed(seq, 150_000)
        assertThat(store.queueNow().single().attempts).isEqualTo(2)
    }

    @Test
    fun aDroppedRowIsGone() = runTest {
        val seq = store.enqueue(QueuedKind.TICK, "k1", "g1", "Milk", 1_000)
        store.drop(seq)
        assertThat(store.queueNow()).isEmpty()
    }

    @Test
    fun stopKeepsWhatWasLearned() = runTest {
        store.connect(c1)
        store.saveRead(listOf(ShoppingItem("g1", "Milk", at)), ShoppingHealth.OK, 2_000)
        store.enqueue(QueuedKind.ADD, "k1", null, "Bread", 3_000)
        db.shoppingDao().upsertLearned(LearnedEntity("milk", "Milk", 3, 1_000, 1_000))
        db.shoppingDao().upsertSeen(SeenEntity("g1", 1_000))

        store.stop()

        assertThat(store.connectionNow()).isNull()
        assertThat(store.items.first()).isEmpty()
        assertThat(store.queueNow()).isEmpty()
        assertThat(db.shoppingDao().allLearned()).hasSize(1)
        assertThat(db.shoppingDao().allSeen()).hasSize(1)
    }

    @Test
    fun theQueueSurvivesANewDatabaseInstance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("shopping-test.db")
        file.parentFile?.mkdirs()
        val first = Room.databaseBuilder(context, ShoppingDatabase::class.java, file.path).allowMainThreadQueries().build()
        ShoppingStore(first).enqueue(QueuedKind.ADD, "k1", null, "Milk", 1_000)
        first.close()
        val second = Room.databaseBuilder(context, ShoppingDatabase::class.java, file.path).allowMainThreadQueries().build()
        try {
            val row = ShoppingStore(second).queueNow().single()
            assertThat(row.text).isEqualTo("Milk")
            assertThat(row.clientKey).isEqualTo("k1")
        } finally {
            second.close()
            context.deleteDatabase("shopping-test.db")
        }
    }

    @Test
    fun nothingPrintsItsText() {
        val printed = listOf(
            ShoppingItem("g1", "Milk", at).toString(),
            QueuedEntity(1, "ADD", "k1", null, "Milk", 1_000, 0, 0, "g1").toString(),
            LearnedEntity("milk", "Milk", 1, 1_000, 1_000).toString(),
            ItemEntity("g1", "Milk", 0, 1_000).toString(),
            StoredShopping(Connection("c1", "shopping.sample", "the sample list", mapOf(SHOPPING_CONFIG_ACCOUNT to "family@example.com")), ShoppingHealth.OK, null, null).toString(),
        )
        printed.forEach { assertThat(it.lowercase()).doesNotContain("milk") }
        assertThat(printed.last()).doesNotContain("family@example.com")
    }
}
