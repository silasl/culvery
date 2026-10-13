package uk.co.siland.culvery.core.household

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.household.db.MIGRATION_1_2

@RunWith(AndroidJUnit4::class)
class HouseholdMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = context.getDatabasePath("household-migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), file, AndroidSQLiteDriver(), HouseholdDatabase::class)

    @Test
    fun redBecomesOrangeAndVioletBrownAndSlateTakeTheFirstFreeColours() = runTest {
        file.parentFile?.mkdirs()
        file.delete()
        val v1 = helper.createDatabase(1)
        listOf("red" to 0xFFE06666, "green" to 0xFF4CB387, "violet" to 0xFF9C7CE3, "brown" to 0xFFA1785A, "slate" to 0xFF8B96A8)
            .forEachIndexed { i, (id, colour) ->
                v1.execSQL("INSERT INTO person (id, name, color, sortOrder, role, pinHash, salt) VALUES ('$id', '$id', $colour, $i, 'ADULT', NULL, NULL)")
            }
        v1.close()

        val v2 = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        val colours = buildMap {
            v2.prepare("SELECT id, color FROM person").use { while (it.step()) put(it.getText(0), it.getLong(1)) }
        }
        v2.close()

        assertThat(colours).containsExactly(
            "red", 0xFFE04A26,
            "green", 0xFF4CB387,
            "violet", 0xFF5B9BE0,
            "brown", 0xFFE07BA8,
            "slate", 0xFF9CC44E,
        )
    }
}
