package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.display.Screen
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage
import uk.co.siland.culvery.shell.ui.ShellLayers

/** 8 §2.2, §4.1, §4.3: the sleep layer in the shell's stack, what a waking tap reaches, and what waits until it wakes. */
@RunWith(AndroidJUnit4::class)
class SleepLayersTest {
    @get:Rule val compose = createComposeRule()
    private val activity = FakeUserActivity()
    private val at = LocalDateTime.of(2026, 10, 19, 23, 41)

    private var screen by mutableStateOf(Screen.ASLEEP)
    private var toast by mutableStateOf<ToastMessage?>(null)
    private var alert by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var pinShowing by mutableStateOf(false)
    private var cameraMark by mutableStateOf(false)
    private var presses = 0
    private var touches = 0
    private var alertPresses = 0
    private var keyPresses = 0

    /** The button, the alert's button and the PIN pad's key all sit on the screen's centre, so one spot reaches each. */
    private fun show(start: Screen) {
        screen = start
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = OverlayState(),
                    toast = toast,
                    onToastHidden = { id -> if (toast?.id == id) toast = null },
                    pinShowing = pinShowing,
                    pinPad = {
                        if (pinShowing) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HhButton("1", { keyPresses++ }, on = ButtonSurface.Surf) }
                        }
                    },
                    activity = activity,
                    onTouch = { touches++ },
                    nowMillis = { compose.mainClock.currentTime },
                    alert = alert,
                    sleeping = screen != Screen.AWAKE,
                    sleep = { SleepLayer(screen, { at }) { screen = Screen.AWAKE } },
                    cameraMark = cameraMark,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HhButton("Add event", { presses++ }, Modifier.testTag("button"), on = ButtonSurface.Bg)
                    }
                }
            }
        }
        settle()
    }

    /** Two frames: one recomposes on the new state, the next draws anything an effect wrote back. */
    private fun settle() {
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    /** A finger down and up on the centre with no time between them, so the clock stays where the test put it. */
    private fun tap() {
        compose.onRoot().performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
    }

    private fun exactly(millis: Long) {
        compose.mainClock.advanceTimeBy(millis, ignoreFrameDuration = true)
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
    }

    private fun shown(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Fails if the night clock shows anything beside the time, or its name or action is missing or repeats. */
    @Test
    fun theNightClockShowsOnlyTheTime() {
        show(Screen.NIGHT_CLOCK)
        val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertThat(clickables).hasSize(1)
        val clock = clickables.single().config
        assertThat(clock.getOrNull(SemanticsProperties.Text)?.map { it.text }).containsExactly("23:41")
        assertThat(clock.getOrNull(SemanticsProperties.ContentDescription)).isNull()
        assertThat(clock.getOrNull(SemanticsActions.OnClick)?.label).isEqualTo("Wake the screen")
        assertThat(clock.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
        assertThat(texts.flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }).containsExactly("23:41")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the dark screen's name is its action, or TalkBack's click doesn't wake it. */
    @Test
    fun theDarkScreenIsNamedApartFromItsAction() {
        show(Screen.ASLEEP)
        val dark = compose.onNodeWithContentDescription("Screen asleep")
        val config = dark.fetchSemanticsNode().config
        assertThat(config.getOrNull(SemanticsActions.OnClick)?.label).isEqualTo("Wake the screen")
        assertThat(config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(config.getOrNull(SemanticsProperties.Text)).isNull()
        compose.assertClickablesHaveRoleAndLabel()
        dark.performSemanticsAction(SemanticsActions.OnClick)
        assertThat(screen).isEqualTo(Screen.AWAKE)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the waking tap reaches the button beneath or counts as a touch on the shell (8 §4.1). */
    @Test
    fun aWakingTapPressesNothingBeneath() {
        show(Screen.ASLEEP)
        for (start in listOf(Screen.ASLEEP, Screen.NIGHT_CLOCK)) {
            screen = start
            settle()
            tap()
            settle()
            assertThat(screen).isEqualTo(Screen.AWAKE)
            compose.onNodeWithTag("button").assertExists()
            assertThat(presses).isEqualTo(0)
            assertThat(touches).isEqualTo(0)
            compose.assertClickablesHaveRoleAndLabel()
            exactly(WAKE_GRACE_MS)
        }
    }

    /** Fails if a second sleepy tap within [WAKE_GRACE_MS] presses the button, or the grace lasts past it. */
    @Test
    fun aSecondTapWithinTheGraceIsSwallowed() {
        show(Screen.NIGHT_CLOCK)
        tap()
        // The frame that shows the screen awake starts the grace.
        compose.mainClock.advanceTimeByFrame()
        assertThat(screen).isEqualTo(Screen.AWAKE)
        exactly(300)
        tap()
        assertThat(presses).isEqualTo(0)
        exactly(299)
        tap()
        assertThat(presses).isEqualTo(0)
        exactly(1)
        tap()
        assertThat(presses).isEqualTo(1)
        assertThat(touches).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a tap swallowed by the grace counts as a touch on the shell, so it would keep the screen awake. */
    @Test
    fun aTapSwallowedByTheGraceIsNotATouch() {
        show(Screen.ASLEEP)
        tap()
        compose.mainClock.advanceTimeByFrame()
        exactly(300)
        tap()
        exactly(299)
        tap()
        assertThat(screen).isEqualTo(Screen.AWAKE)
        assertThat(presses).isEqualTo(0)
        assertThat(touches).isEqualTo(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if TalkBack can reach a control under the dark screen, or it stays hidden once awake. */
    @Test
    fun whatTheSleepLayerCoversIsHiddenFromTalkBack() {
        show(Screen.ASLEEP)
        compose.onNodeWithTag("button").assertDoesNotExist()
        compose.onNodeWithText("Add event").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
        screen = Screen.AWAKE
        settle()
        compose.onNodeWithTag("button").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a toast draws over the night clock, its timer runs while it waits, or it loses time on waking (8 §4.1). */
    @Test
    fun aToastDoesNotShowOverTheNightClock() {
        show(Screen.NIGHT_CLOCK)
        toast = ToastMessage(1, "Event added.", "info")
        settle()
        assertThat(shown("toast")).isFalse()
        exactly(60_000)
        settle()
        assertThat(toast).isNotNull()
        tap()
        // The frame that shows the screen awake starts the toast's time.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Event added.").assertExists()
        exactly(5_999)
        assertThat(toast).isNotNull()
        exactly(1)
        assertThat(toast).isNull()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the older of two toasts that came while asleep shows on waking, or the newer one loses time. */
    @Test
    fun theNewestHeldToastShowsOnWaking() {
        show(Screen.ASLEEP)
        toast = ToastMessage(1, "Event added.", "info")
        settle()
        toast = ToastMessage(2, "Event deleted.", "info")
        settle()
        exactly(10_000)
        assertThat(shown("toast")).isFalse()
        tap()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Event deleted.").assertExists()
        compose.onNodeWithText("Event added.").assertDoesNotExist()
        exactly(5_999)
        assertThat(toast?.id).isEqualTo(2L)
        exactly(1)
        assertThat(toast).isNull()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the mark is missing over the dark screen or Home while the camera runs, or shows when it doesn't. */
    @Test
    fun theCameraMarkShowsWhileTheCameraRuns() {
        cameraMark = true
        show(Screen.ASLEEP)
        val mark = compose.onNodeWithContentDescription("The camera is on")
        mark.assertExists()
        assertThat(mark.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)).isNull()
        screen = Screen.AWAKE
        settle()
        compose.onNodeWithContentDescription("The camera is on").assertExists()
        cameraMark = false
        settle()
        compose.onNodeWithContentDescription("The camera is on").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the sleep layer sits below the alert (the waking tap would press it) or above the PIN pad. */
    @Test
    fun theSleepLayerIsAboveTheAlertAndBelowThePinPad() {
        alert = { HhButton("Got it", { alertPresses++ }, Modifier.fillMaxWidth(), on = ButtonSurface.Surf) }
        show(Screen.ASLEEP)
        tap()
        settle()
        assertThat(screen).isEqualTo(Screen.AWAKE)
        assertThat(alertPresses).isEqualTo(0)
        compose.assertClickablesHaveRoleAndLabel()

        alert = null
        pinShowing = true
        screen = Screen.ASLEEP
        settle()
        tap()
        settle()
        assertThat(keyPresses).isEqualTo(1)
        assertThat(screen).isEqualTo(Screen.ASLEEP)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
