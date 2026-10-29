package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Needs real glyph widths, which legacy Robolectric graphics fakes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClockFaceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun everyDigitGetsTheSameSlot() {
        compose.setContent {
            Column {
                SlotClock("11:11", CreepsterClock, Color.White, Modifier.testTag("ones"))
                SlotClock("00:00", CreepsterClock, Color.White, Modifier.testTag("zeros"))
                SlotClock("23:58", CreepsterClock, Color.White, Modifier.testTag("late"))
            }
        }
        val ones = compose.onNodeWithTag("ones").getUnclippedBoundsInRoot().width.value
        assertThat(ones).isGreaterThan(0f)
        assertThat(compose.onNodeWithTag("zeros").getUnclippedBoundsInRoot().width.value).isEqualTo(ones)
        assertThat(compose.onNodeWithTag("late").getUnclippedBoundsInRoot().width.value).isEqualTo(ones)
    }

    @Test
    fun theClockReadsAsOneTime() {
        compose.setContent { SlotClock("23:41", CreepsterClock, Color.White, Modifier.testTag("clock")) }
        compose.onNodeWithTag("clock").assertTextEquals("23:41")
        val slots = compose.onAllNodesWithTag("clock_slot", useUnmergedTree = true)
        slots.assertCountEquals(5)
        repeat(5) { i ->
            assertThat(slots[i].fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility)).isTrue()
        }
    }
}
