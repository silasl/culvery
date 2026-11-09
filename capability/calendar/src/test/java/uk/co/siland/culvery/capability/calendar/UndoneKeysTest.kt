package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.UndoneCreateEntity

/** 20 §4.3: what Undo remembers is written through to the database and read back after a restart. */
@RunWith(AndroidJUnit4::class)
class UndoneKeysTest {
    private val db = calendarDb()
    private val dao = db.undoneCreateDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun eachChangeIsWrittenThrough() = runTest {
        val undone = UndoneKeys(dao)
        undone.add("k1", "c1", "s1", series = true)
        assertThat(dao.all()).containsExactly(UndoneCreateEntity("k1", "c1", "s1", series = true, stray = false))
        undone.stray("k1", undone["k1"]!!)
        assertThat(dao.all()).containsExactly(UndoneCreateEntity("k1", "c1", "s1", series = true, stray = true))
        undone.letGo("k1")
        assertThat(dao.all()).isEmpty()
    }

    @Test
    fun aStrayIsKeptPastDone() = runTest {
        val undone = UndoneKeys(dao)
        undone.add("k1", "c1", "s1", series = false)
        undone.stray("k1", undone["k1"]!!)
        undone.done("k1")
        assertThat(dao.all()).containsExactly(UndoneCreateEntity("k1", "c1", "s1", series = false, stray = true))
        assertThat(UndoneKeys(dao).apply { load() }.strayCreates.keys).containsExactly("k1")
    }

    @Test
    fun afterALoadEveryStoredKeyIsAStray() = runTest {
        dao.upsert(UndoneCreateEntity("k1", "c1", "s1", series = false, stray = false))
        dao.upsert(UndoneCreateEntity("k2", "c1", "s2", series = true, stray = true))
        val undone = UndoneKeys(dao)
        undone.load()
        assertThat(undone.strayCreates.mapValues { (_, it) -> Triple(it.connectionId, it.sourceId, it.series) }).containsExactly(
            "k1", Triple("c1", "s1", false),
            "k2", Triple("c1", "s2", true),
        )
        assertThat(undone.pending).containsExactly("k1", "k2")
    }

    @Test
    fun aLoadIsOnceAndKeepsWhatIsHeld() = runTest {
        dao.upsert(UndoneCreateEntity("k1", "c1", "s1", series = false, stray = true))
        val undone = UndoneKeys(dao)
        undone.sending("k0")
        undone.load()
        undone.add("k2", "c1", "s1", series = false)
        undone.load()
        assertThat(undone.strayCreates.keys).containsExactly("k1")
        assertThat(undone.pending).containsExactly("k1", "k2")
        assertThat(undone.isSending("k0")).isTrue()
    }
}
