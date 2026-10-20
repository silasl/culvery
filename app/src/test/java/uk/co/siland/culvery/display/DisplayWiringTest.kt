package uk.co.siland.culvery.display

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.multibindings.IntoSet
import java.lang.reflect.Method
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.NightHours
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.display.camera.CAMERA_RETRY_MS
import uk.co.siland.culvery.display.camera.Camera2Frames
import uk.co.siland.culvery.display.camera.CameraModule
import uk.co.siland.culvery.display.camera.CameraStopped
import uk.co.siland.culvery.display.camera.FrameSource
import uk.co.siland.culvery.display.camera.Luma
import uk.co.siland.culvery.display.camera.Presence
import uk.co.siland.culvery.display.di.DisplayModule
import uk.co.siland.culvery.display.ui.DisplayPage
import uk.co.siland.culvery.display.ui.SleepLayer
import uk.co.siland.culvery.display.ui.THE_CAMERA_IS_ON
import uk.co.siland.culvery.holding
import uk.co.siland.culvery.overlayCameraMark
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ui.ShellLayers

/** A camera the test opens and fails by hand. */
private class OpenOrNot : FrameSource {
    var isOpen = false
    private var onFailed: (Exception) -> Unit = {}

    override fun open(onFrame: (read: () -> Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable {
        isOpen = true
        this.onFailed = onFailed
        return AutoCloseable { isOpen = false }
    }

    fun fail() = onFailed(CameraStopped())
}

/** 8 §4.1, §4.3: what holds the screen, the camera prompt's way back to pinning, the camera mark and the bindings. */
@RunWith(AndroidJUnit4::class)
class DisplayWiringTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    /** Fails if any of the PIN pad, a sheet, the door card, Settings or the wizard lets the wall sleep, or if nothing shown, setup still being read included, holds it. */
    @Test
    fun eachShownThingHoldsTheScreen() {
        fun held(pin: Boolean = false, sheet: Boolean = false, alert: Boolean = false, settings: Boolean = false, complete: Boolean? = true) =
            holding(pinShowing = pin, sheetShowing = sheet, alertShowing = alert, settingsOpen = settings, complete = complete)
        assertThat(held()).isFalse()
        assertThat(held(pin = true)).isTrue()
        assertThat(held(sheet = true)).isTrue()
        assertThat(held(alert = true)).isTrue()
        assertThat(held(settings = true)).isTrue()
        assertThat(held(complete = false)).isTrue()
        assertThat(held(complete = null)).isFalse()
    }

    /** Fails if setup still being read at a restart in the night wakes the clock and keeps the wall awake for a minute. */
    @Test
    fun aRestartAtNightKeepsTheClockWhileSetupIsRead() = runTest {
        val display = DisplayState(
            MutableStateFlow(DisplayPrefs()),
            MutableStateFlow(LocalDateTime.of(2026, 10, 19, 23, 0)),
            MutableStateFlow(0L),
            MutableStateFlow(true),
            MutableStateFlow(CameraState.ALLOWED),
            backgroundScope,
        )
        runCurrent()
        assertThat(display.screen.value).isEqualTo(Screen.NIGHT_CLOCK)
        display.held(holding(pinShowing = false, sheetShowing = false, alertShowing = false, settingsOpen = false, complete = null))
        runCurrent()
        assertThat(display.screen.value).isEqualTo(Screen.NIGHT_CLOCK)
        advanceTimeBy(NIGHT_AWAKE_MS)
        runCurrent()
        assertThat(display.screen.value).isEqualTo(Screen.NIGHT_CLOCK)
    }

    /** Fails if the prompt doesn't go through the system screen's unpinning with its own missing line, or if the answer doesn't save the ask before pinning again exactly once. */
    @Test
    fun thePinComesBackAfterTheCameraAnswer() = runTest {
        val settings = displaySettings(folder.root, scope)
        val access = CameraAccess({ true }, { false }, settings, scope)
        val missing = mutableListOf<String>()
        var launched = 0
        var askedWhenPinned: Boolean? = null
        var pins = 0
        val prompt = CameraPrompt(
            access,
            openSystemScreen = { line, launch ->
                missing += line
                launch()
            },
            returnToPinning = {
                askedWhenPinned = runBlocking { settings.prefs.first().cameraAsked }
                pins++
            },
        )

        prompt.request { launched++ }
        assertThat(missing).containsExactly(
            "Culvery can’t ask for the camera on this tablet. Allow the camera in Android’s Settings › Apps › Culvery › Permissions.",
        )
        assertThat(launched).isEqualTo(1)
        assertThat(pins).isEqualTo(0)

        prompt.answered(granted = true, rationale = false)
        assertThat(askedWhenPinned).isTrue()
        assertThat(pins).isEqualTo(1)
        access.state.first { it == CameraState.ALLOWED }
    }

    /** Fails if the dark screen's glyph follows anything but the presence code's `running`, so it could show with the camera closed or hide with it open; the overlay rule leaves the dark screen to the glyph. */
    @Test
    fun theCameraMarkShowsWheneverTheCameraIsOpen() {
        val test = TestScope()
        val source = OpenOrNot()
        val wanted = MutableStateFlow(false)
        val presence = Presence(source, { test.testScheduler.currentTime }, wanted, MutableStateFlow(Screen.AWAKE), {}, test.backgroundScope)
        presence.start()
        test.runCurrent()
        compose.setContent {
            val running by presence.running.collectAsStateWithLifecycle()
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = OverlayState(),
                    toast = null,
                    onToastHidden = {},
                    pinShowing = false,
                    pinPad = {},
                    activity = FakeUserActivity(),
                    onTouch = {},
                    sleeping = true,
                    sleep = { SleepLayer(Screen.ASLEEP, { LocalDateTime.of(2026, 10, 19, 23, 41) }, {}, cameraRunning = running) },
                    cameraMark = overlayCameraMark(
                        running, sleeping = true, complete = true, settingsOpen = false,
                        sheetShowing = false, pinShowing = false, alertShowing = false,
                    ),
                ) {}
            }
        }
        fun markShown(): Boolean {
            compose.waitForIdle()
            return compose.onAllNodesWithContentDescription(THE_CAMERA_IS_ON).fetchSemanticsNodes().isNotEmpty()
        }

