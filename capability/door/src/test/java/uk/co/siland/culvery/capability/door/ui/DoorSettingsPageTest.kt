package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.door.CAPTURE_MS
import uk.co.siland.culvery.capability.door.DoorHealth
import uk.co.siland.culvery.capability.door.DoorPrefs
import uk.co.siland.culvery.capability.door.FakeChime
import uk.co.siland.culvery.capability.door.FakeDoorProvider
import uk.co.siland.culvery.capability.door.NOTIFICATION_ACCESS_MISSING
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.RecordingToaster
import uk.co.siland.culvery.capability.door.doorSettings
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 5 §5.4, §5.5: the chime, quiet hours, Ring's status and steps, and the capture switch. */
@RunWith(AndroidJUnit4::class)
class DoorSettingsPageTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val london = ZoneId.of("Europe/London")
    private val settings by lazy { doorSettings(folder.root, io) }
    private val provider = FakeDoorProvider(installed = true, access = false)
    private val chime = FakeChime()
    private val toaster = RecordingToaster()
    private val navigator = RecordingNavigator()
    private val clock = HouseholdClock(flowOf(london), WallClock { NOW }, io, flowOf(NOW))

    @After
    fun tearDown() = io.cancel()

    /** Robolectric's main looper runs only when the test idles it, so each check lets the UI and its effects run first. */
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(5_000) {
        compose.waitForIdle()
        condition()
    }

    private fun shows(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun prefs(): DoorPrefs = runBlocking { settings.prefs.first() }

    private fun showHost() {
        val health = DoorHealth(provider, settings, MutableStateFlow(true), MutableStateFlow(true), io)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) { DoorSettingsHost(settings, health, provider, chime, clock, flowOf(london), toaster) }
            }
        }
        waitFor { shows("settings_door") }
    }

    /** Fails if the app step isn't ticked when Ring is installed, or the access step is ticked while access is off. */
    @Test
    fun theStepsTickAsTheyAreDone() {
        showHost()
        waitFor { shows("door_step_app_done") }
        compose.onNodeWithTag("door_step_access_done", useUnmergedTree = true).assertDoesNotExist()
        compose.assertAccessible()
    }

    /** 5 §5.5 step 2 goes through the navigator, which asks the PIN and unpins. Fails if the step sends other intents or words, or none. */
    @Test
    fun allowNotificationAccessAsksForAndroidsScreen() {
        showHost()
        compose.onNodeWithTag("door_step_access").performClick()
        assertThat(navigator.systemScreens).containsExactly(
            listOf("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") to NOTIFICATION_ACCESS_MISSING,
        )
    }

    /** Fails if Play chime ignores the saved volume. */
    @Test
    fun playChimePlaysAtTheChosenVolume() {
        runBlocking { settings.setVolume(0.4f) }
        showHost()
        waitFor { compose.onAllNodesWithTag("door_volume").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("door_play_chime").performClick()
        assertThat(chime.volumes).containsExactly(0.4f)
    }

    /** Fails if the slider's value never reaches the settings. */
    @Test
    fun theVolumeIsSavedWhenTheSliderIsLetGo() {
        showHost()
        compose.onNodeWithTag("door_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
        waitFor { prefs().volume != 0.7f }
        assertThat(prefs().volume).isEqualTo(0.3f)
    }

    /** Half an hour at a time. Fails if Later moves quiet hours by anything but 30 minutes. */
    @Test
    fun quietHoursMoveHalfAnHourAtATime() {
        showHost()
        compose.onNodeWithTag("door_from_later").performClick()
        waitFor { prefs().quietFrom != LocalTime.of(21, 0) }
        assertThat(prefs().quietFrom).isEqualTo(LocalTime.of(21, 30))
    }

    /** 5 §5.4: on for 30 minutes; the recorder stops at the same moment. Fails if the switch saves another end time or doesn't show on. */
    @Test
    fun theCaptureSwitchRunsForThirtyMinutes() {
        showHost()
        compose.onNodeWithTag("door_capture").assertIsOff().performScrollTo().performClick()
        waitFor { prefs().captureUntilMillis != 0L }
        assertThat(prefs().captureUntilMillis).isEqualTo(NOW + CAPTURE_MS)
        waitFor { runCatching { compose.onNodeWithTag("door_capture").assertIsOn() }.isSuccess }
    }

    /** Fails if the line doesn't say "not yet" before a notice, or doesn't show the day and time after one. */
    @Test
    fun lastHeardSaysWhenOrNotYet() {
        showHost()
        compose.onNodeWithTag("door_last_heard").assertTextEquals("Culvery hasn’t heard from Ring yet.")
        runBlocking { settings.heard(NOW) }
        waitFor { runCatching { compose.onNodeWithTag("door_last_heard").assertTextEquals("Last heard from Ring: Wednesday 14:02") }.isSuccess }
    }
}
