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
