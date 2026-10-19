package uk.co.siland.culvery.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalTime
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

@RunWith(AndroidJUnit4::class)
class DisplaySettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { displaySettings(folder.root, scope) }

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
        val fresh = DisplaySettings(displaySettingsStore(own) { File(folder.root, FILE_NAME) }, own)
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
}
