package uk.co.siland.culvery.capability.door

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.id
import uk.co.siland.culvery.core.ui.SeasonalTheme

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
        val expected = DoorPrefs(true, 0.4f, FOLLOW_THEME_ID, false, LocalTime.of(22, 30), LocalTime.of(6, 30), NOW + CAPTURE_MS, NOW, 0)
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

    /** 14 §4.6: Follow the theme until the family picks a sound. */
    @Test
    fun theDoorbellFollowsTheThemeByDefault() = runTest {
        assertThat(settings.prefs.first().doorbell).isEqualTo(FOLLOW_THEME_ID)
    }

    @Test
    fun theDoorbellChoiceIsKept() = runTest {
        val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        doorSettings(folder.root, writer).setDoorbell("file:Test Honk.wav")
        writer.coroutineContext[Job]!!.cancelAndJoin()
        assertThat(settings.prefs.first().doorbell).isEqualTo("file:Test Honk.wav")
    }

    private suspend fun storedAs(value: String): String {
        val store = settingsDataStore(scope) { File(folder.root, "door_settings_$value.preferences_pb") }
        store.edit { it[stringPreferencesKey("doorbell_sound")] = value }
        return DoorSettings(store, scope).prefs.first().doorbell
    }

    /** 14 §4.6: an unknown stored value reads as Follow the theme. */
    @Test
    fun anUnknownDoorbellValueReadsAsFollowTheTheme() = runTest {
        assertThat(storedAs("KAZOO")).isEqualTo(FOLLOW_THEME_ID)
    }

    /** 18 §6.1: the choices Plan 14 stored keep meaning what they did. */
    @Test
    fun oldStoredChoicesKeepTheirMeaning() = runTest {
        assertThat(storedAs("FOLLOW_THEME")).isEqualTo("follow")
        assertThat(storedAs("CLASSIC")).isEqualTo("builtin:classic")
        assertThat(storedAs("ORGAN")).isEqualTo("builtin:organ")
    }

    /** 18 §6.2: with no Halloween file on the tablet, the theme's built-in sound. */
    @Test
    fun followTheThemeAtHalloweenIsTheOrgan() {
        val library = testSounds()
        assertThat(DoorPrefs().doorbellSound(library, SeasonalTheme.HALLOWEEN, birthday = false).id).isEqualTo(BuiltInSound.ORGAN.id)
        assertThat(DoorPrefs().doorbellSound(library, null, birthday = false).id).isEqualTo(BuiltInSound.CLASSIC.id)
        assertThat(DoorPrefs(doorbell = BuiltInSound.CHRISTMAS_MUSIC_BOX.id).doorbellSound(library, SeasonalTheme.HALLOWEEN, birthday = true).id)
            .isEqualTo(BuiltInSound.CHRISTMAS_MUSIC_BOX.id)
    }

    /** 18 §6.2: Happy Birthday is the birthday's sound, so a stored choice of it plays the classic chime. */
    @Test
    fun aStoredHappyBirthdayChoicePlaysTheClassicChime() {
        val prefs = DoorPrefs(doorbell = BuiltInSound.HAPPY_BIRTHDAY.id)
        assertThat(prefs.doorbellSound(testSounds(), null, birthday = false).id).isEqualTo(BuiltInSound.CLASSIC.id)
    }

    /** Regression guard: Follow the theme still plays Happy Birthday on a birthday. */
    @Test
    fun aBirthdayStillPlaysHappyBirthday() {
        val prefs = DoorPrefs(doorbell = FOLLOW_THEME_ID)
        assertThat(prefs.doorbellSound(testSounds(), null, birthday = true).id).isEqualTo(BuiltInSound.HAPPY_BIRTHDAY.id)
    }

    /** 15a §3.1: two days until the family picks more. */
    @Test
    fun keepDefaultsToTwoDays() = runTest {
        assertThat(settings.prefs.first().keep).isEqualTo(KeepDays.TWO)
        assertThat(settings.storedKeep()).isEqualTo(KeepDays.TWO)
    }

    @Test
    fun keepIsKept() = runTest {
        val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        doorSettings(folder.root, writer).setKeep(KeepDays.THIRTY)
        writer.coroutineContext[Job]!!.cancelAndJoin()
        assertThat(settings.prefs.first().keep).isEqualTo(KeepDays.THIRTY)
        assertThat(settings.storedKeep()).isEqualTo(KeepDays.THIRTY)
    }

    /** An unknown stored value reads as two days, as the other enums fall back. */
    @Test
    fun anUnknownKeepFallsBack() = runTest {
        val store = settingsDataStore(scope) { File(folder.root, "door_settings.preferences_pb") }
        store.edit { it[stringPreferencesKey("keep_days")] = "FOREVER" }
        val read = DoorSettings(store, scope)
        assertThat(read.prefs.first().keep).isEqualTo(KeepDays.TWO)
        assertThat(read.storedKeep()).isEqualTo(KeepDays.TWO)
    }

    /** A file of garbage bytes reads as thirty days, the longest history, not the two-day default that would cut it. */
    @Test
    fun aCorruptFileKeepsThirtyDays() = runTest {
        File(folder.root, "door_settings.preferences_pb").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertThat(settings.storedKeep()).isEqualTo(KeepDays.THIRTY)
    }

    /** An unreadable file gives no keep, not the default, so a prune can tell it from a choice of two days. */
    @Test
    fun anUnreadableFileHasNoStoredKeep() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unreadable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("unreadable")
        }
        assertThat(DoorSettings(broken, scope).storedKeep()).isNull()
    }

    @Test
    fun aVolumeOutOfRangeIsHeldToIt() = runTest {
        settings.setVolume(1.5f)
        assertThat(settings.prefs.first().volume).isEqualTo(1f)
    }
}
