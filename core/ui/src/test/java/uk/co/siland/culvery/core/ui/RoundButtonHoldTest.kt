package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class RoundButtonHoldTest {
    @get:Rule val compose = createComposeRule()

    private var steps = 0
    private var enabled by mutableStateOf(true)

    private fun show() {
        steps = 0
        enabled = true
        compose.setContent {
            CulveryTheme(dark = true) {
                HhRoundButton(
                    Icons.ADD, "More times", { steps++ }, Modifier.testTag("more"),
                    enabled = enabled, on = ButtonSurface.Surf, iconSize = ButtonTokens.icon, repeatWhileHeld = true,
                )
            }
        }
    }

    @Test
    fun aTapStepsOnce() {
        show()
        compose.onNodeWithTag("more").performClick()
        compose.waitForIdle()
        assertThat(steps).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun holdingRepeatsAfterHalfASecond() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("more").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(484)
        assertThat(steps).isEqualTo(0)
        compose.mainClock.advanceTimeBy(32)
        assertThat(steps).isEqualTo(1)
        compose.mainClock.advanceTimeBy(300)
        assertThat(steps).isEqualTo(4)
        compose.onNodeWithTag("more").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(400)
        assertThat(steps).isEqualTo(4)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aDisabledButtonStopsRepeating() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("more").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(616)
        assertThat(steps).isEqualTo(2)
        compose.runOnUiThread {
            enabled = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(16)
        compose.mainClock.advanceTimeBy(500)
        assertThat(steps).isEqualTo(2)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
