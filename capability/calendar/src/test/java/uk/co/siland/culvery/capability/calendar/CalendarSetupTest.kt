package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

@RunWith(AndroidJUnit4::class)
class CalendarSetupTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val provider = ScriptedProvider(
        "calendar.a",
        sourceList = listOf(CalendarSource("s1", "Alex", writable = false), CalendarSource("s2", "Family", writable = false)),
    )

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun connectStoresTheConnectionWithMappedSources() = runTest {
        val setup = CalendarSetup(store, setOf(provider))
        assertThat(setup.hasConnections()).isFalse()
        setup.connect(Connection("c1", "calendar.a", "A", emptyMap()), mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        assertThat(setup.hasConnections()).isTrue()
        assertThat(store.visibleSourcesFor("c1").associate { it.source.id to it.mapping.person }).containsExactly(
            "s1", PersonId("alex"),
            "s2", PersonId.FAMILY,
        )
    }

    @Test
    fun unknownProviderIsRejectedAndNothingIsStored() = runTest {
        val setup = CalendarSetup(store, setOf(provider))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { setup.connect(Connection("c1", "calendar.missing", "X", emptyMap()), emptyMap()) }
        }
        assertThat(setup.hasConnections()).isFalse()
    }

    private val writable = ScriptedProvider(
        "calendar.w",
        sourceList = listOf(CalendarSource("s1", "Alex", writable = false), CalendarSource("s2", "Family calendar", writable = true)),
        features = setOf(Feature.READ, Feature.WRITE),
    )

    @Test
    fun theMasterIsNullUntilChosen() = runTest {
        val setup = CalendarSetup(store, setOf(writable))
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        assertThat(setup.master()).isNull()
    }

    @Test
    fun setMasterMarksAWritableSourceAndAsksForASync() = runTest {
        var syncs = 0
        val setup = CalendarSetup(store, setOf(writable)) { syncs++ }
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        setup.setMaster("c1", "s2")
        assertThat(setup.master()?.source?.id).isEqualTo("s2")
        assertThat(syncs).isEqualTo(1)
    }

    @Test
    fun setMasterRecordsTheSourceAsWritableEvenIfAnOlderInstallStoredItReadOnly() = runTest {
        store.addConnection(
            Connection("c1", "calendar.w", "W", emptyMap()),
            listOf(CalendarSource("s2", "Family calendar", writable = false)),
            emptyMap(),
        )
        CalendarSetup(store, setOf(writable)).setMaster("c1", "s2")
        assertThat(store.source("c1", "s2")!!.source.writable).isTrue()
    }

    @Test
    fun setMasterRefusesAReadOnlySourceOrAProviderThatCannotWrite() = runTest {
        val setup = CalendarSetup(store, setOf(writable, provider))
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        setup.connect(Connection("c2", "calendar.a", "A", emptyMap()), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { setup.setMaster("c1", "s1") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { setup.setMaster("c2", "s2") } }
        assertThat(setup.master()).isNull()
    }
}
