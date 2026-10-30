package uk.co.siland.culvery.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalTime
import java.time.MonthDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.ui.SeasonDates
import uk.co.siland.culvery.core.ui.SeasonalTheme

@RunWith(AndroidJUnit4::class)
class DisplaySettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rawStore by lazy { settingsDataStore(scope) { File(folder.root, FILE_NAME) } }
    private val settings by lazy { DisplaySettings(rawStore, scope) }

    @After
    fun tearDown() = scope.cancel()

    /** Fails if a default drifts from the spec: night 22:00 to 07:00, 70 and 25 percent, 5 minutes, camera switch unset, camera not asked. */
    @Test
    fun theDefaults() = runTest {
        assertThat(settings.prefs.first()).isEqualTo(
            DisplayPrefs(LocalTime.of(22, 0), LocalTime.of(7, 0), 70, 25, 5, null, false),
        )
    }

    /** Fails if `read` shows the defaults before the file has been read, which would look like a saved choice. */
    @Test
    fun readIsNullUntilTheFileIsRead() = runTest {
        val own = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val fresh = DisplaySettings(settingsDataStore(own) { File(folder.root, FILE_NAME) }, own)
        assertThat(fresh.read.value).isNull()
        assertThat(fresh.current.value).isEqualTo(DisplayPrefs())
        assertThat(fresh.read.first { it != null }).isEqualTo(DisplayPrefs())
        own.cancel()
    }

    /** Fails if any setter's value is lost, or is not read back by a new store on the same file. */
    @Test
    fun everySettingIsKept() = runTest {
        settings.setNight(LocalTime.of(21, 30), LocalTime.of(6, 45))
        settings.setDayPercent(55)
        settings.setEveningPercent(15)
        settings.setSleepAfter(10)
        settings.setWakeByCamera(false)
        settings.setCameraAsked()
        val expected = DisplayPrefs(LocalTime.of(21, 30), LocalTime.of(6, 45), 55, 15, 10, false, true)
        assertThat(settings.prefs.first()).isEqualTo(expected)

        scope.cancel()
        val again = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertThat(displaySettings(folder.root, again).prefs.first()).isEqualTo(expected)
        } finally {
            again.cancel()
        }
    }

    /** Fails if a file that can't be parsed stops the settings instead of giving the defaults. */
    @Test
    fun aFileThatCantBeReadIsTheDefaults() = runTest {
        File(folder.root, FILE_NAME).writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04))
        assertThat(settings.prefs.first()).isEqualTo(DisplayPrefs())
    }

    /** Fails if a brightness outside 5 to 100 is kept as given. */
    @Test
    fun percentsAreKeptInRange() = runTest {
        settings.setDayPercent(0)
        settings.setEveningPercent(120)
        val prefs = settings.prefs.first()
        assertThat(prefs.dayPercent).isEqualTo(5)
        assertThat(prefs.eveningPercent).isEqualTo(100)
    }

    /** Fails if a delay that Settings doesn't offer is saved. */
    @Test
    fun onlyTheOfferedDelaysAreKept() = runTest {
        var thrown: Throwable? = null
        try {
            settings.setSleepAfter(7)
        } catch (e: IllegalArgumentException) {
            thrown = e
        }
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(settings.prefs.first().sleepAfterMinutes).isEqualTo(5)
    }

    private fun day(month: Int, day: Int) = MonthDay.of(month, day)
    private val halloweenDefaults = SeasonDates(day(10, 24), day(10, 31))

    /** Fails if a fresh file doesn't give each theme its own default dates. */
    @Test
    fun seasonDatesDefaultToEachThemes() = runTest {
        assertThat(settings.prefs.first().seasonDates.getValue(SeasonalTheme.HALLOWEEN)).isEqualTo(halloweenDefaults)
    }

    /** Fails if edited dates are lost or aren't read back. */
    @Test
    fun editedDatesAreKept() = runTest {
        val edited = SeasonDates(day(10, 20), day(11, 2))
        settings.setSeasonDates(SeasonalTheme.HALLOWEEN, edited)
        assertThat(settings.prefs.first().seasonDates.getValue(SeasonalTheme.HALLOWEEN)).isEqualTo(edited)
    }

    /** Fails if putting back the defaults leaves either key in the file. */
    @Test
    fun puttingBackTheDefaultsRemovesTheKeys() = runTest {
        settings.setSeasonDates(SeasonalTheme.HALLOWEEN, SeasonDates(day(10, 20), day(11, 2)))
        settings.resetSeasonDates(SeasonalTheme.HALLOWEEN)
        assertThat(settings.prefs.first().seasonDates.getValue(SeasonalTheme.HALLOWEEN)).isEqualTo(halloweenDefaults)
        assertThat(storedKeys()).containsNoneOf("season_halloween_start", "season_halloween_end")
    }

    private suspend fun storedKeys(): Set<String> = rawStore.data.first().asMap().keys.map { it.name }.toSet()

    private suspend fun seasonDatesAfterStoring(edit: (MutablePreferences) -> Unit): SeasonDates {
        rawStore.edit(edit)
        return settings.prefs.first().seasonDates.getValue(SeasonalTheme.HALLOWEEN)
    }

    /** Fails if an unreadable start (not a month and day) is used instead of falling back to the defaults. */
    @Test
    fun unreadableStartsFallBackToTheDefaults() = runTest {
        listOf(1332, 231, 0, -1, 1301, 100).forEach { bad ->
            val read = seasonDatesAfterStoring {
                it[intPreferencesKey("season_halloween_start")] = bad
                it[intPreferencesKey("season_halloween_end")] = 1105
            }
            assertThat(read).isEqualTo(halloweenDefaults)
        }
    }

    /** Fails if a key stored as text throws or is used. */
    @Test
    fun aStartStoredAsTextFallsBackToTheDefaults() = runTest {
        val read = seasonDatesAfterStoring {
            it[stringPreferencesKey("season_halloween_start")] = "1020"
            it[intPreferencesKey("season_halloween_end")] = 1105
        }
        assertThat(read).isEqualTo(halloweenDefaults)
    }

    /** Fails if a good start is kept beside an unreadable end. */
    @Test
    fun aGoodStartWithABadEndFallsBackToBothDefaults() = runTest {
        val read = seasonDatesAfterStoring {
            it[intPreferencesKey("season_halloween_start")] = 1020
            it[intPreferencesKey("season_halloween_end")] = 1332
        }
        assertThat(read).isEqualTo(halloweenDefaults)
    }

    /** Fails if one stored end is used without its start. */
    @Test
    fun onlyAnEndStoredFallsBackToTheDefaults() = runTest {
        val read = seasonDatesAfterStoring { it[intPreferencesKey("season_halloween_end")] = 1105 }
        assertThat(read).isEqualTo(halloweenDefaults)
    }

    /** Fails if 29 February is refused as a date. */
    @Test
    fun theLeapDayIsAValidDate() = runTest {
        val read = seasonDatesAfterStoring {
            it[intPreferencesKey("season_halloween_start")] = 229
            it[intPreferencesKey("season_halloween_end")] = 301
        }
        assertThat(read).isEqualTo(SeasonDates(day(2, 29), day(3, 1)))
    }
}
