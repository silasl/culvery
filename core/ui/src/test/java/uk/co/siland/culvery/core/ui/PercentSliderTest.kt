package uk.co.siland.culvery.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PercentSliderTest {
    @get:Rule val compose = createComposeRule()

    /** 7 §4.3. Fails if a drag reports nothing, if the release isn't reported once, or if the last drag value differs from it. */
    @Test
    fun aDragReportsEachValueAndTheReleaseIsTheLast() {
        val drags = mutableListOf<Int>()
        val letGo = mutableListOf<Int>()
        compose.setContent {
            CulveryTheme(dark = true) { HhPercentSlider("x", 40, "s", range = 1..100, onDrag = { drags += it }, onLetGo = { letGo += it }) }
        }
        compose.onNodeWithTag("s").performTouchInput { swipeRight(startX = centerX, endX = right) }
        assertThat(letGo).hasSize(1)
        assertThat(drags).isNotEmpty()
        assertThat(drags.last()).isEqualTo(letGo.single())
        assertThat(letGo.single()).isAtLeast(90)
    }

    /** Fails if the slider's description and state don't read together as "[name], [n] percent". */
    @Test
    fun itIsSpokenAsNameAndPercent() {
        compose.setContent {
            CulveryTheme(dark = true) { HhPercentSlider("Lounge", 70, "s", spokenName = "Lounge brightness", onLetGo = {}) }
        }
        val config = compose.onNodeWithTag("s").fetchSemanticsNode().config
        val spoken = config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription]
        assertThat(spoken).isEqualTo("Lounge brightness, 70 percent")
    }
}
