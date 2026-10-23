package uk.co.siland.culvery.capability.shopping

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.plugin.WallClock

/** 10 §4.7: what is learned from adds, ticks and what is seen on the list. */
@RunWith(AndroidJUnit4::class)
class ShoppingLearningTest {
    private lateinit var db: ShoppingDatabase
    private lateinit var learning: ShoppingLearning
    private var now = START
    private val hour = 60 * 60_000L
    private val day = 24 * hour

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShoppingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        learning = ShoppingLearning(ShoppingStore(db), WallClock { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun rows() = learning.learned.first().associateBy { it.key }

    private fun item(id: String, text: String, at: Long = now) = ShoppingItem(id, text, Instant.ofEpochMilli(at))

    private fun monthsBefore(millis: Long, months: Long) =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).minusMonths(months).toInstant().toEpochMilli()

    @Test
    fun anAddCountsOncePerDayAndTheBestCasingWins() = runTest {
        learning.added("milk")
        now += hour
        learning.added("Milk")
        now = START + 25 * hour
        learning.added("milk")
        val row = rows().getValue("milk")
        assertThat(rows()).hasSize(1)
        assertThat(row.count).isEqualTo(2)
        assertThat(row.text).isEqualTo("Milk")
        assertThat(betterText("Milk", "MILK")).isEqualTo("MILK")
        assertThat(betterText("Milk", "milk")).isEqualTo("Milk")
    }

    @Test
    fun anItemSeenTwiceCountsOnce() = runTest {
        learning.seen(listOf(item("g1", "Milk")))
        learning.seen(listOf(item("g1", "Milk")))
        assertThat(rows().getValue("milk").count).isEqualTo(1)
        now += 25 * hour
        learning.seen(listOf(item("g2", "Milk")))
        assertThat(rows().getValue("milk").count).isEqualTo(2)
    }

    @Test
    fun aYearOfHistoryCountsMilkOncePerDayItWasTicked() = runTest {
        learning.seen(
            listOf(
                item("h3", "Milk", START - day * 3),
                item("h1", "Milk", START - day),
                item("h2", "Milk", START - day * 2),
                item("h2b", "milk", START - day * 2 + hour),
            ),
        )
        val row = rows().getValue("milk")
        assertThat(row.count).isEqualTo(3)
        assertThat(row.lastMillis).isEqualTo(START - day)
    }

    @Test
    fun aWallAddMarkedSeenIsNotCountedAgain() = runTest {
        learning.added("Milk")
        learning.markSeen("g1")
        now += 2 * day
        learning.seen(listOf(item("g1", "Milk")))
        assertThat(rows().getValue("milk").count).isEqualTo(1)
    }

    @Test
    fun aTickOnlyUpdatesTheDate() = runTest {
        learning.added("Milk")
        now += 2 * day
        learning.added("Milk")
        now += day
        learning.ticked("Milk")
        val row = rows().getValue("milk")
        assertThat(row.count).isEqualTo(2)
        assertThat(row.lastMillis).isEqualTo(now)
    }

    @Test
    fun aTickOfAForgottenItemDoesNotLearnItAgain() = runTest {
        learning.ticked("Milk")
        assertThat(rows()).isEmpty()
    }

    @Test
    fun aYearUnusedIsForgotten() = runTest {
        now = monthsBefore(START, 12) - day
        learning.added("Butter")
        now = monthsBefore(START, 11)
        learning.added("Bread")
        now = START
        learning.pruneIfDue()
        assertThat(rows().keys).containsExactly("bread")
    }

    @Test
    fun pruningWaitsADayBetweenRuns() = runTest {
        now = monthsBefore(START, 12) + 12 * hour
        learning.added("Bread")
        now = START
        learning.pruneIfDue()
        assertThat(rows().keys).containsExactly("bread")
        now = START + 23 * hour
        learning.pruneIfDue()
        assertThat(rows().keys).containsExactly("bread")
        now = START + 25 * hour
        learning.pruneIfDue()
        assertThat(rows()).isEmpty()
    }

    @Test
    fun theStoreKeepsAThousand() = runTest {
        learning.added("Anchovies")
        now += 2 * day
        learning.added("Anchovies")
        for (i in 0 until LEARNED_MAX) {
            now += 1_000
            learning.added("Thing $i")
        }
        assertThat(rows()).hasSize(LEARNED_MAX)
        assertThat(rows()).containsKey("anchovies")
        assertThat(rows()).doesNotContainKey("thing 0")
        assertThat(rows()).containsKey("thing 999")
    }

    @Test
    fun forgetAndClearRemoveLearning() = runTest {
        learning.added("Butter")
        learning.added("Bread")
        learning.forget("butter")
        assertThat(rows().keys).containsExactly("bread")
        learning.seen(listOf(item("g1", "Eggs")))
        learning.clear()
        assertThat(rows()).isEmpty()
        learning.seen(listOf(item("g1", "Eggs")))
        assertThat(rows()).isEmpty()
    }

    private companion object {
        const val START = 1_800_000_000_000L
    }
}
