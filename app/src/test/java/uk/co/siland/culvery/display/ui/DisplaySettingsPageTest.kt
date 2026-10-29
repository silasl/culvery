package uk.co.siland.culvery.display.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
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
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.defaultSeasonDates
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.display.CameraAccess
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DisplayPrefs
import uk.co.siland.culvery.display.SeasonalThemes
import uk.co.siland.culvery.display.appDetailsIntents
import uk.co.siland.culvery.display.displaySettings
import uk.co.siland.culvery.shell.SunToday

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
    private val previews = mutableListOf<Pair<SeasonalTheme, Boolean>>()
    private val actions = DisplaySettingsActions(
        onNight = { from, until -> nights += from to until },
        onDay = { days += it },
        onEvening = { evenings += it },
        onSleepAfter = { sleeps += it },
        onWakeByCamera = { wakes += it },
        onAllowCamera = { allows++ },
        onOpenAndroidSettings = { opens++ },
        onPreview = { theme, on -> previews += theme to on },
    )

    @After
    fun tearDown() = io.cancel()

    private fun show(
        camera: CameraState = CameraState.ALLOWED,
        prefs: DisplayPrefs = DisplayPrefs(),
        sunKnown: Boolean = true,
        previewing: SeasonalTheme? = null,
    ) {
        compose.setContent {
            CulveryTheme(dark = true) { InSettingsPane { DisplaySettingsScreen(DisplaySettingsUi(prefs, camera, sunKnown, previewing), actions) } }
        }
    }

    private fun themes(now: MutableStateFlow<LocalDateTime?>) = SeasonalThemes(now, flowOf(defaultSeasonDates()), io)

    private fun shows(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun spoken(tag: String): String {
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        return config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription]
    }

    private fun access() = CameraAccess({ true }, { false }, displaySettings(folder.root, io), io)

    /** Fails if the page's id, name or place in Settings' list changes. */
    @Test
    fun itSitsBeforeMusic() {
        val page = DisplayPage(displaySettings(folder.root, io), access(), SunToday(null, io), RecordingToaster(), themes(MutableStateFlow(null)))
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

    /** Fails if a brightness slider is not the shared 69 dp row with its value on the right. */
    @Test
    fun theBrightnessRowsAreSliderRows() {
        show(prefs = DisplayPrefs(dayPercent = 40, eveningPercent = 25))
        listOf("display_day" to "40 %", "display_evening" to "25 %").forEach { (tag, value) ->
            assertThat(compose.onNodeWithTag("${tag}_row").getUnclippedBoundsInRoot().height.value).isWithin(1f).of(69f)
            compose.onNodeWithTag("${tag}_value", useUnmergedTree = true).assertTextEquals(value)
        }
        compose.onNodeWithTag("display_day").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.waitForIdle()
        assertThat(days.last()).isEqualTo(60)
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
                CulveryTheme(dark = true) { InSettingsPane { DisplayPage(settings, access, SunToday(null, io), RecordingToaster(), themes(MutableStateFlow(null))).Content() } }
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
        assertThat(navigator.systemScreens).containsExactly(
            listOf("android.settings.APPLICATION_DETAILS_SETTINGS") to
                "Culvery can’t open Android’s settings for Culvery. Allow the camera in Android’s Settings › Apps › Culvery › Permissions.",
        )
    }

    /** Fails if a theme has no preview switch, it starts on, or its line or section name differs. */
    @Test
    fun eachThemeHasAPreview() {
        show()
        compose.onNodeWithContentDescription("Seasonal themes").performScrollTo().assertExists()
        SeasonalTheme.entries.forEach { theme ->
            val tag = "display_preview_${theme.name.lowercase()}"
            compose.onNodeWithTag(tag).performScrollTo().assertIsOff()
            compose.onNodeWithText("Preview ${theme.label}").assertExists()
            compose.onNodeWithText("Shows ${theme.label} on the wall and plays its doorbell until midnight.").assertExists()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a running preview doesn't say when it ends. */
    @Test
    fun turningThePreviewOnSaysWhenItEnds() {
        show(previewing = SeasonalTheme.HALLOWEEN)
        compose.onNodeWithTag("display_preview_halloween").performScrollTo().assertIsOn()
        compose.onNodeWithText("Showing now. It ends at midnight.").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the switch hands on anything but the opposite of what it shows. */
    @Test
    fun theSwitchPreviews() {
        show()
        compose.onNodeWithTag("display_preview_halloween").performScrollTo().performClick()
        assertThat(previews).containsExactly(SeasonalTheme.HALLOWEEN to true)
    }

    /** Fails if an on switch turns the preview on again instead of off. */
    @Test
    fun anOnSwitchPreviewsOff() {
        show(previewing = SeasonalTheme.HALLOWEEN)
        compose.onNodeWithTag("display_preview_halloween").performScrollTo().performClick()
        assertThat(previews).containsExactly(SeasonalTheme.HALLOWEEN to false)
    }

    private fun showHost(now: MutableStateFlow<LocalDateTime?>): SeasonalThemes {
        val settings = displaySettings(folder.root, io)
        val themes = themes(now)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = true) {
                    InSettingsPane { DisplayPage(settings, CameraAccess({ true }, { false }, settings, io), SunToday(null, io), RecordingToaster(), themes).Content() }
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            shows("display_preview_halloween")
        }
        return themes
    }

    private fun waitUntilShowing(expected: SeasonalTheme?, themes: SeasonalThemes) = compose.waitUntil(5_000) {
        compose.waitForIdle()
        themes.showing.value == expected
    }

    /** Fails if the host doesn't send the switch to SeasonalThemes, or the switch doesn't follow it. */
    @Test
    fun theHostPreviewsThroughSeasonalThemes() {
        val themes = showHost(MutableStateFlow(LocalDateTime.of(2026, 11, 5, 10, 0)))
        assertThat(themes.showing.value).isNull()
        compose.onNodeWithTag("display_preview_halloween").performScrollTo().performClick()
        waitUntilShowing(SeasonalTheme.HALLOWEEN, themes)
        compose.onNodeWithTag("display_preview_halloween").assertIsOn()
        compose.onNodeWithTag("display_preview_halloween").performClick()
        waitUntilShowing(null, themes)
        compose.onNodeWithTag("display_preview_halloween").assertIsOff()
    }

    /** Fails if the switch stays on after the household's clock passes midnight. */
    @Test
    fun aPreviewEndingAtMidnightTurnsTheSwitchOff() {
        val now = MutableStateFlow<LocalDateTime?>(LocalDateTime.of(2026, 11, 5, 23, 59))
        val themes = showHost(now)
        compose.onNodeWithTag("display_preview_halloween").performScrollTo().performClick()
        waitUntilShowing(SeasonalTheme.HALLOWEEN, themes)
        compose.onNodeWithTag("display_preview_halloween").assertIsOn()
        now.value = LocalDateTime.of(2026, 11, 6, 0, 0)
        waitUntilShowing(null, themes)
        compose.onNodeWithTag("display_preview_halloween").assertIsOff()
    }
}
