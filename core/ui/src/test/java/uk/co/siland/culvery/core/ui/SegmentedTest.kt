package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.core.ui.test.snap

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedTest {
    @get:Rule val compose = createComposeRule()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun oneIsSelectedAndALockedOneExplains() {
        var selected by mutableIntStateOf(0)
        var picks = 0
        var explained = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                HhSegmented(
                    listOf("All day", "At a time", "Later"),
                    selected = selected,
                    onSelect = {
                        picks++
                        selected = it
                    },
                    tag = "seg",
                    locked = setOf(1),
                    onLocked = { explained++ },
                )
            }
        }
        val c = DarkColors
        compose.onNodeWithTag("seg").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithTag("seg_0").assert(radio).assertIsSelected()
        compose.onNodeWithTag("seg_2").assert(radio).assertIsNotSelected()
        val height = compose.onNodeWithTag("seg_0").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertThat(height.value).isWithin(0.5f).of(48f)
        assertWithMessage("the chosen segment's fill").that(pixelAt(compose.onNodeWithTag("seg_0"), 24.dp, 2.dp)).isEqualTo(c.accent.toArgb())
        assertWithMessage("the pill around the segments").that(pixelAt(compose.onNodeWithTag("seg"), 24.dp, 2.dp)).isEqualTo(c.surf2.toArgb())
        assertWithMessage("an unchosen segment sits on the pill").that(pixelAt(compose.onNodeWithTag("seg_2"), 24.dp, 2.dp)).isEqualTo(c.surf2.toArgb())

        compose.onNodeWithTag("seg_1_lock", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("seg_1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available")).performClick()
        compose.runOnIdle {
            assertThat(explained).isEqualTo(1)
            assertThat(picks).isEqualTo(0)
        }
        compose.onNodeWithTag("seg_0").assertIsSelected()

        compose.onNodeWithTag("seg_2").performClick()
        compose.runOnIdle {
            assertThat(picks).isEqualTo(1)
            assertThat(selected).isEqualTo(2)
        }
        compose.onNodeWithTag("seg_2").assertIsSelected()
        compose.onNodeWithTag("seg_0").assertIsNotSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** At a time chosen; then All day chosen with At a time locked, as while an event lasts several days. */
    private fun segments(dark: Boolean) = compose.snap(if (dark) "segmented_dark" else "segmented_light", dark, Modifier.padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HhSegmented(listOf("All day", "At a time"), selected = 1, onSelect = {}, tag = "time")
            HhSegmented(listOf("All day", "At a time"), selected = 0, onSelect = {}, tag = "locked", locked = setOf(1))
        }
    }

    @Test fun segmentedDark() = segments(dark = true)

    @Test fun segmentedLight() = segments(dark = false)
}
