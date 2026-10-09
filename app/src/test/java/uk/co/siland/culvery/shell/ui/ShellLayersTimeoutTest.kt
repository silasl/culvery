package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.SESSION_TIMEOUT_MS
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState

/** 4d-2 §6.2, §6.3 under the Compose test clock: what closes when, and the two warnings. */
@RunWith(AndroidJUnit4::class)
class ShellLayersTimeoutTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val activity = FakeUserActivity()
    private var expiresAt by mutableStateOf<Long?>(null)
    private var pinShowing by mutableStateOf(false)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay,
                    toast = null,
                    onToastHidden = {},
                    pinShowing = pinShowing,
                    pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().testTag("pad")) },
                    activity = activity,
                    onTouch = activity::touch,
                    settingsExpiresAt = expiresAt,
                    nowMillis = { compose.mainClock.currentTime },
                ) { Text("Home") }
            }
        }
    }

    /** Two frames: one recomposes on the new state, the next draws anything an effect wrote back. */
    private fun settle() {
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun after(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.advanceTimeByFrame()
    }

    private fun open(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun settingsWarnsTwentySecondsBeforeItsSessionEndsAndATouchHidesIt() {
        show()
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        after(SESSION_TIMEOUT_MS - 20_000 - 100)
        assertThat(open("settings_warning")).isFalse()
        after(100)
        compose.onNodeWithTag("settings_warning")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertTextEquals("Settings closes in 20 seconds. Touch the screen to stay.")
        // A touch moves the session on; the warning goes until the next countdown's last 20 seconds.
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        assertThat(open("settings_warning")).isFalse()
        after(SESSION_TIMEOUT_MS - 20_000 - 100)
        assertThat(open("settings_warning")).isFalse()
        after(100)
        assertThat(open("settings_warning")).isTrue()
    }

    /** A sheet and the PIN pad hide what they cover from TalkBack; the warning sits above both, so it is still read. */
    @Test
    fun theWarningIsReadOverAnOpenSheetAndThePinPad() {
        show()
        overlay.show { Box(Modifier.fillMaxSize().testTag("sheet")) }
        pinShowing = true
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        after(SESSION_TIMEOUT_MS - 20_000)
        assertThat(open("pad")).isTrue()
        compose.onNodeWithTag("settings_warning").assertTextEquals(SETTINGS_CLOSING)
    }

    @Test
    fun noSessionNoWarning() {
        show()
        after(30 * 60_000L)
        assertThat(open("settings_warning")).isFalse()
    }
}
