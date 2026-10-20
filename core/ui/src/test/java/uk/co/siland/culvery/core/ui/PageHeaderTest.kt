package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class PageHeaderTest {
    @get:Rule val compose = createComposeRule()

    private val subtitleText = "2 of 4 rooms on · Hue Bridge"

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.width(600.dp)) { content() } } }

    @Composable
    private fun Header(subtitle: String?, trailing: @Composable RowScope.() -> Unit = {}) =
        HhPageHeader("Lights", subtitle, Modifier.testTag("header"), titleModifier = Modifier.testTag("title"), trailing = trailing)

    @Test
    fun theTitleIsAHeadingAndTheSubtitleSitsUnderIt() {
        show { Header(subtitleText) }
        compose.onNodeWithTag("title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val title = compose.onNodeWithTag("title").getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithText(subtitleText).getUnclippedBoundsInRoot()
        assertThat(subtitle.top.value).isAtLeast(title.bottom.value)
        assertThat(compose.onNodeWithTag("title").textLayout().layoutInput.style.fontSize).isEqualTo(45.sp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSubtitleAddsItsLineAndGap() {
        var subtitle by mutableStateOf<String?>(null)
        show { Header(subtitle) }
        val without = compose.onNodeWithTag("header").getUnclippedBoundsInRoot().height.value
        subtitle = subtitleText
        compose.waitForIdle()
        val withSub = compose.onNodeWithTag("header").getUnclippedBoundsInRoot().height.value
        val layout = compose.onNodeWithText(subtitleText).textLayout()
        val line = with(compose.density) { (layout.getLineBottom(0) - layout.getLineTop(0)).toDp().value }
        assertThat(withSub - without).isWithin(1f).of(4f + line)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: the trailing slot is the right-hand end of the title's row. */
    @Test
    fun theTrailingContentSitsOnTheRight() {
        show { Header("Sub") { HhRoundButton(Icons.HELP, "Help", {}, Modifier.testTag("help"), on = ButtonSurface.Bg) } }
        val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("help").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("title").getUnclippedBoundsInRoot()
        assertThat(button.width.value).isEqualTo(48f)
        assertThat(button.right.value).isWithin(0.5f).of(header.right.value)
        assertThat((button.top.value + button.bottom.value) / 2).isWithin(1f).of((title.top.value + title.bottom.value) / 2)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: the tray is an outlined row with 8 dp padding and 8 dp between its children. */
    @Test
    fun theTrayIsAnOutlinedRow() {
        show {
            HhTray(Modifier.testTag("tray")) {
                HhButton("All lights off", {}, Modifier.testTag("a"), on = ButtonSurface.Bg)
                HhButton("Holiday mode", {}, Modifier.testTag("b"), on = ButtonSurface.Bg)
            }
        }
        val tray = compose.onNodeWithTag("tray").getUnclippedBoundsInRoot()
        val a = compose.onNodeWithTag("a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("b").getUnclippedBoundsInRoot()
        assertThat(a.left.value - tray.left.value).isWithin(0.5f).of(8f)
        assertThat(b.left.value - a.right.value).isWithin(0.5f).of(8f)
        assertThat(tray.right.value - b.right.value).isWithin(0.5f).of(8f)
        assertThat(a.top.value - tray.top.value).isWithin(0.5f).of(8f)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