        assertThat(source.isOpen to markShown()).isEqualTo(false to false)
        wanted.value = true
        test.runCurrent()
        assertThat(source.isOpen to markShown()).isEqualTo(true to true)
        source.fail()
        test.runCurrent()
        assertThat(source.isOpen to markShown()).isEqualTo(false to false)
        test.advanceTimeBy(CAMERA_RETRY_MS)
        test.runCurrent()
        assertThat(source.isOpen to markShown()).isEqualTo(true to true)
        wanted.value = false
        test.runCurrent()
        assertThat(source.isOpen to markShown()).isEqualTo(false to false)
        test.cancel()
    }

    /** Pin. Fails if a binding the shell relies on is dropped or bound to another type, which Hilt would only catch in a full build. */
    @Test
    fun theModulesBindEachPiece() {
        assertThat(bindings(DisplayModule::class.java)).containsExactly(
            "nightHours" to Binding(DisplayState::class.java, NightHours::class.java, intoSet = false),
            "tabletHeat" to Binding(AndroidTabletHeat::class.java, TabletHeat::class.java, intoSet = false),
            "heatLog" to Binding(HeatLog::class.java, Startable::class.java, intoSet = true),
            "displayPage" to Binding(DisplayPage::class.java, SettingsPage::class.java, intoSet = true),
        )
        assertThat(bindings(CameraModule::class.java)).containsExactly(
            "frames" to Binding(Camera2Frames::class.java, FrameSource::class.java, intoSet = false),
            "presence" to Binding(Presence::class.java, Startable::class.java, intoSet = true),
        )
    }

    private data class Binding(val from: Class<*>, val to: Class<*>, val intoSet: Boolean)

    private fun bindings(module: Class<*>): List<Pair<String, Binding>> = module.declaredMethods
        .filterNot(Method::isSynthetic)
        .map { it.name.substringBefore('$') to Binding(it.parameterTypes.single(), it.returnType, it.isAnnotationPresent(IntoSet::class.java)) }
}
