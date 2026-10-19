package uk.co.siland.culvery.display.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.display.CANT_OPEN_ANDROIDS_SETTINGS
import uk.co.siland.culvery.display.CameraAccess
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DisplayPrefs
import uk.co.siland.culvery.display.appDetailsIntents
import uk.co.siland.culvery.display.displaySettings

/** 8 §4.3, §4.4, §4.6: Settings › Display, with what each control hands on. */
@RunWith(AndroidJUnit4::class)
class DisplaySettingsPageTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val nights = mutableListOf<Pair<LocalTime, LocalTime>>()
    private val days = mutableListOf<Int>()
    private val evenings = mutableListOf<Int>()
    private val sleeps = mutableListOf<Int>()
    private val wakes = mutableListOf<Boolean>()
    private var allows = 0
    private var opens = 0
    private val actions = DisplaySettingsActions(
        onNight = { from, until -> nights += from to until },
        onDay = { days += it },
        onEvening = { evenings += it },
        onSleepAfter = { sleeps += it },
        onWakeByCamera = { wakes += it },
        onAllowCamera = { allows++ },
        onOpenAndroidSettings = { opens++ },
    )

    @After
    fun tearDown() = io.cancel()

    private fun show(camera: CameraState = CameraState.ALLOWED, prefs: DisplayPrefs = DisplayPrefs(), sunKnown: Boolean = true) {
        compose.setContent {
            CulveryTheme(dark = true) { InSettingsPane { DisplaySettingsScreen(DisplaySettingsUi(prefs, camera, sunKnown), actions) } }
        }
    }

    private fun shows(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun spoken(tag: String): String {
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        return config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription]
    }

    private fun access() = CameraAccess({ true }, { false }, displaySettings(folder.root, io), io)

    /** Fails if the page's id, name or place in Settings' list changes. */
    @Test
    fun itSitsBeforeMusic() {
        val page = DisplayPage(displaySettings(folder.root, io), access(), Optional.empty(), RecordingToaster())
        assertThat(page.id).isEqualTo("display")
        assertThat(page.id).isEqualTo(DISPLAY_PAGE_ID)
        assertThat(page.title).isEqualTo("Display")
        assertThat(page.order).isEqualTo(440)
    }

    /** Fails if Later moves the hour by anything but 30 minutes, moves the wrong end, or the line doesn't follow the hours. */
    @Test
    fun theNightClockTimesStepByHalfAnHour() {
        show(prefs = DisplayPrefs(nightFrom = LocalTime.of(22, 0), nightUntil = LocalTime.of(7, 0)))
        compose.onNodeWithTag("display_night_line").assertTextEquals(
            "A dim clock shows from 22:00 until 07:00. Only a tap wakes the screen, and rings wait in the Door tab.",
        )
        compose.onNodeWithTag("display_from_later").performClick()
        compose.onNodeWithTag("display_until_earlier").performClick()
        assertThat(nights).containsExactly(LocalTime.of(22, 30) to LocalTime.of(7, 0), LocalTime.of(22, 0) to LocalTime.of(6, 30)).inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a slider is spoken by anything but its name and percent, or saves a different value or the other level. */
    @Test
    fun theBrightnessSlidersSaveWhenLetGo() {
        show(prefs = DisplayPrefs(dayPercent = 70, eveningPercent = 25))
        assertThat(spoken("display_day")).isEqualTo("Daytime brightness, 70 percent")
        assertThat(spoken("display_evening")).isEqualTo("Evening brightness, 25 percent")
        compose.onNodeWithTag("display_day").performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        compose.waitForIdle()
        assertThat(days).isNotEmpty()
        assertThat(days.last()).isEqualTo(40)
        assertThat(evenings).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the line is missing without sun times, or shown when they are known. */
    @Test
    fun withoutSunTimesItSaysTheDaytimeLevelApplies() {
        show(sunKnown = false)
        compose.onNodeWithTag("display_no_sun").assertTextEquals("Until the home location is set, the daytime level applies all day.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: the line belongs to the missing sun times alone. */
    @Test
    fun withSunTimesThereIsNoNoSunLine() {
        show(sunKnown = true)
        assertThat(shows("display_no_sun")).isFalse()
    }

    /** Fails if there aren't four chips, the saved delay isn't the selected one, or a chip saves another number. */
    @Test
    fun theSleepDelayChoices() {
        show(prefs = DisplayPrefs(sleepAfterMinutes = 5))
        listOf(2, 5, 10, 15).forEach { assertThat(shows("display_sleep_$it")).isTrue() }
        compose.onNodeWithTag("display_sleep_5").assertIsSelected()
        compose.onNodeWithText("10 minutes").performScrollTo().performClick()
        assertThat(sleeps).containsExactly(10)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the switch isn't on while untouched, or toggling sends anything but off. */
    @Test
    fun allowedShowsTheSwitchOnByDefault() {
        show(camera = CameraState.ALLOWED, prefs = DisplayPrefs(wakeByCamera = null))
        compose.onNodeWithTag("display_camera").performScrollTo().assertIsOn()
        compose.onNodeWithText("The front camera looks for movement about once a second. Nothing is recorded or sent.").assertExists()
        compose.onNodeWithTag("display_camera").performClick()
        assertThat(wakes).containsExactly(false)
        assertThat(shows("display_allow_camera")).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a saved off shows as on. */
    @Test
    fun aSavedOffShowsOff() {
        show(camera = CameraState.ALLOWED, prefs = DisplayPrefs(wakeByCamera = false))
        compose.onNodeWithTag("display_camera").performScrollTo().assertIsOff()
    }

    /** Fails if a switch shows before the camera is allowed, or the button doesn't ask. */
    @Test
    fun notAskedOffersAllowTheCamera() {
        show(camera = CameraState.NOT_ASKED)
        assertThat(shows("display_camera")).isFalse()
        compose.onNodeWithText("Walking up can wake the screen once Culvery may use the camera.").performScrollTo().assertExists()
        compose.onNodeWithText("Allow the camera").performScrollTo().performClick()
        assertThat(allows).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the refusal doesn't say how to allow it, or its button doesn't open Android's settings. */
    @Test
    fun aRefusalSaysHowToAllowIt() {
        show(camera = CameraState.REFUSED)
        compose.onNodeWithText("The camera isn’t allowed, so only a tap wakes the screen. Allow it in Android’s settings for Culvery.").performScrollTo().assertExists()
        assertThat(shows("display_camera")).isFalse()
        assertThat(shows("display_allow_camera")).isFalse()
        compose.onNodeWithText("Open Android’s settings for Culvery").performScrollTo().performClick()
        assertThat(opens).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tablet without a front camera gets a switch or a button, or isn't told a tap wakes it. */
    @Test
    fun noFrontCameraSaysATapWakes() {
        show(camera = CameraState.NO_CAMERA)
        compose.onNodeWithText("This tablet has no front camera, so a tap wakes the screen.").performScrollTo().assertExists()
        assertThat(shows("display_camera")).isFalse()
        assertThat(shows("display_allow_camera")).isFalse()
        assertThat(shows("display_open_settings")).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the intent isn't Android's details screen for the package. */
    @Test
    fun theAppDetailsIntentNamesThePackage() {
        val intent = appDetailsIntents("uk.co.siland.culvery").single()
        assertThat(intent.action).isEqualTo("android.settings.APPLICATION_DETAILS_SETTINGS")
        assertThat(intent.data.toString()).isEqualTo("package:uk.co.siland.culvery")
    }

    /** Fails if the page draws before the saved settings are known (the prompt would flash after a restart), or its buttons miss the access and the navigator. */
    @Test
    fun theHostWaitsForTheSavedSettingsAndWiresTheButtons() {
        val settings = displaySettings(folder.root, io)
        val access = CameraAccess({ true }, { false }, settings, io)
        val navigator = RecordingNavigator()
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) { InSettingsPane { DisplayPage(settings, access, Optional.empty(), RecordingToaster()).Content() } }
            }
        }
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            shows("settings_display")
        }
        compose.onNodeWithTag("display_allow_camera").performScrollTo().performClick()
        assertThat(runBlocking { access.requests.first() }).isEqualTo(Unit)
        runBlocking { settings.setCameraAsked() }
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            shows("display_open_settings")
        }
        compose.onNodeWithTag("display_open_settings").performScrollTo().performClick()
        assertThat(navigator.systemScreens).containsExactly(listOf("android.settings.APPLICATION_DETAILS_SETTINGS") to CANT_OPEN_ANDROIDS_SETTINGS)
    }
}
