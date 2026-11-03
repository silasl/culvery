package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.windowBitmap

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BirthdaySkyTest {
    @get:Rule val compose = createComposeRule()

    private fun show(measured: HomeSkyBand?, width: Int = 400, height: Int = 100) = compose.setContent {
        CompositionLocalProvider(LocalHomeSkyBand provides measured) {
            CulveryTheme(dark = true) {
                Box(Modifier.size(width.dp, height.dp).testTag("still_frame").background(DarkColors.bg)) {
                    BirthdaySky(drifting = false, modifier = Modifier.size(width.dp, height.dp))
                }
            }
        }
    }

    /** The header's clock, greeting and items leave a band; the sky is that band and the bunting hangs wholly inside it. */
    @Test
    fun theBuntingStaysInTheBand() {
        val measured = HomeSkyBand().apply {
            headerWidth = 1100
            headerHeight = 220
            clockWidth = 400
            greetingWidth = 420
            dateWidth = 100
            itemsWidth = 300
        }
        val d = compose.density.density
        compose.setContent {
            CompositionLocalProvider(LocalHomeSkyBand provides measured) {
                CulveryTheme(dark = true) { Box(Modifier.size((1100 / d).dp, (220 / d).dp).testTag("still_frame")) { BirthdaySky(drifting = false, modifier = Modifier.size((1100 / d).dp, (220 / d).dp)) } }
            }
        }
        val band = skyBand(measured, Size(1100f, 220f), Density(d))
        val frame = compose.onNodeWithTag("still_frame").fetchSemanticsNode().boundsInRoot
        val sky = compose.onNodeWithTag("birthday_sky").fetchSemanticsNode().boundsInRoot
        val want = band.translate(frame.topLeft)
        assertThat(sky.left).isWithin(1f).of(want.left)
        assertThat(sky.right).isWithin(1f).of(want.right)
        assertThat(sky.top).isWithin(1f).of(want.top)
        assertThat(sky.bottom).isWithin(1f).of(want.bottom)
        // Left edge: the greeting is the widest of the three, plus the gap.
        assertThat(band.left).isWithin(0.01f).of(420f + HalloweenDimens.skyGap.value * d)
        assertThat(lowestFlagTip(Rect(Offset.Zero, Size(sky.width, sky.height)), Density(d))).isAtMost(sky.height)
    }

    @Test
    fun flagsFillTheBand() {
        val d = Density(1f)
        assertThat(flagCount(280f, d)).isEqualTo(10)
        assertThat(flagCount(83f, d)).isEqualTo(0)
        assertThat(flagCount(84f, d)).isEqualTo(3)
        assertThat(flagCount(560f, Density(2f))).isEqualTo(10)
    }

    @Test
    fun theFlagsAreThePersonColours() {
        show(null)
        val node = compose.onNodeWithTag("birthday_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val d = compose.density
        val anchors = flagAnchors(Rect(Offset.Zero, Size(bounds.width, bounds.height)), d)
        assertThat(anchors.size).isAtLeast(PersonPalette.colors.size + 1)
        val centre = BirthdayDimens.flagHeight.value * d.density / 3
        PersonPalette.colors.forEachIndexed { i, colour ->
            val at = anchors[i]
            assertThat(image.getPixel((bounds.left + at.x).toInt(), (bounds.top + at.y + centre).toInt())).isEqualTo(BirthdayInks.flag(colour).toArgb())
        }
        // The seventh flag starts the colours again.
        val seventh = anchors[6]
        assertThat(image.getPixel((bounds.left + seventh.x).toInt(), (bounds.top + seventh.y + centre).toInt())).isEqualTo(BirthdayInks.flag(PersonPalette.colors[0]).toArgb())
    }

    @Test
    fun theSkyIsSilent() {
        show(null)
        val node = compose.onNodeWithTag("birthday_sky").fetchSemanticsNode()
        assertThat(node.config.contains(SemanticsProperties.ContentDescription)).isFalse()
        assertThat(node.children).isEmpty()
        assertThat(node.config.contains(SemanticsProperties.Text)).isFalse()
    }
}
