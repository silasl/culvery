package uk.co.siland.culvery.capability.calendar.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2 (Plan 2b-1): the master-calendar flag and the outbox. The SQL must match schemas/…/2.json exactly. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `isMaster` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`connectionId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `remoteId` TEXT, `kind` TEXT NOT NULL, " +
                "`draftJson` TEXT, `attempts` INTEGER NOT NULL, `nextAttemptMillis` INTEGER NOT NULL, " +
                "`createdMillis` INTEGER NOT NULL)",
        )
    }
}

/** v3 (Plan 2b-2): a create's client key on the outbox. The SQL must match schemas/…/3.json exactly. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `clientKey` TEXT")
    }
}

/**
 * v4 (Plan 3a): the series' RRULE on each event; an update's touched fields on the outbox; when a connection's
 * sources were last refreshed and when its sign-in pause began (D16). The SQL must match schemas/…/4.json exactly.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `event` ADD COLUMN `recurrenceRule` TEXT")
        db.execSQL("ALTER TABLE `outbox` ADD COLUMN `fields` TEXT")
        db.execSQL("ALTER TABLE `connection` ADD COLUMN `sourcesCheckedMillis` INTEGER")
        db.execSQL("ALTER TABLE `connection` ADD COLUMN `needsSignInSinceMillis` INTEGER")
    }
}

/**
 * v5 (Plan 4a): the service's last-seen tick per calendar, seeded from `visible` (which until now always followed the
 * tick, the primary always shown), so the first refresh after the upgrade changes nothing. The SQL must match
 * schemas/…/5.json exactly.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `shownInService` INTEGER")
        db.execSQL("UPDATE `source` SET `shownInService` = `visible`")
    }
}

/**
 * v6 (Plan 4c): a calendar's read problem (§6.4); and every sync cursor cleared, as their key changes meaning (§6.2), so
 * each calendar is read in full once. The SQL must match schemas/…/6.json exactly.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `readProblem` TEXT")
        db.execSQL("DELETE FROM `sync_state`")
    }
}

/** Every migration, oldest first: the database builder and the tests add these. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
