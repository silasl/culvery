package uk.co.siland.culvery.capability.calendar

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import uk.co.siland.culvery.core.plugin.settingsDataStore

@RunWith(AndroidJUnit4::class)
class CalendarSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun store() = settingsDataStore(scope) { File(folder.root, "calendar_settings.preferences_pb") }

    @Test
    fun defaultsToEight() = runTest {
        assertThat(CalendarSettings(store(), scope).shown.first()).isEqualTo(WeeksShown.EIGHT)
    }

    @Test
    fun keepsAChoice() = runTest {
        val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        CalendarSettings(settingsDataStore(writer) { File(folder.root, "calendar_settings.preferences_pb") }, writer).setShown(WeeksShown.TWELVE)
        writer.coroutineContext[Job]!!.cancelAndJoin()
        assertThat(CalendarSettings(store(), scope).shown.first()).isEqualTo(WeeksShown.TWELVE)
    }

    @Test
    fun anUnknownNameFallsBack() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("weeks_shown")] = "SIXTEEN" }
        assertThat(CalendarSettings(store, scope).shown.first()).isEqualTo(WeeksShown.EIGHT)
    }

    /** 15a §2.3: the last day follows the setting, and the last week is one less than the weeks. */
    @Test
    fun lastShownDayFollowsTheSetting() {
        val today = LocalDate.of(2026, 10, 30)
        assertThat(lastShownDay(today, WeeksShown.FOUR)).isEqualTo(LocalDate.of(2026, 11, 26))
        assertThat(lastShownDay(today, WeeksShown.EIGHT)).isEqualTo(LocalDate.of(2026, 12, 24))
        assertThat(lastShownDay(today, WeeksShown.TWELVE)).isEqualTo(LocalDate.of(2027, 1, 21))
        assertThat(WeeksShown.entries.map { it.weeksAfter }).containsExactly(3, 7, 11).inOrder()
    }
}
