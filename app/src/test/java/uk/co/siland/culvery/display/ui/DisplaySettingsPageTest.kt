package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
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
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonDates
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
    private val dated = mutableListOf<Pair<SeasonalTheme, SeasonDates?>>()
    private val overlay = TestOverlay()
    private val actions = DisplaySettingsActions(
        onNight = { from, until -> nights += from to until },
        onDay = { days += it },
        onEvening = { evenings += it },
        onSleepAfter = { sleeps += it },
        onWakeByCamera = { wakes += it },
        onAllowCamera = { allows++ },
        onOpenAndroidSettings = { opens++ },
        onPreview = { theme, on -> previews += theme to on },
        onDates = { theme, dates -> dated += theme to dates },
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 15)
    }

    @After
    fun tearDown() = io.cancel()

    private fun show(
        camera: CameraState = CameraState.ALLOWED,
        prefs: DisplayPrefs = DisplayPrefs(),
        sunKnown: Boolean = true,
        previewing: SeasonalTheme? = null,
        dates: Map<SeasonalTheme, SeasonDates> = defaultSeasonDates(),
        next: Pair<SeasonalTheme, LocalDate>? = null,
        today: LocalDate = TODAY,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) {
                    Box {
                        InSettingsPane { DisplaySettingsScreen(DisplaySettingsUi(prefs, camera, sunKnown, today, previewing, dates, next), actions) }
                        overlay.content?.invoke()
                    }
                }
            }
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
            CompositionLocalProvider(LocalShellNavigator provides navigator, LocalOverlayHost provides overlay) {
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
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalOverlayHost provides overlay) {
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

    private fun day(month: Int, day: Int) = compose.onNodeWithTag("day_${LocalDate.of(2026, month, day)}")

    private fun openHalloweenDates(dates: SeasonDates = SeasonalTheme.HALLOWEEN.defaultDates, today: LocalDate = TODAY) {
        show(dates = mapOf(SeasonalTheme.HALLOWEEN to dates), today = today)
        compose.onNodeWithTag("display_dates_halloween").performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Fails if the refusal for a last day before the first, or a year or more after it, differs or shows a needless year. */
    @Test
    fun checkRangeNamesWhatToDo() {
        assertThat(checkRange(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 31))).isNull()
        assertThat(checkRange(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 24)))
            .isEqualTo("The last day is before the first. Tap a last day on or after 31 Oct.")
        assertThat(checkRange(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 2))).isNull()
        assertThat(checkRange(LocalDate.of(2026, 10, 24), LocalDate.of(2027, 10, 24)))
            .isEqualTo("A theme can show for a year at most. Tap a last day before 24 Oct 2027.")
    }

    /** Fails if the sheet opens on the wrong run: the one holding today, else the next to start; a wrapped end lands in the new year. */
    @Test
    fun shownRangeIsTheRunHoldingTodayElseTheNext() {
        val halloween = SeasonalTheme.HALLOWEEN.defaultDates
        assertThat(shownRange(LocalDate.of(2026, 10, 30), halloween)).isEqualTo(LocalDate.of(2026, 10, 24) to LocalDate.of(2026, 10, 31))
        assertThat(shownRange(LocalDate.of(2026, 11, 1), halloween)).isEqualTo(LocalDate.of(2027, 10, 24) to LocalDate.of(2027, 10, 31))
        assertThat(shownRange(LocalDate.of(2026, 9, 15), halloween)).isEqualTo(LocalDate.of(2026, 10, 24) to LocalDate.of(2026, 10, 31))
        val christmas = SeasonDates(MonthDay.of(12, 20), MonthDay.of(1, 2))
        assertThat(shownRange(LocalDate.of(2027, 1, 1), christmas)).isEqualTo(LocalDate.of(2026, 12, 20) to LocalDate.of(2027, 1, 2))
        assertThat(shownRange(LocalDate.of(2026, 12, 25), christmas)).isEqualTo(LocalDate.of(2026, 12, 20) to LocalDate.of(2027, 1, 2))
        assertThat(shownRange(LocalDate.of(2026, 6, 1), christmas)).isEqualTo(LocalDate.of(2026, 12, 20) to LocalDate.of(2027, 1, 2))
    }

    /** Fails if a 29 Feb end in a year without one isn't the 28th. */
    @Test
    fun shownRangeKeepsALeapDayEndInANonLeapYear() {
        val february = SeasonDates(MonthDay.of(2, 1), MonthDay.of(2, 29))
        assertThat(shownRange(LocalDate.of(2027, 2, 10), february)).isEqualTo(LocalDate.of(2027, 2, 1) to LocalDate.of(2027, 2, 28))
        assertThat(shownRange(LocalDate.of(2028, 2, 10), february)).isEqualTo(LocalDate.of(2028, 2, 1) to LocalDate.of(2028, 2, 29))
    }

    /** Fails if, during the dates, the sheet doesn't open on that month with the saved first day, last day and the days between shown as picked. */
    @Test
    fun theSheetOpensOnTheSavedDatesPicked() {
        openHalloweenDates(today = LocalDate.of(2026, 10, 30))
        compose.onNodeWithTag("month_title").assertTextEquals("October 2026")
        day(10, 24).assertIsSelected()
        day(10, 31).assertIsSelected()
        day(10, 27).assertIsNotSelected()
        day(10, 24).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "first day"))
        day(10, 27).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "in the range"))
        day(10, 31).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "last day"))
        compose.onNodeWithTag("display_dates_save").assertIsEnabled()
    }

    /** Fails if, outside the dates, the sheet doesn't open on the next run's month. */
    @Test
    fun outsideTheDatesTheSheetOpensOnTheNextRun() {
        openHalloweenDates(today = LocalDate.of(2026, 11, 1))
        compose.onNodeWithTag("month_title").assertTextEquals("October 2027")
    }

    /** Fails if a tap on an opened sheet doesn't start a fresh pick. */
    @Test
    fun aTapAfterTheSavedRangeStartsAFreshPick() {
        openHalloweenDates(today = LocalDate.of(2026, 10, 30))
        day(10, 26).performClick()
        day(10, 24).assertIsNotSelected()
        day(10, 26).assertIsSelected()
        compose.onNodeWithTag("display_dates_save").assertIsNotEnabled()
    }

    /** Fails if a theme's row doesn't name it and its dates, or its click doesn't say what it does. */
    @Test
    fun eachThemeShowsItsDates() {
        show()
        compose.onNodeWithTag("display_dates_halloween").performScrollTo().assertTextEquals("Halloween", "24 Oct – 31 Oct")
        val click = compose.onNodeWithTag("display_dates_halloween").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(click.label).isEqualTo("Change the dates")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if two taps don't hand on the two days, or the sheet stays open. */
    @Test
    fun pickingTwoDaysSavesThem() {
        openHalloweenDates()
        compose.onNodeWithText("Halloween dates").assertExists()
        compose.onNodeWithText("Tap the first day, then the last.").assertExists()
        day(10, 20).performClick()
        compose.onNodeWithTag("month_next").performClick()
        day(11, 2).performClick()
        compose.onNodeWithTag("display_dates_save").assertIsEnabled().performClick()
        assertThat(dated).containsExactly(SeasonalTheme.HALLOWEEN to SeasonDates(MonthDay.of(10, 20), MonthDay.of(11, 2)))
        compose.waitForIdle()
        assertThat(shows("display_dates_sheet")).isFalse()
    }

    /** Fails if a last day before the first is accepted, goes unexplained, or can be saved. */
    @Test
    fun aLastDayBeforeTheFirstIsRefused() {
        openHalloweenDates()
        day(10, 31).performClick()
        day(10, 24).performClick()
        compose.onNodeWithTag("display_dates_refused").assertTextEquals("The last day is before the first. Tap a last day on or after 31 Oct.")
        compose.onNodeWithTag("display_dates_save").assertIsNotEnabled()
        assertThat(dated).isEmpty()
    }

    /** Fails if, after a refusal, the next tap doesn't try another last day for the same first day. */
    @Test
    fun afterARefusalTheNextTapIsAnotherLastDay() {
        openHalloweenDates()
        day(10, 31).performClick()
        day(10, 24).performClick()
        compose.onNodeWithTag("month_next").performClick()
        day(11, 3).performClick()
        compose.onNodeWithTag("display_dates_refused").assertTextEquals("")
        compose.onNodeWithTag("display_dates_save").performClick()
        assertThat(dated).containsExactly(SeasonalTheme.HALLOWEEN to SeasonDates(MonthDay.of(10, 31), MonthDay.of(11, 3)))
    }

    /** Fails if Save works before both days are picked. */
    @Test
    fun saveWaitsForTwoDays() {
        openHalloweenDates()
        day(10, 20).performClick()
        compose.onNodeWithTag("display_dates_save").assertIsNotEnabled()
    }

    /** Fails if the default dates can't be put back, or the button doesn't name them. */
    @Test
    fun puttingBackTheDefaults() {
        openHalloweenDates(SeasonDates(MonthDay.of(10, 20), MonthDay.of(11, 2)))
        compose.onNodeWithTag("display_dates_reset").assertTextEquals("Put back 24 Oct – 31 Oct").performClick()
        assertThat(dated).containsExactly(SeasonalTheme.HALLOWEEN to null)
        compose.waitForIdle()
        assertThat(shows("display_dates_sheet")).isFalse()
    }

    /** Fails if Cancel saves anything or leaves the sheet open. */
    @Test
    fun cancelSavesNothing() {
        openHalloweenDates()
        day(10, 20).performClick()
        day(10, 22).performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertThat(dated).isEmpty()
        compose.waitForIdle()
        assertThat(shows("display_dates_sheet")).isFalse()
    }

    /** Fails if the Next line reads differently, or shows when nothing is next. */
    @Test
    fun theNextLineShowsOutsideTheDates() {
        show(next = SeasonalTheme.HALLOWEEN to LocalDate.of(2027, 10, 24))
        compose.onNodeWithTag("display_next").performScrollTo().assertTextEquals("Next: Halloween from 24 October")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: no Next line while a theme is in its dates. */
    @Test
    fun withoutANextThereIsNoNextLine() {
        show(next = null)
        assertThat(shows("display_next")).isFalse()
    }
}
