package uk.co.siland.culvery.capability.door.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
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
import uk.co.siland.culvery.capability.door.FOLLOW_THEME_ID
import uk.co.siland.culvery.capability.door.FakeChime
import uk.co.siland.culvery.capability.door.FakeDoorProvider
import uk.co.siland.culvery.capability.door.KeepDays
import uk.co.siland.culvery.capability.door.NOTIFICATION_ACCESS_MISSING
import uk.co.siland.culvery.capability.door.NOW
import uk.co.siland.culvery.capability.door.RecordingToaster
import uk.co.siland.culvery.capability.door.TestAccess
import uk.co.siland.culvery.capability.door.clearTabletSounds
import uk.co.siland.culvery.capability.door.doorSettings
import uk.co.siland.culvery.capability.door.tabletSound
import uk.co.siland.culvery.capability.door.testSounds
import uk.co.siland.culvery.core.access.test.signedIn
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.id
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.SETTINGS_HOLD_MS
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

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
    private val access = TestAccess(Role.ADULT)
    private val clock = HouseholdClock(flowOf(london), WallClock { NOW }, io, flowOf(NOW))

    @After
    fun tearDown() {
        io.cancel()
        clearTabletSounds()
    }

    /** Robolectric's main looper runs only when the test idles it, so each check lets the UI and its effects run first. */
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(5_000) {
        compose.waitForIdle()
        condition()
    }

    private fun shows(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun prefs(): DoorPrefs = runBlocking { settings.prefs.first() }

    private fun showHost(showing: SeasonalTheme? = null) {
        val health = DoorHealth(provider, settings, MutableStateFlow(true), MutableStateFlow(true), io)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) {
                    InSettingsPane { DoorSettingsHost(settings, health, provider, chime, testSounds(), clock, flowOf(london), toaster, MutableStateFlow(showing), access) }
                }
            }
        }
        waitFor { shows("settings_door") }
    }

    /** Fails with "Vertically scrollable component was measured with an infinity maximum height" if the page nests a scroll of its own inside Settings'. */
    @Test
    fun thePageDrawsInsideSettingsScroll() {
        showHost()
        compose.onNodeWithTag("settings_door").assertExists()
        compose.onNodeWithTag("door_capture").assertExists()
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
        compose.onNodeWithTag("door_step_access").performScrollTo().performClick()
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

    /** 14 §4.6: the first section, before Chime; Follow the theme is chosen until another is. */
    @Test
    fun theDoorbellSoundRows() {
        showHost()
        val sound = compose.onNodeWithContentDescription("Doorbell sound").getBoundsInRoot()
        val chimeSection = compose.onNodeWithContentDescription("Chime").getBoundsInRoot()
        assertThat(sound.top).isLessThan(chimeSection.top)
        compose.onNodeWithTag("door_doorbell_follow").assertIsSelected().assertTextContains("Follow the theme")
        compose.onNodeWithTag("door_doorbell_follow_note", useUnmergedTree = true)
            .assertTextEquals("The season’s sound while a seasonal theme shows, the classic chime otherwise.")
        compose.onNodeWithTag("door_doorbell_builtin:classic").assertIsNotSelected().assertTextContains("Classic chime")
        compose.onNodeWithTag("door_doorbell_builtin:organ").assertIsNotSelected().assertTextContains("Haunted organ")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 18 §6.1: Follow the theme first, then every sound but Happy Birthday, the tablet's marked as such. */
    @Test
    fun everyChoiceIsOffered() {
        tabletSound("Test Honk.wav")
        showHost()
        waitFor { shows("door_doorbell_file:Test Honk.wav") }
        val follow = compose.onNodeWithTag("door_doorbell_follow").getBoundsInRoot().top
        val offered = listOf(BuiltInSound.CLASSIC, BuiltInSound.ORGAN, BuiltInSound.SAD_TROMBONE, BuiltInSound.CHRISTMAS_BELLS).map { sound ->
            compose.onNodeWithTag("door_doorbell_${sound.id}").assertIsNotSelected().assertTextContains(sound.label)
            compose.onNodeWithTag("door_doorbell_${sound.id}_note", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("door_doorbell_${sound.id}").getBoundsInRoot().top
        }
        val honk = compose.onNodeWithTag("door_doorbell_file:Test Honk.wav").getBoundsInRoot().top
        assertThat(listOf(follow) + offered + honk).isInStrictOrder()
        compose.onNodeWithTag("door_doorbell_file:Test Honk.wav").assertTextContains("Test Honk")
        compose.onNodeWithTag("door_doorbell_file:Test Honk.wav_note", useUnmergedTree = true).assertTextEquals("On this tablet")
        assertThat(shows("door_doorbell_${BuiltInSound.HAPPY_BIRTHDAY.id}")).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 18 §6.1: a chosen file that has gone stays chosen, named from its file, says so, and plays the classic chime. */
    @Test
    fun aGoneChoiceSaysSo() {
        runBlocking { settings.setDoorbell("file:halloween/Gone Howl.ogg") }
        showHost()
        waitFor { shows("door_doorbell_file:halloween/Gone Howl.ogg") }
        compose.onNodeWithTag("door_doorbell_file:halloween/Gone Howl.ogg").assertIsSelected().assertTextContains("Gone Howl")
        compose.onNodeWithTag("door_doorbell_file:halloween/Gone Howl.ogg_note", useUnmergedTree = true).assertTextEquals("Not on this tablet any more")
        compose.onNodeWithTag("door_doorbell_follow").assertIsNotSelected()
        compose.onNodeWithTag("door_play_chime").performClick()
        assertThat(chime.plays).containsExactly(BuiltInSound.CLASSIC.id to 1.0f)
    }

    @Test
    fun choosingASoundSavesIt() {
        showHost()
        compose.onNodeWithTag("door_doorbell_builtin:organ").performClick()
        waitFor { prefs().doorbell == BuiltInSound.ORGAN.id }
        waitFor { runCatching { compose.onNodeWithTag("door_doorbell_builtin:organ").assertIsSelected() }.isSuccess }
    }

    /** The preview button plays what a ring would: the showing theme's sound under Follow the theme, else the choice. */
    @Test
    fun playChimePlaysWhatARingWould() {
        showHost(SeasonalTheme.HALLOWEEN)
        compose.onNodeWithTag("door_play_chime").performClick()
        assertThat(chime.plays).containsExactly(BuiltInSound.ORGAN.id to 1.0f)
        compose.onNodeWithTag("door_doorbell_builtin:classic").performClick()
        waitFor { prefs().doorbell == BuiltInSound.CLASSIC.id }
        waitFor { runCatching { compose.onNodeWithTag("door_doorbell_builtin:classic").assertIsSelected() }.isSuccess }
        compose.onNodeWithTag("door_play_chime").performClick()
        assertThat(chime.plays.last()).isEqualTo(BuiltInSound.CLASSIC.id to 1.0f)
    }

    private fun spokenVolume(): String {
        val config = compose.onNodeWithTag("door_volume").fetchSemanticsNode().config
        return config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription]
    }

    private var stored by mutableFloatStateOf(1.0f)

    private fun showScreen(volume: Float, onVolume: (Float) -> Unit) {
        stored = volume
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                InSettingsPane {
                    DoorSettingsScreen(
                        DoorSettingsUi("Ring", null, stored, FOLLOW_THEME_ID, emptyList(), false, LocalTime.of(21, 0), LocalTime.of(7, 0), null, "", true, true, false, KeepDays.TWO),
                        DoorSettingsActions(onVolume, {}, {}, { _, _, _ -> }, {}, {}, {}, {}, {}),
                    )
                }
            }
        }
    }

    /** Runs the virtual clock to [ms] since the start, drawing every frame on the way. */
    private fun at(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms - compose.mainClock.currentTime)
        compose.waitForIdle()
    }

    private fun letGoAt(percent: Float): Long {
        compose.onNodeWithTag("door_volume").performSemanticsAction(SemanticsActions.SetProgress) { it(percent) }
        at(compose.mainClock.currentTime + 16)
        return compose.mainClock.currentTime
    }

    /** Review Focus 5: tens, spoken with the name, saved once on let go, in the 69 dp row. Fails on a free Float slider. */
    @Test
    fun theVolumeStepsInTensAndSavesOnLetGo() {
        val saved = mutableListOf<Float>()
        showScreen(0.3f) { saved += it }
        at(16)
        assertThat(spokenVolume()).isEqualTo("Chime volume, 30 percent")
        assertThat(compose.onNodeWithTag("door_volume_row").getUnclippedBoundsInRoot().height.value).isWithin(1f).of(69f)
        // Moving to 30 from the stored 100 is a change; setting the value it already has saves nothing.
        stored = 1.0f
        at(32)
        assertThat(compose.onNodeWithTag("door_volume").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps).isEqualTo(9)
        letGoAt(30f)
        assertThat(saved).containsExactly(0.3f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Review Focus 5: the store refuses, so the slider goes back to the stored volume once the hold ends. */
    @Test
    fun aFailedSaveReturnsTheSliderToTheStoredVolume() {
        showScreen(1.0f) {}
        at(16)
        val start = letGoAt(30f)
        assertThat(spokenVolume()).isEqualTo("Chime volume, 30 percent")
        at(start + SETTINGS_HOLD_MS - 16)
        assertThat(spokenVolume()).isEqualTo("Chime volume, 30 percent")
        at(start + SETTINGS_HOLD_MS + 16)
        assertThat(spokenVolume()).isEqualTo("Chime volume, 100 percent")
        compose.assertClickablesHaveRoleAndLabel()
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

    private fun chooseKeep(days: Int) = compose.onNodeWithTag("door_keep_$days").performScrollTo().performClick()

    /** 15a §3.1: 2, 7 and 30 days, two chosen to start; a longer keep saves with no PIN. */
    @Test
    fun keepRowsChooseAndSave() {
        showHost()
        compose.onNodeWithTag("door_keep_2").assertIsSelected().assertTextContains("2 days")
        compose.onNodeWithTag("door_keep_7").assertIsNotSelected().assertTextContains("7 days")
        compose.onNodeWithTag("door_keep_30").assertIsNotSelected().assertTextContains("30 days")
        compose.onNodeWithContentDescription("Keep door history for").assertExists()
        chooseKeep(7)
        waitFor { prefs().keep == KeepDays.SEVEN }
        waitFor { runCatching { compose.onNodeWithTag("door_keep_7").assertIsSelected() }.isSuccess }
        chooseKeep(30)
        waitFor { prefs().keep == KeepDays.THIRTY }
        assertThat(access.asked).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 15a §3.2: a shorter keep deletes events, so it asks first, then for a fresh PIN of an Adult or Admin, whoever is signed in. */
    @Test
    fun aShorterKeepConfirmsThenAsksAFreshPin() {
        runBlocking { settings.setKeep(KeepDays.THIRTY) }
        access.session.value = signedIn(Role.ADMIN)
        showHost()
        waitFor { runCatching { compose.onNodeWithTag("door_keep_30").assertIsSelected() }.isSuccess }
        chooseKeep(2)
        waitFor { shows("door_keep_confirm") }
        compose.onNodeWithText("Delete door events older than 2 days?").assertExists()
        compose.onNodeWithTag("door_keep_30").assertIsSelected()
        assertThat(access.asked).isEmpty()

        compose.onNodeWithTag("door_keep_as_is").performScrollTo().performClick()
        waitFor { !shows("door_keep_confirm") }
        assertThat(prefs().keep).isEqualTo(KeepDays.THIRTY)
        assertThat(access.asked).isEmpty()

        chooseKeep(2)
        waitFor { shows("door_keep_confirm") }
        access.role = null
        compose.onNodeWithTag("door_keep_delete").performScrollTo().performClick()
        waitFor { access.asked.size == 1 }
        compose.waitForIdle()
        assertThat(prefs().keep).isEqualTo(KeepDays.THIRTY)
        assertThat(shows("door_keep_confirm")).isTrue()

        access.role = Role.ADULT
        compose.onNodeWithTag("door_keep_delete").performScrollTo().performClick()
        waitFor { prefs().keep == KeepDays.TWO }
        waitFor { !shows("door_keep_confirm") }
        assertThat(access.asked).containsExactly(listOf("door.clear"), listOf("door.clear"))
    }

    /** A Child's PIN is refused by the permission, so the shorter keep isn't saved. */
    @Test
    fun aChildCantShorten() {
        runBlocking { settings.setKeep(KeepDays.SEVEN) }
        showHost()
        waitFor { runCatching { compose.onNodeWithTag("door_keep_7").assertIsSelected() }.isSuccess }
        access.role = Role.CHILD
        chooseKeep(2)
        waitFor { shows("door_keep_confirm") }
        compose.onNodeWithTag("door_keep_delete").performScrollTo().performClick()
        waitFor { access.asked.size == 1 }
        compose.waitForIdle()
        assertThat(prefs().keep).isEqualTo(KeepDays.SEVEN)
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
