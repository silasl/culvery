package uk.co.siland.culvery.capability.shopping

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.NeedsSignInException

/** 10 §4.10, §4.11: turning the list on and off, each behind an Admin's fresh PIN. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShoppingConnectionsTest : ShoppingTestBase() {
    private val day = 24 * 60 * 60_000L

    private fun TestScope.connections(
        rig: ShoppingRig = rig(),
        access: FakeAccess = FakeAccess(),
        providers: Set<ShoppingProvider> = setOf(rig.provider),
    ) = ConnectionsRig(rig, backgroundScope, access, providers = providers)

    /** Lets the database's threads finish and runs what they unblock, with virtual time standing still. */
    private fun TestScope.pump() = repeat(100) {
        runCurrent()
        Thread.sleep(5)
    }

    private suspend fun ShoppingRig.learnedCounts(): Map<String, Int> = store.learned.first().associate { it.text to it.count }

    @Test
    fun connectingAsksForAnAdminPin() = runTest {
        val refused = connections(access = FakeAccess(admin = false))
        assertThat(refused.connections.mayConnect()).isFalse()
        assertThat(refused.rig.store.connectionNow()).isNull()
        assertThat(refused.access.asked).containsExactly(listOf(CorePermissions.CONNECTIONS_MANAGE))

        val allowed = connections(access = FakeAccess(admin = true))
        assertThat(allowed.connections.mayConnect()).isTrue()
    }

    @Test
    fun connectingSeedsFavourites() = runTest {
        val c = connections()
        advanceTimeBy(60 * day)
        val now = testScheduler.currentTime
        // Butter on three different days, Bananas on two.
        listOf("Butter" to 2L, "Bananas" to 4L, "Butter" to 9L, "Bananas" to 11L, "Butter" to 16L, "Bin bags" to 6L)
            .forEach { (text, daysAgo) -> c.rig.provider.tickedOn(text, Instant.ofEpochMilli(now - daysAgo * day)) }

        c.connections.connected(c.rig.conn)
        pump()

        assertThat(c.rig.learnedCounts()).containsExactly("Butter", 3, "Bananas", 2, "Bin bags", 1)
        assertThat(c.rig.store.connectionNow()!!.seeded).isTrue()
    }

    @Test
    fun theFirstConnectDoesNotWaitOnItsOwnLock() = runTest {
        val c = connections()
        c.connections.connected(c.rig.conn)
        pump()
        // check() takes the lock itself; connected() must have let go of it first.
        assertThat(c.rig.provider.calls).contains("items")
        assertThat(c.rig.store.connectionNow()!!.lastReadMillis).isNotNull()
    }

    @Test
    fun anItemQueuedBeforeConnectingDrainsAfterConnecting() = runTest {
        val c = connections()
        c.rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        c.loop.start()
        pump()
        assertThat(c.rig.provider.calls).isEmpty()

        c.connections.connected(c.rig.conn)
        pump()

        assertThat(c.rig.openTexts()).containsExactly("Milk")
        assertThat(c.rig.store.queueNow()).isEmpty()
    }

    @Test
    fun reallowingKeepsTheQueue() = runTest {
        val c = connections()
        c.rig.connect()
        c.rig.store.setHealth(ShoppingHealth.NEEDS_PERMISSION)
        c.rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        c.rig.enqueue(QueuedKind.ADD, "k2", "Bread")
        val again = c.rig.conn.copy(config = mapOf("account" to "family@example.com"))

        c.connections.reallowed(again)
        pump()

        val stored = c.rig.store.connectionNow()!!
        assertThat(stored.connection).isEqualTo(again)
        assertThat(stored.health).isEqualTo(ShoppingHealth.OK)
        assertThat(c.rig.store.queueNow().map { it.text }).containsExactly("Milk", "Bread").inOrder()
    }

    @Test
    fun reallowingDrainsWhatWasWaiting() = runTest {
        val c = connections()
        c.rig.connect()
        c.rig.provider.failAll = NeedsSignInException()
        c.rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        c.rig.enqueue(QueuedKind.ADD, "k2", "Bread")
        c.loop.start()
        pump()
        assertThat(c.rig.store.connectionNow()!!.health).isEqualTo(ShoppingHealth.NEEDS_PERMISSION)
        assertThat(c.rig.store.queueNow()).hasSize(2)

        c.rig.provider.failAll = null
        val before = testScheduler.currentTime
        c.connections.reallowed(c.rig.conn)
        pump()
        // No time passed: the retry in 30 s is not what sent them.
        assertThat(testScheduler.currentTime).isEqualTo(before)

        assertThat(c.rig.store.queueNow()).isEmpty()
        assertThat(c.rig.openTexts()).containsExactly("Bread", "Milk")
    }

    @Test
    fun reallowingAConnectionThatWasStoppedStoresNothing() = runTest {
        val c = connections()
        c.connections.reallowed(c.rig.conn)
        pump()
        assertThat(c.rig.store.connectionNow()).isNull()
    }

    @Test
    fun makingANewListAsksForAPinAndKeepsTheQueue() = runTest {
        val refused = connections(access = FakeAccess(admin = false))
        refused.rig.connect()
        assertThat(refused.connections.remakeList()).isFalse()
        assertThat(refused.rig.provider.calls).isEmpty()

        val c = connections()
        c.rig.connect()
        c.rig.store.setHealth(ShoppingHealth.LIST_GONE)
        c.rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        assertThat(c.connections.remakeList()).isTrue()
        assertThat(c.rig.provider.calls).containsExactly("remakeList")
        val stored = c.rig.store.connectionNow()!!
        assertThat(stored.connection.config).containsEntry("list", "new")
        assertThat(stored.health).isEqualTo(ShoppingHealth.OK)
        assertThat(c.rig.store.queueNow()).hasSize(1)
    }

    @Test
    fun stopKeepsWhatWasLearned() = runTest {
        val c = connections()
        c.rig.connect()
        c.rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        c.rig.enqueue(QueuedKind.ADD, "k2", "Bread")
        c.rig.learning.added("Butter")
        assertThat(c.connections.queued()).isEqualTo(2)

        assertThat(c.connections.stop()).isTrue()

        assertThat(c.rig.store.connectionNow()).isNull()
        assertThat(c.rig.store.queueNow()).isEmpty()
        assertThat(c.rig.learnedCounts()).containsExactly("Butter", 1)
        assertThat(c.toasts.messages).containsExactly("Stopped using the sample list.")
    }

    @Test
    fun stoppingForgettingAndClearingAskForAnAdminPin() = runTest {
        val c = connections(access = FakeAccess(admin = false))
        c.rig.connect()
        c.rig.enqueue(QueuedKind.ADD, "k1", "Milk")
        c.rig.learning.added("Butter")
        val butter = c.rig.store.learned.first().single()

        assertThat(c.connections.stop()).isFalse()
        assertThat(c.connections.forget(butter)).isFalse()
        assertThat(c.connections.clearSuggestions()).isFalse()

        assertThat(c.rig.store.connectionNow()).isNotNull()
        assertThat(c.rig.store.queueNow()).hasSize(1)
        assertThat(c.rig.learnedCounts()).containsExactly("Butter", 1)
        assertThat(c.toasts.messages).isEmpty()
        assertThat(c.access.asked).hasSize(3)
        assertThat(c.access.asked.flatten().toSet()).containsExactly(CorePermissions.CONNECTIONS_MANAGE)
    }

    @Test
    fun forgettingAndClearingChangeWhatIsLearned() = runTest {
        val c = connections()
        c.rig.learning.added("Butter")
        c.rig.learning.added("Tea bags")
        val butter = c.rig.store.learned.first().first { it.text == "Butter" }

        assertThat(c.connections.forget(butter)).isTrue()
        assertThat(c.rig.learnedCounts()).containsExactly("Tea bags", 1)
        assertThat(c.connections.clearSuggestions()).isTrue()
        assertThat(c.rig.learnedCounts()).isEmpty()
        assertThat(c.toasts.messages).containsExactly("Forgot Butter.", "Suggestions cleared.").inOrder()
    }

    @Test
    fun theAccountUseSaysWhetherShoppingUsesIt() = runTest {
        val rig = rig()
        val use = ShoppingAccountUse(rig.store)
        assertThat(use.uses("family@example.com")).isFalse()

        rig.store.connect(Connection("c1", SAMPLE_DESCRIPTOR.id, "the sample list", mapOf(SHOPPING_CONFIG_ACCOUNT to "family@example.com")))

        assertThat(use.uses("FAMILY@example.com")).isTrue()
        assertThat(use.uses("sam@example.com")).isFalse()
    }

    @Test
    fun theDebugSampleIsOfferedFirst() = runTest {
        val rig = rig()
        val sample = rig.provider
        val phone = SampleShoppingProvider(clock = rig.clock, descriptor = SAMPLE_DESCRIPTOR.copy(id = "shopping.other", userConnectable = true))

        assertThat(connections(rig, providers = setOf(phone, sample)).connections.offered).isSameInstanceAs(sample)
        assertThat(connections(rig, providers = setOf(sample, phone)).connections.offered).isSameInstanceAs(sample)
        assertThat(connections(rig, providers = setOf(phone)).connections.offered).isSameInstanceAs(phone)
        assertThat(connections(rig, providers = emptySet()).connections.offered).isNull()
    }
}
