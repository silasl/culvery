package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.shopping.ShoppingState
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertFitsTheCard

/** The utility card in Home's right column; app's HomeGridTest pins the same sizes. */
internal val CARD_WIDE = DpSize(701.dp, 359.dp)
internal val CARD_TALL = DpSize(701.dp, 586.dp)

/** The card beside the One-tap tray (11 §4.4): the 700 dp column less the 333 dp tray and the 15 dp gap, rounded down; HomeGridTest measures about 351.3. */
internal val CARD_BESIDE_TRAY = DpSize(351.dp, 359.dp)

private const val PERMISSION_LINE = "Allow Google Tasks in Settings › Shopping"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingCardTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val overlay = TestOverlay()
    private val rigs = mutableListOf<UiRig>()
    private val ticks = mutableListOf<String>()

    @After fun closeRigs() = rigs.forEach { it.close() }

    private fun rig() = UiRig().also { rigs += it }

    private fun setCard(size: DpSize, dark: Boolean = true, content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = dark) {
            CompositionLocalProvider(LocalShellNavigator provides navigator, LocalOverlayHost provides overlay) {
                Box(Modifier.size(size).testTag("fit_card")) { content() }
            }
        }
    }

    private fun show(state: ShoppingState, size: DpSize = CARD_WIDE, dark: Boolean = true) = setCard(size, dark) {
        ShoppingCard(
            state,
            onTick = { ticks += it.text },
            onOpen = { navigator.openTab("shopping") },
            onAdd = { navigator.openTab("shopping") },
        )
    }

    private fun items(count: Int) = (1..count).map { row(it) }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun itemTag(n: Int) = "shopping_card_item_g$n"

    /** Fails if the title or the count differs, or the + is under 48 dp or spoken otherwise. */
    @Test
    fun theHeaderSaysHowMany() {
        show(stateOf(listOf(row(1, "Milk"), row(2, "Bread"), row(3, "Eggs"))))
        compose.onNodeWithText("Shopping").assertIsDisplayed()
        compose.onNodeWithText("3 items").assertIsDisplayed()
        val plus = compose.onNodeWithTag("shopping_card_add")
        plus.assertContentDescriptionEquals("Add to the shopping list")
        assertThat(plus.getUnclippedBoundsInRoot().width).isAtLeast(48.dp)
        assertThat(plus.getUnclippedBoundsInRoot().height).isAtLeast(48.dp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the items run in one column, fill across before down, or the rows are not 31 dp. */
    @Test
    fun itemsAreTwoColumnsDownThenAcross() {
        show(stateOf(items(10)))
        val first = bounds(itemTag(1))
        val ninth = bounds(itemTag(9))
        assertThat(ninth.left).isGreaterThan(first.right)
        assertThat(ninth.top).isEqualTo(first.top)
        assertThat(bounds(itemTag(8)).left).isEqualTo(first.left)
        for (n in listOf(1, 8, 10)) assertThat(bounds(itemTag(n)).height.value).isWithin(0.5f).of(31f)
        compose.onNodeWithTag("shopping_card_more").assertDoesNotExist()
        compose.assertFitsTheCard()
    }

    /** Fails if the overflow isn't 15 rows and a footer in the second column's last slot, or the footer doesn't open the tab. */
    @Test
    fun overflowSaysHowManyMore() {
        show(stateOf(items(20)))
        compose.onNodeWithTag(itemTag(15)).assertIsDisplayed()
        compose.onNodeWithTag(itemTag(16)).assertDoesNotExist()
        compose.onNodeWithText("+5 more · Open Shopping").assertIsDisplayed()
        val more = bounds("shopping_card_more")
        assertThat(more.top).isEqualTo(bounds(itemTag(8)).top)
        assertThat(more.left).isEqualTo(bounds(itemTag(9)).left)
        compose.assertFitsTheCard()
        compose.onNodeWithTag("shopping_card_more").performClick()
        assertThat(navigator.tabs).containsExactly("shopping")
    }

    /** Fails if the taller card holds back items it has room for, or shows a footer. */
    @Test
    fun theTallCardShowsMore() {
        show(stateOf(items(20)), CARD_TALL)
        compose.onNodeWithTag(itemTag(20)).assertIsDisplayed()
        compose.onNodeWithTag("shopping_card_more").assertDoesNotExist()
        compose.assertFitsTheCard()
    }

    /** Fails if a tap on the row doesn't tick that item, or the row isn't a checkbox. */
    @Test
    fun aRowTicks() {
        show(stateOf(listOf(row(1, "Milk"), row(2, "Bread"))))
        val milk = compose.onNodeWithTag(itemTag(1))
        milk.assert(SemanticsMatcher("is a checkbox") { it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox })
        milk.performClick()
        assertThat(ticks).containsExactly("Milk")
    }

    /** Fails if the + doesn't ask for the add box before opening the tab. */
    @Test
    fun plusOpensTheTabAskingForTheBox() {
        val rig = rig()
        setCard(CARD_WIDE) { ShoppingCardHost(rig.view, rig.actions, rig.checks, rig.focus) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("shopping_card_add") }
        compose.onNodeWithTag("shopping_card_add").performClick()
        assertThat(rig.focus.takeAddAsk()).isTrue()
        assertThat(navigator.tabs).containsExactly("shopping")
    }

    /** Fails if the header doesn't open the tab, or asks for the box as the + does. */
    @Test
    fun theHeaderOpensTheTab() {
        val rig = rig()
        setCard(CARD_WIDE) { ShoppingCardHost(rig.view, rig.actions, rig.checks, rig.focus) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("shopping_card_title") }
        compose.onNodeWithTag("shopping_card_title").performClick()
        assertThat(navigator.tabs).containsExactly("shopping")
        assertThat(rig.focus.takeAddAsk()).isFalse()
    }

    /** Fails if an empty list doesn't say so or loses the +. */
    @Test
    fun emptySaysSo() {
        show(stateOf(emptyList()))
        compose.onNodeWithText("Nothing on the list").assertIsDisplayed()
        compose.onNodeWithTag("shopping_card_add").assertIsDisplayed()
        compose.onNodeWithTag("shopping_card_count", useUnmergedTree = true).assertDoesNotExist()
        compose.assertFitsTheCard()
    }

    /** Fails if a card with no room for a row, and a problem, throws. */
    @Test
    fun aCardWithNoRoomForRowsDoesNotCrash() {
        show(stateOf(items(3), problem = PERMISSION_LINE), DpSize(701.dp, 100.dp))
        compose.onNodeWithTag("shopping_card_add").assertIsDisplayed()
    }

    /** Pin: a tick through the host shows the Undo toast. */
    @Test
    fun aTickOnHomeOffersUndo() {
        val rig = rig()
        runBlocking { rig.actions.add("Milk") }
        setCard(CARD_WIDE) { ShoppingCardHost(rig.view, rig.actions, rig.checks, rig.focus) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Milk")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Milk").performClick()
        compose.waitUntil(5_000) { rig.toasts.messages.isNotEmpty() }
        assertThat(rig.toasts.messages).containsExactly("Ticked Milk")
        assertThat(rig.toasts.actions.single()?.label).isEqualTo("Undo")
    }

    /** Fails if a problem's line isn't shown under the items. */
    @Test
    fun aProblemShowsOnTheCard() {
        show(stateOf(items(3), problem = PERMISSION_LINE))
        compose.onNodeWithText(PERMISSION_LINE).assertIsDisplayed()
        assertThat(bounds("shopping_card_status").top).isGreaterThan(bounds(itemTag(3)).top)
        compose.assertFitsTheCard()
    }

    /** Fails if the problem line doesn't take the last row, "+n more" doesn't move up a slot, or n leaves out an item. */
    @Test
    fun aProblemTakesTheLastRowAndTheFooterMovesUp() {
        show(stateOf(items(20), problem = PERMISSION_LINE))
        compose.onNodeWithTag(itemTag(13)).assertIsDisplayed()
        compose.onNodeWithTag(itemTag(14)).assertDoesNotExist()
        compose.onNodeWithText("+7 more · Open Shopping").assertIsDisplayed()
        assertThat(bounds("shopping_card_more").top).isEqualTo(bounds(itemTag(7)).top)
        assertThat(bounds("shopping_card_more").left).isEqualTo(bounds(itemTag(8)).left)
        assertThat(bounds("shopping_card_status").top.value).isWithin(0.5f).of(bounds(itemTag(7)).top.value + 31f)
        compose.assertFitsTheCard()
    }

    /** Fails if the card doesn't watch the list while it is shown, or keeps watching after it goes. */
    @Test
    fun theCardWatchesWhileShown() {
        val rig = rig()
        var shown by mutableStateOf(true)
        setCard(CARD_WIDE) { if (shown) ShoppingCardHost(rig.view, rig.actions, rig.checks, rig.focus) }
        compose.waitForIdle()
        assertThat(rig.checks.watching).isEqualTo(1)
        shown = false
        compose.waitForIdle()
        assertThat(rig.checks.watching).isEqualTo(0)
    }

    /** Anti-goal 3: nothing on the card goes beyond the Shopping tab. */
    @Test
    fun nothingOnTheCardLeavesTheList() {
        val states = listOf(
            stateOf(items(20)),
            stateOf(emptyList()),
            stateOf(items(3), problem = PERMISSION_LINE),
        )
        var shown by mutableStateOf(states.first())
        setCard(CARD_WIDE) {
            ShoppingCard(
                shown,
                onTick = {},
                onOpen = { navigator.openTab("shopping") },
                onAdd = { navigator.openTab("shopping") },
            )
        }
        states.forEach { state ->
            shown = state
            compose.waitForIdle()
            val clickables = compose.onAllNodes(hasClickAction())
            repeat(clickables.fetchSemanticsNodes().size) { clickables[it].performClick() }
        }
        assertThat(navigator.tabs.toSet()).containsExactly("shopping")
        assertThat(navigator.pages).isEmpty()
        assertThat(navigator.settingsOpened).isEqualTo(0)
        assertThat(navigator.kioskExits).isEqualTo(0)
        assertThat(navigator.systemScreens).isEmpty()
        assertThat(navigator.homeAppChoices).isEqualTo(0)
        assertThat(overlay.shown).isEqualTo(0)
    }

    /** Pin: built for 701 dp, the card beside the tray with 20 items cuts nothing and overlaps nothing. */
    @Test
    fun theCardFitsBesideTheTray() {
        show(stateOf(items(20)), CARD_BESIDE_TRAY)
        compose.assertFitsTheCard()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: the same in the light theme. */
    @Test
    fun theCardFitsBesideTheTrayInLight() {
        show(stateOf(items(20)), CARD_BESIDE_TRAY, dark = false)
        compose.assertFitsTheCard()
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun ComposeContentTestRule.onAllNodesWithTagExists(tag: String) =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}

class CardSlotsTest {
    /** Fails if a list that fits two columns is cut, or an overflow doesn't give the footer the last slot. */
    @Test
    fun cardSlotsFillTwoColumns() {
        assertThat(cardSlots(10, 8)).isEqualTo(10 to 0)
        assertThat(cardSlots(16, 8)).isEqualTo(16 to 0)
        assertThat(cardSlots(17, 8)).isEqualTo(15 to 2)
    }

    /** Fails if a card too short for a row, or the problem line's row alone, makes a negative count. */
    @Test
    fun cardSlotsSurviveNoRoom() {
        assertThat(cardSlots(3, 0)).isEqualTo(0 to 3)
        assertThat(cardSlots(0, 0)).isEqualTo(0 to 0)
    }
}
