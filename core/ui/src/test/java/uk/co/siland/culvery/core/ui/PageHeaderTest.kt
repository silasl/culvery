package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
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

    /** Pin: a String title is still one heading in the 45 sp bold title style, tagged by its caller. */
    @Test
    fun theStringTitleIsUnchanged() {
        show { Header(subtitleText) }
        compose.onNodeWithTag("title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val style = compose.onNodeWithTag("title").textLayout().layoutInput.style
        assertThat(style.fontSize).isEqualTo(45.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(style.letterSpacing).isEqualTo((-0.7).sp)
        compose.onNodeWithText("Lights").assertExists()
    }

    @Test
    fun aComposableTitleSitsWhereTheStringOneDoes() {
        show { HhPageHeader({ Box(Modifier.testTag("slot").width(100.dp).height(50.dp)) }, subtitleText, Modifier.testTag("header")) }
        val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        val slot = compose.onNodeWithTag("slot").getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithText(subtitleText).getUnclippedBoundsInRoot()
        assertThat(slot.top.value).isWithin(0.5f).of(header.top.value)
        assertThat(subtitle.top.value - slot.bottom.value).isWithin(0.5f).of(4f)
    }

    @Test
    fun aPageTitleIsAHeadingWithItsLineUnderIt() {
        show { HhPageTitle("Calendars", "Connect and choose.") }
        compose.onNodeWithText("Calendars").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val title = compose.onNodeWithText("Calendars").getUnclippedBoundsInRoot()
        val line = compose.onNodeWithText("Connect and choose.").getUnclippedBoundsInRoot()
        assertThat(line.top.value - title.bottom.value).isWithin(1f).of(8f)
        assertThat(compose.onNodeWithText("Calendars").textLayout().layoutInput.style.fontSize).isEqualTo(37.sp)
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

    /** 9 §4.4. Fails if the trailing slot isn't at the right with its foot level with the subtitle's, as the hand-off aligns them. */
    @Test
    fun theTrailingContentSitsRightLevelWithTheSubtitlesFoot() {
        show { Header(subtitleText) { HhRoundButton(Icons.HELP, "Help", {}, Modifier.testTag("help"), on = ButtonSurface.Bg) } }
        val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("help").getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithText(subtitleText).getUnclippedBoundsInRoot()
        assertThat(button.width.value).isEqualTo(48f)
        assertThat(button.right.value).isWithin(0.5f).of(header.right.value)
        assertThat(button.bottom.value).isWithin(0.5f).of(subtitle.bottom.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a title with no subtitle drops to the foot of a taller trailing slot instead of staying at the header's top. */
    @Test
    fun theTitleStaysAtTheTopBesideATallerTrailingSlot() {
        show { Header(null) { Box(Modifier.testTag("tall").width(40.dp).height(90.dp)) } }
        val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("title").getUnclippedBoundsInRoot()
        assertThat(title.top.value).isWithin(1f).of(header.top.value)
        assertThat(compose.onNodeWithTag("tall").getUnclippedBoundsInRoot().bottom.value).isWithin(0.5f).of(header.bottom.value - 15f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.4. Fails if a line given as [under] leaves the left of the trailing slot, or the slot's foot isn't level with its foot. */
    @Test
    fun aLineUnderTheTitleKeepsLeftOfTheTrailingSlot() {
        show {
            HhPageHeader(
                "This week", null, under = { Box(Modifier.testTag("under").fillMaxWidth().height(40.dp)) },
            ) { HhRoundButton(Icons.HELP, "Help", {}, Modifier.testTag("help"), on = ButtonSurface.Bg) }
        }
        val under = compose.onNodeWithTag("under").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("help").getUnclippedBoundsInRoot()
        assertThat(under.right.value).isAtMost(button.left.value)
        assertThat(button.bottom.value).isWithin(0.5f).of(under.bottom.value)
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

    /** 9 §4.4. Fails if the label isn't drawn uppercase in the section-label style with its icon left of the buttons, or is spoken. */
    @Test
    fun aTrayLabelSitsLeftOfItsButtonsAndIsNotSpoken() {
        show {
            HhTray(Modifier.testTag("tray"), label = "One-tap") {
                HhButton("All lights off", {}, Modifier.testTag("a"), on = ButtonSurface.Bg)
            }
        }
        val tray = compose.onNodeWithTag("tray").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText("ONE-TAP", useUnmergedTree = true)
        val style = text.textLayout().layoutInput.style
        assertThat(style.fontSize).isEqualTo(13.sp)
        assertThat(style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(style.color).isEqualTo(DarkColors.mute)
        val icon = compose.onNodeWithTag("tray_label_icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = text.getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag("a").getUnclippedBoundsInRoot()
        assertThat(icon.left.value).isAtLeast(tray.left.value + 8f)
        assertThat(label.left.value).isAtLeast(icon.right.value)
        assertThat(button.left.value).isAtLeast(label.right.value + 8f)
        assertThat((icon.top.value + icon.bottom.value) / 2).isWithin(1f).of((button.top.value + button.bottom.value) / 2)
        compose.onNodeWithTag("tray_label", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: with no label the tray holds only its buttons. */
    @Test
    fun aTrayWithoutALabelHasNone() {
        show { HhTray { HhButton("All lights off", {}, on = ButtonSurface.Bg) } }
        compose.onNodeWithTag("tray_label", useUnmergedTree = true).assertDoesNotExist()
    }

    /** 11 §4.4. Fails if the label isn't on top and unspoken, or the rows aren't 8 apart at their own heights. */
    @Test
    fun theColumnTrayStacksRowsUnderItsLabel() {
        show {
            HhTrayColumn(
                "One-tap",
                listOf(
                    "a" to { Box(Modifier.fillMaxWidth().height(40.dp)) },
                    "b" to { Box(Modifier.fillMaxWidth().height(60.dp)) },
                ),
            )
        }
        val label = compose.onNodeWithTag("tray_label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val a = compose.onNodeWithTag("tray_row_a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("tray_row_b").getUnclippedBoundsInRoot()
        assertThat(a.top.value - label.bottom.value).isWithin(0.5f).of(8f)
        assertThat(a.height.value).isWithin(0.5f).of(40f)
        assertThat(b.top.value - a.bottom.value).isWithin(0.5f).of(8f)
        assertThat(b.height.value).isWithin(0.5f).of(60f)
        compose.onNodeWithTag("tray_label", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }
}
