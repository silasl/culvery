package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoorSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings by lazy { doorSettings(folder.root, scope) }

    @After
    fun tearDown() = scope.cancel()

    /** 5 §2.6, §4.1, §5.4: 100 %, quiet 21:00–07:00 on, the capture off, not connected. */
    @Test
    fun theDefaults() = runTest {
        assertThat(settings.prefs.first()).isEqualTo(
            DoorPrefs(connected = false, volume = 1.0f, quietOn = true, quietFrom = LocalTime.of(21, 0), quietUntil = LocalTime.of(7, 0),
                captureUntilMillis = 0L, lastHeardMillis = null, unrecognisedRun = 0),
        )
    }

    @Test
    fun eachSettingIsKeptAndReachesCurrent() = runTest {
        settings.setConnected(true)
        settings.setVolume(0.4f)
        settings.setQuietHours(on = false, from = LocalTime.of(22, 30), until = LocalTime.of(6, 30))
        settings.setCapture(untilMillis = NOW + CAPTURE_MS)
        settings.heard(NOW)
        val expected = DoorPrefs(true, 0.4f, false, LocalTime.of(22, 30), LocalTime.of(6, 30), NOW + CAPTURE_MS, NOW, 0)
        assertThat(settings.prefs.first()).isEqualTo(expected)
        // The last write's value reaching current is the signal; then all of it must match.
        settings.current.first { it.lastHeardMillis != null }
        assertThat(settings.current.value).isEqualTo(expected)
    }

    /** The run of unrecognised alerts (5 §6), ended by any recognised ring or motion. */
    @Test
    fun theRunOfUnrecognisedAlertsEndsAtARecognisedOne() = runTest {
        repeat(3) { settings.countAlert(recognised = false) }
        assertThat(settings.prefs.first().unrecognisedRun).isEqualTo(3)
        settings.countAlert(recognised = true)
        assertThat(settings.prefs.first().unrecognisedRun).isEqualTo(0)
    }

    /** The capture runs until its end time and not at it. */
    @Test
    fun theCaptureWindowEndsAtItsTime() {
        val prefs = DoorPrefs(captureUntilMillis = NOW + CAPTURE_MS)
        assertThat(prefs.capturingAt(NOW + CAPTURE_MS - 1)).isTrue()
        assertThat(prefs.capturingAt(NOW + CAPTURE_MS)).isFalse()
        assertThat(DoorPrefs().capturingAt(NOW)).isFalse()
    }

    /** A clock set back past the 30 minutes leaves the end time further off than a capture can run: off, not stretched. */
    @Test
    fun theCaptureIsOffWhenTheClockWentBackPastItsWindow() {
        val prefs = DoorPrefs(captureUntilMillis = NOW + CAPTURE_MS)
        assertThat(prefs.capturingAt(NOW)).isTrue()
        assertThat(prefs.capturingAt(NOW - 1)).isFalse()
    }

    @Test
    fun aVolumeOutOfRangeIsHeldToIt() = runTest {
        settings.setVolume(1.5f)
        assertThat(settings.prefs.first().volume).isEqualTo(1f)
    }
}
