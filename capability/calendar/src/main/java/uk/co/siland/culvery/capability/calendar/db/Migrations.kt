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
