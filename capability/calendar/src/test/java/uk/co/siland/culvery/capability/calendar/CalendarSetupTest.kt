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
}
