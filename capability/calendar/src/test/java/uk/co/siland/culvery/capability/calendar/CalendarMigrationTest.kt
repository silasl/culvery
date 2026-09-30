package uk.co.siland.culvery.capability.calendar

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
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
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_2_3
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_3_4
import uk.co.siland.culvery.capability.calendar.db.MIGRATION_4_5
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
/** A draft as a v2 outbox row stores it. */
private const val DRAFT_JSON =
    """{"title":"Swim","start":{"instant":1000},"end":{"instant":2000},"forPerson":"alex-id","createdBy":"alex-id"}"""

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

    @Test
    fun migrationFromV2AddsAnEmptyClientKeyAndKeepsEverything() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v2 = helper.createDatabase(2)
        v2.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{}', 'OK', NULL, 1234)",
        )
        v2.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1), ('c1', 's2', 'Alex', 0, 1, 'alex-id', 0)",
        )
        v2.execSQL(
            "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                "recurring, forPerson, createdBy, startSort, endSort) " +
                "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 0, 'alex-id', 'sam-id', 1000, 2000)",
        )
        v2.execSQL(
            "INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) " +
                "VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')",
        )
        v2.execSQL(
            "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis) VALUES " +
                "('c1', 's1', NULL, 'CREATE', '$DRAFT_JSON', 0, 10, 100), " +
                "('c1', 's1', 'e1', 'UPDATE', '$DRAFT_JSON', 1, 20, 200), " +
                "('c1', 's1', 'e1', 'ASSIGN', '$DRAFT_JSON', 0, 30, 300), " +
                "('c1', 's1', 'e1', 'DELETE', NULL, 2, 40, 400)",
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3))
        try {
            // The driver-based helper may not notice a dropped table, so the list is checked here.
            assertThat(tableNames(v3)).containsExactly("connection", "event", "outbox", "source", "sync_state").inOrder()
        } finally {
            v3.close()
        }

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            assertThat(store.connectionsNow().single().connection.label).isEqualTo("Google")
            assertThat(store.master().first()?.source?.id).isEqualTo("s1")
            assertThat(store.eventNow(EventRef("c1", "s1", "e1"))?.title).isEqualTo("Swim")
            val range = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))

            val pending = store.pendingNow()
            assertThat(pending.map { it.kind })
                .containsExactly(ChangeKind.CREATE, ChangeKind.UPDATE, ChangeKind.ASSIGN, ChangeKind.DELETE).inOrder()
            assertThat(pending.map { it.clientKey }).containsExactly(null, null, null, null)
            assertThat(pending.map { it.attempts }).containsExactly(0, 1, 0, 2).inOrder()
            assertThat(pending.first().draft?.title).isEqualTo("Swim")
            // A v2 create has no key, so no ref: the drain drops it as undeliverable (Task 2's isComplete).
            assertThat(pending.first().ref).isNull()
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationFromV3AddsTheNewColumnsEmptyAndKeepsEverything() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v3 = helper.createDatabase(3)
        v3.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{\"account\":\"family@example.com\"}', 'NEEDS_SIGN_IN', NULL, 1234)",
        )
        v3.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1), ('c1', 's2', 'Alex', 0, 0, 'alex-id', 0)",
        )
        v3.execSQL(
            "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                "recurring, forPerson, createdBy, startSort, endSort) " +
                "VALUES ('c1', 's1', 'e1', 'Swim', 1000, NULL, 2000, NULL, 1, 'alex-id', 'sam-id', 1000, 2000)",
        )
        v3.execSQL(
            "INSERT INTO sync_state (connectionId, sourceId, cursor, rangeStart) " +
                "VALUES ('c1', 's1', 'k7', '2026-09-22|Europe/London')",
        )
        v3.execSQL(
            "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis, clientKey) VALUES " +
                "('c1', 's1', NULL, 'CREATE', '$DRAFT_JSON', 0, 10, 100, 'k-1'), " +
                "('c1', 's1', 'e1', 'UPDATE', '$DRAFT_JSON', 1, 20, 200, NULL), " +
                "('c1', 's1', 'e1', 'ASSIGN', '$DRAFT_JSON', 0, 30, 300, NULL), " +
                "('c1', 's1', 'e1', 'DELETE', NULL, 2, 40, 400, NULL)",
        )
        v3.close()

        val v4 = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))
        try {
            assertThat(tableNames(v4)).containsExactly("connection", "event", "outbox", "source", "sync_state").inOrder()
        } finally {
            v4.close()
        }

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            val stored = store.connectionsNow().single()
            assertThat(stored.connection.config).containsExactly("account", "family@example.com")
            assertThat(stored.health).isEqualTo(ConnectionHealth.NeedsSignIn)
            // D16: an existing NEEDS_SIGN_IN connection's pause starts at the next NeedsSignIn a sync reports.
            assertThat(listOf(stored.sourcesCheckedMillis, stored.needsSignInSinceMillis)).containsExactly(null, null)
            assertThat(store.master().first()?.source?.id).isEqualTo("s1")
            assertThat(store.source("c1", "s2")!!.mapping).isEqualTo(SourceMapping(PersonId("alex-id"), visible = false))

            val event = store.eventNow(EventRef("c1", "s1", "e1"))!!
            assertThat(listOf(event.title, event.recurring, event.recurrenceRule)).containsExactly("Swim", true, null).inOrder()
            val range = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
            assertThat(store.cursor("c1", "s1", range)).isEqualTo(SyncCursor("k7"))

            val pending = store.pendingNow()
            assertThat(pending.map { it.kind })
                .containsExactly(ChangeKind.CREATE, ChangeKind.UPDATE, ChangeKind.ASSIGN, ChangeKind.DELETE).inOrder()
            assertThat(pending.map { it.clientKey }).containsExactly("k-1", null, null, null).inOrder()
            assertThat(pending.map { it.fields }).containsExactly(null, null, null, null)
            assertThat(pending.map { it.createdMillis }).containsExactly(100L, 200L, 300L, 400L).inOrder()
            assertThat(pending.first().draft?.forPersonColor).isNull()
            // A v3 row's fields are what it sent then: every field for an update, who for an assign.
            assertThat(fieldsFor(ChangeKind.UPDATE, pending[1].fields)).containsExactlyElementsIn(EventField.entries)
            assertThat(fieldsFor(ChangeKind.ASSIGN, pending[2].fields)).containsExactly(EventField.FOR_PERSON)
        } finally {
            db.close()
        }
    }

    /** The app's own tables, without SQLite's, Android's and Room's bookkeeping. */
    @Test
    fun migrationFromV4CopiesVisibilityIntoTheLastSeenTick() = runTest {
        file.parentFile?.mkdirs()
        file.delete()

        val v4 = helper.createDatabase(4)
        v4.execSQL(
            "INSERT INTO connection (id, providerId, label, configJson, health, healthMessage, lastSyncMillis, " +
                "sourcesCheckedMillis, needsSignInSinceMillis) " +
                "VALUES ('c1', 'calendar.test', 'Google', '{}', 'OK', NULL, 1234, 5000, NULL)",
        )
        v4.execSQL(
            "INSERT INTO source (connectionId, sourceId, name, writable, visible, personId, isMaster) " +
                "VALUES ('c1', 's1', 'Family', 1, 1, 'family', 1), ('c1', 's2', 'Alex', 0, 0, 'alex-id', 0), " +
                "('c1', 's3', 'Swimming', 0, 1, 'mia-id', 0)",
        )
        v4.close()

        val v5 = helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5))
        try {
            assertThat(tableNames(v5)).containsExactly("connection", "event", "outbox", "source", "sync_state").inOrder()
            val statement = v5.prepare("SELECT sourceId, shownInService FROM source ORDER BY sourceId")
            val ticks = mutableListOf<Pair<String, Long>>()
            try {
                while (statement.step()) ticks += statement.getText(0) to statement.getLong(1)
            } finally {
                statement.close()
            }
            assertThat(ticks).containsExactly("s1" to 1L, "s2" to 0L, "s3" to 1L).inOrder()
        } finally {
            v5.close()
        }

        val db = Room.databaseBuilder(context, CalendarDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            // refreshSources runs in withTransaction, which needs the framework open helper, not the driver.
            .allowMainThreadQueries()
            .build()
        try {
            val store = CalendarStore(db)
            // The service still ticks as before: an upgraded install keeps every choice, the hidden Alex included.
            val listed = listOf(
                CalendarSource("s1", "Family", writable = true, primary = true),
                CalendarSource("s2", "Alex", writable = false, shown = false),
                CalendarSource("s3", "Swimming", writable = false),
            )
            store.refreshSources("c1", listed, 6_000L) { SourceMapping.Default }
            assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
                "s1", SourceMapping(PersonId.FAMILY, visible = true),
                "s2", SourceMapping(PersonId("alex-id"), visible = false),
                "s3", SourceMapping(PersonId("mia-id"), visible = true),
            )
            assertThat(store.master().first()?.source?.id).isEqualTo("s1")
        } finally {
            db.close()
        }
    }

    private fun tableNames(connection: SQLiteConnection): List<String> {
        val statement = connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
        )
        try {
            val names = mutableListOf<String>()
            while (statement.step()) names += statement.getText(0)
            return names
        } finally {
            statement.close()
        }
    }
}
