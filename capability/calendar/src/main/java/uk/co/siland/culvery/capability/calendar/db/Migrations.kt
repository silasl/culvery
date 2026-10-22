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
 * v6 (Plan 4c): a calendar's read problem (§6.4); and every sync cursor cleared, as their key changes meaning (§6.2),
 * so each calendar is read in full once. The SQL must match schemas/…/6.json exactly.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `source` ADD COLUMN `readProblem` TEXT")
        db.execSQL("DELETE FROM `sync_state`")
    }
}

/**
 * v7 (Plan 4e): the series each occurrence belongs to; and every sync cursor cleared, so each calendar is read in full
 * once and its stored occurrences get their series. The SQL must match schemas/…/7.json exactly.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `event` ADD COLUMN `seriesId` TEXT")
        db.execSQL("DELETE FROM `sync_state`")
    }
}

/**
 * v8 (Plan 9c-1c): the titles the Add event editor offers. Filled by the first sync pass after the upgrade, from the
 * stored events. The SQL must match schemas/…/8.json exactly.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remembered_titles` (`connectionId` TEXT NOT NULL, `titleKey` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `sourceEventId` TEXT NOT NULL, `forPerson` TEXT NOT NULL, " +
                "`allDay` INTEGER NOT NULL, `startMinute` INTEGER, `lengthMinutes` INTEGER, `days` INTEGER, `rule` TEXT, " +
                "`seriesStart` TEXT, `rememberedDate` TEXT NOT NULL, PRIMARY KEY(`connectionId`, `titleKey`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remembered_titles_rememberedDate` ON `remembered_titles` (`rememberedDate`)")
    }
}

/** Every migration, oldest first: the database builder and the tests add these. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
