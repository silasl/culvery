package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
import uk.co.siland.culvery.core.ui.test.TRAY_HEIGHT_DP
import uk.co.siland.culvery.core.ui.test.TRAY_WIDTH_DP
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

    private val gap = TrayTokens.gap.value
    private val padding = TrayTokens.padding.value

    /** 15a §2.1. Fails if the label isn't on top and unspoken, the rows aren't 8 apart and equal, or the spare room isn't below them. */
    @Test
    fun theGridKeepsThreeEqualRowsUnderItsLabel() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(listOf(TrayCell("a") { Box(Modifier.fillMaxWidth().height(40.dp)) }), listOf(TrayCell("b") { Box(Modifier.fillMaxWidth().height(60.dp)) })),
                Modifier.height(300.dp),
            )
        }
        val label = compose.onNodeWithTag("tray_label", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val a = compose.onNodeWithTag("tray_row_a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("tray_row_b").getUnclippedBoundsInRoot()
        val tray = compose.onNodeWithTag("tray_column").getUnclippedBoundsInRoot()
        assertThat(a.top.value - label.bottom.value).isWithin(0.5f).of(gap)
        assertThat(a.height.value).isWithin(0.5f).of(b.height.value)
        assertThat(b.top.value - a.bottom.value).isWithin(0.5f).of(gap)
        // The third row is empty: its row and gap are left as spare room under b.
        assertThat(tray.bottom.value - b.bottom.value).isWithin(0.5f).of(gap + a.height.value + padding)
        compose.onNodeWithTag("tray_label", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    /** 15a §2.1. Fails if two cells in a row aren't the same width and top, 8 apart. */
    @Test
    fun halvesAreEqualWidthAndShareATop() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(listOf(TrayCell("a") { Box(Modifier.fillMaxWidth().height(40.dp)) }, TrayCell("b") { Box(Modifier.fillMaxWidth().height(40.dp)) })),
                Modifier.width(TRAY_WIDTH_DP.dp).height(300.dp),
            )
        }
        val a = compose.onNodeWithTag("tray_row_a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("tray_row_b").getUnclippedBoundsInRoot()
        assertThat(a.width.value).isWithin(0.5f).of(b.width.value)
        assertThat(a.width.value).isWithin(0.5f).of((TRAY_WIDTH_DP - 2 * padding - gap) / 2)
        assertThat(b.left.value - a.right.value).isWithin(0.5f).of(gap)
        assertThat(a.top.value).isEqualTo(b.top.value)
    }

    /** 15a §2.1. Fails if a cell taller than its third runs over the next row or out of the tray, instead of growing its row. */
    @Test
    fun aTallCellGrowsItsRowFromTheEmptyOne() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(
                    listOf(TrayCell("a") { Box(Modifier.fillMaxWidth().height(150.dp)) }, TrayCell("b") { Box(Modifier.fillMaxSize()) }),
                    listOf(TrayCell("c") { Box(Modifier.fillMaxSize()) }),
                ),
                Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
            )
        }
        val tray = compose.onNodeWithTag("tray_column").getUnclippedBoundsInRoot()
        val a = compose.onNodeWithTag("tray_row_a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("tray_row_b").getUnclippedBoundsInRoot()
        val c = compose.onNodeWithTag("tray_row_c").getUnclippedBoundsInRoot()
        assertThat(a.height.value).isWithin(0.5f).of(150f)
        assertThat(b.height.value).isWithin(0.5f).of(150f)
        assertThat(c.top.value - a.bottom.value).isWithin(0.5f).of(gap)
        assertThat(c.bottom.value).isAtMost(tray.bottom.value - padding + 0.5f)
        assertThat(c.height.value).isGreaterThan(0f)
    }

    /** User ruling: fails if an empty row between two filled ones takes any room, or a short row isn't kept at a third. */
    @Test
    fun anEmptyMiddleRowClosesUp() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(
                    listOf(TrayCell("a") { Box(Modifier.fillMaxWidth().height(150.dp)) }),
                    emptyList(),
                    listOf(TrayCell("c") { Box(Modifier.fillMaxSize()) }),
                ),
                Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
            )
        }
        val a = compose.onNodeWithTag("tray_row_a").getUnclippedBoundsInRoot()
        val c = compose.onNodeWithTag("tray_row_c").getUnclippedBoundsInRoot()
        val tray = compose.onNodeWithTag("tray_column").getUnclippedBoundsInRoot()
        assertThat(a.height.value).isWithin(0.5f).of(150f)
        assertThat(c.top.value - a.bottom.value).isWithin(0.5f).of(gap)
        assertThat(c.bottom.value).isLessThan(tray.bottom.value - padding)
    }

    /** User ruling: fails if rows [A,B],[],[C,D] don't sit together at the top, C and D one gap under A and B. */
    @Test
    fun filledRowsSitTogetherAtTheTop() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(
                    listOf(TrayCell("a") { Tile("a") }, TrayCell("b") { Tile("b") }),
                    emptyList(),
                    listOf(TrayCell("c") { Tile("c") }, TrayCell("d") { Tile("d") }),
                ),
                Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
            )
        }
        val a = bounds("a")
        val c = bounds("c")
        val tray = compose.onNodeWithTag("tray_column").getUnclippedBoundsInRoot()
        assertThat(c.top.value - a.bottom.value).isWithin(0.5f).of(gap)
        assertThat(c.height.value).isWithin(0.5f).of(a.height.value)
        // The spare room is below the last row: a row and a gap of it.
        assertThat(tray.bottom.value - padding - c.bottom.value).isWithin(2f).of(a.height.value + gap)
    }

    @Composable
    private fun Tile(id: String) =
        HouseModeTile(id, Icons.BEACH_ACCESS, id, on = false, second = null, secondTag = "${id}_second", spoken = id, enabled = true, onTap = {})

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** F4: fails if a tile's line squashes it, or sits beside its neighbour instead of under the whole row. */
    @Test
    fun aTilesLineGoesUnderItsWholeRowAndTheTileKeepsItsSize() {
        val line = "The heating is still on Eco. Turn it off in the Nest app."
        show {
            HhTrayGrid(
                "One-tap",
                listOf(listOf(TrayCell("a", line) { Tile("a") }, TrayCell("b") { Tile("b") }), listOf(TrayCell("c") { Tile("c") })),
                Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
            )
        }
        val a = bounds("a")
        val b = bounds("b")
        val c = bounds("c")
        val under = bounds("a_line")
        assertThat(a.height.value).isWithin(0.5f).of(b.height.value)
        assertThat(a.height.value).isWithin(0.5f).of(c.height.value)
        assertThat(under.top.value).isAtLeast(maxOf(a.bottom.value, b.bottom.value))
        assertThat(c.top.value).isAtLeast(under.bottom.value)
    }

    /** F4: fails if two lines in a row aren't both shown, one under the other in tile order. */
    @Test
    fun twoLinesInARowStackInTileOrder() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(listOf(TrayCell("a", "Turn Party mode off first.") { Tile("a") }, TrayCell("b", "The music didn’t start.") { Tile("b") })),
                Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
            )
        }
        val a = bounds("a")
        val first = bounds("a_line")
        val second = bounds("b_line")
        assertThat(first.top.value).isAtLeast(a.bottom.value)
        assertThat(second.top.value).isAtLeast(first.bottom.value)
        assertThat(second.left.value).isWithin(0.5f).of(first.left.value)
        assertThat(bounds("b").height.value).isWithin(0.5f).of(a.height.value)
    }

    /** F4: fails if the gap between two rows of tiles differs from the gap between the tiles of a row. */
    @Test
    fun theRowGapIsTheTileGap() {
        show {
            HhTrayGrid(
                "One-tap",
                listOf(listOf(TrayCell("a") { Tile("a") }, TrayCell("b") { Tile("b") }), listOf(TrayCell("c") { Tile("c") }, TrayCell("d") { Tile("d") })),
                Modifier.width(TRAY_WIDTH_DP.dp).height(TRAY_HEIGHT_DP.dp),
            )
        }
        val a = bounds("a")
        val b = bounds("b")
        val c = bounds("c")
        assertThat(c.top.value - a.bottom.value).isWithin(0.5f).of(b.left.value - a.right.value)
    }
}
