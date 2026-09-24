package uk.co.siland.culvery.capability.calendar

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_1_2
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/**
 * Drives MigrationTestHelper with the plain framework SQLite driver ([AndroidSQLiteDriver]) rather than
 * Room's default SupportSQLiteOpenHelper-backed one. That default path compares the configured database name
 * against the requested filename using `substringAfterLast('/')` (androidx.sqlite 2.6.0-2.6.2's
 * SupportSQLiteDriver.open), which always mismatches on Windows because Robolectric's absolute path is
 * backslash-separated there. AndroidSQLiteDriver opens the file directly with no such comparison.
 */
@RunWith(AndroidJUnit4::class)
class CalendarMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = context.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), file, AndroidSQLiteDriver(), CalendarDatabase::class)

    @Test
    fun migrationFromV1KeepsConnectionsSourcesEventsAndCursors() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v1 = helper.createDatabase(1)
        v1.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{\"k\":\"v\"}', 'NEEDS_SIGN_IN', NULL, 1234)",
        )
        v1.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family'), ('c1', 's2', 'Alex', 0, 0, 'alex-id')",
        )
        v1.execSQL(
            "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                "recurring, forPerson, createdBy, startSort, endSort) " +
                "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 0, 'alex-id', 'sam-id', 1000, 2000)",
        )
        v1.execSQL(
            "INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) " +
                "VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')",
        )
        v1.close()

        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).close()

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            val stored = store.connectionsNow().single()
            assertThat(stored.connection).isEqualTo(Connection("c1", "calendar.test", "Google", mapOf("k" to "v")))
            assertThat(stored.health).isEqualTo(ConnectionHealth.NeedsSignIn)
            assertThat(stored.lastSyncMillis).isEqualTo(1234L)

            assertThat(store.sources().first().map { Triple(it.source.id, it.mapping, it.isMaster) }).containsExactly(
                Triple("s1", SourceMapping(PersonId.FAMILY, visible = true), false),
                Triple("s2", SourceMapping(PersonId("alex-id"), visible = false), false),
            )
            assertThat(store.source("c1", "s1")!!.source.writable).isTrue()

            val event = store.eventNow(EventRef("c1", "s1", "e1"))!!
            assertThat(listOf(event.title, event.forPerson, event.createdBy)).containsExactly("Swim", "alex-id", "sam-id").inOrder()
            assertThat(event.start).isEqualTo(EventTime.Timed(Instant.ofEpochMilli(1000)))

            val range = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))

            assertThat(store.pendingNow()).isEmpty()
            assertThat(store.master().first()).isNull()
        } finally {
            db.close()
        }
    }
}
