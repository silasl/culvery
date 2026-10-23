package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OfflineException
import uk.co.siland.culvery.core.plugin.UnreachableException

/** 10 §2: item text is never logged, whatever the provider throws and whatever the exception says. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingLogTest : ShoppingTestBase() {
    @Test
    fun noLogLineHoldsItemText() = runTest {
        val rig = rig(open = listOf("Tea bags"))
        rig.connect()
        ShadowLog.clear()
        // Each message carries the item text, as a careless provider's might.
        val failures = listOf(
            NeedsSignInException("Bin bags"),
            UnreachableException("Tea bags"),
            OfflineException("Bin bags"),
            ListGoneException("Tea bags"),
            AccountGoneException("Bin bags"),
            ItemGoneException("Tea bags"),
            ShoppingRejectedException("Bin bags"),
            IllegalStateException("Tea bags"),
        )
        for (failure in failures) {
            rig.provider.failAll = failure
            rig.store.saveRead(emptyList(), ShoppingHealth.OK, 0)
            rig.enqueue(QueuedKind.TICK, "t1", "Tea bags", itemId = "g1")
            rig.enqueue(QueuedKind.ADD, "k1", "Bin bags")
            rig.sync.drain()
            rig.sync.check()
            advanceTimeBy(300_000)
        }
        val logged = ShadowLog.getLogs().map { it.msg.orEmpty() }
        assertThat(logged).isNotEmpty()
        assertThat(logged.filter { "Bin bags" in it || "bin bags" in it || "Tea bags" in it }).isEmpty()
    }
}
