package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme

/** 16 §2.3, §3.3: when a burst drops, what ends it, and that it is never in the way. */
@RunWith(AndroidJUnit4::class)
class ConfettiLayerTest {
    @get:Rule val compose = createComposeRule()
    private val wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var awake by mutableStateOf(true)
    private var falls by mutableStateOf(true)
    private var clock = 1_000L
    private var clicks = 0

    @Before
    fun host() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(400.dp, 300.dp)) {
                    Box(Modifier.fillMaxSize().testTag("under").clickable { clicks++ })
                    ConfettiLayer(wakes, awake, falls) { clock }
                }
            }
        }
        frames(2)
    }

    private fun frames(count: Int) = repeat(count) { compose.mainClock.advanceTimeByFrame() }

    /** The write reaches the composition at the next frame, not by itself while the clock is held. */
    private fun set(change: () -> Unit) {
        compose.runOnIdle {
            change()
            Snapshot.sendApplyNotifications()
        }
        frames(2)
    }

    private fun wake() {
        compose.runOnIdle { assertThat(wakes.tryEmit(Unit)).isTrue() }
        frames(2)
    }

    private fun bursts() = compose.onAllNodesWithTag("confetti")

    @Test
    fun aWakeDropsABurst() {
        bursts().assertCountEquals(0)
        wake()
        bursts().assertCountEquals(1)
        compose.mainClock.advanceTimeBy(2_400 - 32)
        bursts().assertCountEquals(1)
        compose.mainClock.advanceTimeBy(600)
        bursts().assertCountEquals(0)
    }

    /** Pin: a wake while it mustn't fall drops nothing. */
    @Test
    fun noBurstWhenItShouldntFall() {
        set { falls = false }
        wake()
        compose.mainClock.advanceTimeBy(500)
        bursts().assertCountEquals(0)
        set { falls = true }
        compose.mainClock.advanceTimeBy(500)
        bursts().assertCountEquals(0)
    }

    @Test
    fun aBurstStopsWhenTheScreenSleeps() {
        wake()
        compose.mainClock.advanceTimeBy(1_000)
        bursts().assertCountEquals(1)
        set { awake = false }
        bursts().assertCountEquals(0)
        set { awake = true }
        compose.mainClock.advanceTimeBy(500)
        bursts().assertCountEquals(0)
    }

    @Test
    fun aBurstStopsWhenARingShows() {
        wake()
        compose.mainClock.advanceTimeBy(1_000)
        bursts().assertCountEquals(1)
        set { falls = false }
        bursts().assertCountEquals(0)
        set { falls = true }
        compose.mainClock.advanceTimeBy(500)
        bursts().assertCountEquals(0)
    }

    @Test
    fun aRecompositionKeepsListening() {
        assertThat(wakes.subscriptionCount.value).isEqualTo(1)
        set { falls = false }
        set { awake = false }
        set { awake = true }
        set { falls = true }
        assertThat(wakes.subscriptionCount.value).isEqualTo(1)
        wake()
        bursts().assertCountEquals(1)
        compose.mainClock.advanceTimeBy(1_000)
        set { falls = true }
        set { awake = true }
        compose.mainClock.advanceTimeBy(500)
        bursts().assertCountEquals(1)
    }

    @Test
    fun aSecondWakeStartsAFreshBurst() {
        wake()
        compose.mainClock.advanceTimeBy(2_000)
        clock += 2_000
        wake()
        compose.mainClock.advanceTimeBy(2_000)
        clock += 2_000
        bursts().assertCountEquals(1)
    }

    /** A wake the screen left again before it was seen awake must not show at the next unrelated wake of the screen. */
    @Test
    fun aWakeTheScreenNeverShowedIsDropped() {
        set { awake = false }
        wake()
        compose.mainClock.advanceTimeBy(3_000)
        clock += 3_000
        set { awake = true }
        compose.mainClock.advanceTimeBy(100)
        bursts().assertCountEquals(0)
    }

    @Test
    fun touchesPassThrough() {
        wake()
        compose.mainClock.advanceTimeBy(500)
        bursts().assertCountEquals(1)
        compose.onNodeWithTag("under").performTouchInput { click() }
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun talkBackFindsNothing() {
        wake()
        compose.mainClock.advanceTimeBy(500)
        val nodes = listOf(compose.onNodeWithTag("confetti", useUnmergedTree = true).fetchSemanticsNode()) +
            compose.onAllNodes(hasAnyAncestor(hasTestTag("confetti")), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { node ->
            assertThat(node.config.contains(SemanticsProperties.Text)).isFalse()
            assertThat(node.config.contains(SemanticsProperties.ContentDescription)).isFalse()
            assertThat(node.config.filter { it.value is AccessibilityAction<*> }).isEmpty()
        }
    }
}
