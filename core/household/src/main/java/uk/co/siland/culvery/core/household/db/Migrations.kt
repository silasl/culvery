package uk.co.siland.culvery.core.household.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val RED = 0xFFE06666
private const val ORANGE = 0xFFE04A26
private val RETIRED = listOf(0xFF9C7CE3, 0xFFA1785A) // violet, now Family's, and brown

// PersonPalette's colours as of v2; :core:ui's list may move on, this migration mustn't.
private val V2_PALETTE = listOf(0xFF4CB387, 0xFF5B9BE0, 0xFFE07BA8, 0xFF9CC44E, ORANGE, 0xFFCEC018)

/** v2 (4e): red becomes orange; whoever had violet or brown takes the first colour nobody has, in list order. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE person SET color = $ORANGE WHERE color = $RED")
        val people = buildList {
            db.query("SELECT id, color FROM person ORDER BY sortOrder").use { while (it.moveToNext()) add(it.getString(0) to it.getLong(1)) }
        }
        val taken = people.map { it.second }.toMutableSet()
        people.filter { it.second in RETIRED }.forEach { (id, _) ->
            val free = V2_PALETTE.firstOrNull { it !in taken } ?: return@forEach
            taken += free
            db.execSQL("UPDATE person SET color = ? WHERE id = ?", arrayOf<Any>(free, id))
        }
    }
}
