package uk.co.siland.culvery.capability.shopping

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** Every message and action a toast was shown with. */
internal class ToastLog : Toaster {
    val messages = mutableListOf<String>()
    val actions = mutableListOf<ToastAction?>()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        messages += message
        actions += action
    }
}

/**
 * The shopping classes over one database whose queries run on the test scheduler, so virtual time only moves when a
 * test moves it, and the sample provider. The clock reads the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ShoppingRig(private val test: TestScope, private val fileName: String? = null, open: List<String> = emptyList()) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val clock = WallClock { test.testScheduler.currentTime }
    val provider = SampleShoppingProvider(open = open, clock = clock)
    val conn = Connection("c1", SAMPLE_DESCRIPTOR.id, "the sample list", emptyMap())
    val lock = ShoppingLock()

    var db: ShoppingDatabase = build()
        private set
    var store = ShoppingStore(db)
        private set
    var learning = ShoppingLearning(store, clock)
        private set
    var sync = ShoppingSync(store, setOf(provider), learning, lock, clock)
        private set

    private fun build(): ShoppingDatabase {
        val builder = if (fileName == null) {
            Room.inMemoryDatabaseBuilder(context, ShoppingDatabase::class.java)
        } else {
            Room.databaseBuilder(context, ShoppingDatabase::class.java, fileName)
        }
        return builder.allowMainThreadQueries().setQueryCoroutineContext(StandardTestDispatcher(test.testScheduler)).build()
    }

    /** As after the tablet restarts: the database file opened again under new objects. */
    fun reopen() {
        db.close()
        db = build()
        store = ShoppingStore(db)
        learning = ShoppingLearning(store, clock)
        sync = ShoppingSync(store, setOf(provider), learning, lock, clock)
    }

    suspend fun connect() = store.connect(conn)

    suspend fun enqueue(kind: QueuedKind, clientKey: String, text: String, itemId: String? = null, excludeIds: String? = null) =
        store.enqueue(kind, clientKey, itemId, text, clock.nowMillis(), excludeIds)

    /** The text of each open item on the provider, in its order. */
    suspend fun openTexts(): List<String> = provider.items(conn).map { it.text }

    fun checks(count: () -> Unit = {}) = ShoppingChecks(flowOf(true), MutableStateFlow(true), { count() }, test.backgroundScope)

    fun actions(toaster: Toaster = ToastLog(), checks: ShoppingChecks = checks()) = ShoppingActions(
        store,
        learning,
        lock,
        ShoppingDrainLoop({ null }, { clock.nowMillis() }, test.backgroundScope),
        checks,
        toaster,
        clock,
        test.backgroundScope,
    )

    fun close() {
        db.close()
        if (fileName != null) context.deleteDatabase(fileName)
    }
}

/** Closes each rig a test made. */
abstract class ShoppingTestBase {
    private val rigs = mutableListOf<ShoppingRig>()

    internal fun TestScope.rig(fileName: String? = null, open: List<String> = emptyList()) =
        ShoppingRig(this, fileName, open).also { rigs += it }

    @After
    fun closeRigs() = rigs.forEach { it.close() }
}

internal fun queued(
    seq: Long,
    kind: QueuedKind,
    clientKey: String,
    text: String,
    itemId: String? = null,
) = QueuedEntity(
    seq = seq,
    kind = kind.name,
    clientKey = clientKey,
    itemId = itemId,
    text = text,
    madeAtMillis = 0,
    attempts = 0,
    nextAtMillis = 0,
)
