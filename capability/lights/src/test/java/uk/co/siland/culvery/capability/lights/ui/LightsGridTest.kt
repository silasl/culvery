package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.moreRooms
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 9 §4.4, §6.9: the tab's rooms in a grid that grows to three columns and then scrolls, on rooms first, in a kept order. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightsGridTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val lights = LightsFixture()
    private var rooms by mutableStateOf<List<LightRoom>>(emptyList())
    private var focus by mutableStateOf<String?>(null)
    private var focused = 0

    /** Room 1 … Room [n], in Hue's order; those named in [on] are on. */
    private fun numbered(n: Int, on: Set<Int> = emptySet(), scenes: List<LightScene> = emptyList()) =
        (1..n).map { room("Room $it", on = it in on, scenes = scenes) }

    private fun show(fontScale: Float = 1f) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            CulveryTheme(dark = true) {
                Box(Modifier.size(1116.dp, 690.dp).testTag("tab")) {
                    LightsTab(rooms, false, true, null, null, null, emptyMap(), focus, { focused++ }, NO_HOUSE_ACTIONS, RoomActions.None)
                }
            }
        }
    }

    private fun card(n: Int) = "lights_room_Room $n-id"

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Where the node lies even past the grid's edge, which [bounds] clips to. */
    private fun unclipped(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun assertWhollyShown(tag: String) {
        val grid = bounds("lights_grid")
        val at = unclipped(tag)
        assertThat(at.top).isAtLeast(grid.top - 1)
        assertThat(at.bottom).isAtMost(grid.bottom + 1)
    }

    private val dp get() = with(compose.density) { 1.dp.toPx() }

    /** The cards of rooms 1…[n] lie in [columns] columns, the rows sharing the height and the last reaching the tab's foot. */
    private fun assertRowsFillTheTab(n: Int, columns: Int) {
        val cards = (1..n).map { bounds(card(it)) }
        val rows = cards.chunked(columns)
        rows.forEach { row -> row.forEach { assertThat(it.top).isWithin(dp).of(row[0].top) } }
        cards.forEachIndexed { i, b -> assertThat(b.left).isWithin(dp).of(cards[i % columns].left) }
        assertThat(rows[0][columns - 1].left).isGreaterThan(rows[0][0].right)
        rows.forEach { assertThat(it[0].height).isWithin(dp).of(rows[0][0].height) }
        assertThat(rows.last()[0].bottom).isWithin(dp).of(bounds("tab").bottom)
        assertThat(exists("lights_more_rooms")).isFalse()
    }

    /** 9 §7. Fails if four rooms aren't two rows of two sharing the tab's height. */
    @Test
    fun fourRoomsAreTwoByTwo() {
        rooms = numbered(4)
        show()
        assertRowsFillTheTab(4, columns = 2)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §7. Fails if six rooms aren't two rows of three sharing the tab's height. */
    @Test
    fun sixRoomsAreThreeByTwo() {
        rooms = numbered(6)
        show()
        assertRowsFillTheTab(6, columns = 3)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if anything scrolls at six rooms. */
    @Test
    fun sixRoomsDoNotScroll() {
        rooms = numbered(6)
        show()
        assertThat(compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if seven rooms don't scroll in three columns, or the title stays when they do. */
    @Test
    fun sevenRoomsScroll() {
        rooms = numbered(7)
        show()
        compose.onNodeWithTag("lights_grid").assert(hasScrollAction())
        assertThat(bounds(card(3)).top).isWithin(dp).of(bounds(card(1)).top)
        assertThat(bounds(card(4)).top).isGreaterThan(bounds(card(1)).bottom)
        compose.onNodeWithTag("lights_grid").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Lights").assertIsNotDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §7. Fails if nine rooms show no "3 more rooms" with its arrow at the top, a tap doesn't scroll, or the pill stays at the end. */
    @Test
    fun nineRoomsScrollWithAPill() {
        rooms = numbered(9)
        show()
        val pill = compose.onNodeWithTag("lights_more_rooms").assertIsDisplayed().assertTextEquals("3 more rooms")
        val label = compose.onNode(hasText("3 more rooms") and hasAnyAncestor(hasTestTag("lights_more_rooms")), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // The arrow sits before the label: more than the icon's width between the pill's start and the words.
        assertThat(label.left - bounds("lights_more_rooms").left).isGreaterThan(ButtonTokens.icon.value * dp)
        compose.assertClickablesHaveRoleAndLabel()
        pill.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Lights").assertIsNotDisplayed()
        compose.onNodeWithTag(card(9)).assertIsDisplayed()
        assertThat(exists("lights_more_rooms")).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the pill counts n − 6 rather than the rooms after the last row wholly on screen, or stays at the end. */
    @Test
    fun tenRoomsCountWhatsBelow() {
        rooms = numbered(10)
        show()
        compose.onNodeWithTag("lights_grid").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 400 * dp) }
        compose.waitForIdle()
        val grid = bounds("lights_grid")
        val composed = (1..10).filter { exists(card(it)) }
        val whole = composed.filter { unclipped(card(it)).let { b -> b.top >= grid.top - 1 && b.bottom <= grid.bottom + 1 } }
        assertThat(composed.any { unclipped(card(it)).bottom > grid.bottom + 1 }).isTrue()
        val below = 10 - whole.max()
        assertThat(below).isNotEqualTo(10 - 6)
        compose.onNodeWithTag("lights_more_rooms").assertTextEquals(moreRooms(below))
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("lights_grid").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 2_000 * dp) }
        compose.waitForIdle()
        assertThat(exists("lights_more_rooms")).isFalse()
    }

    /** Review Focus: Fails if a room switched on elsewhere moves its card. */
    @Test
    fun aRoomTurningOnDoesNotMoveIt() = runBlocking {
        val lounge = room("Lounge", on = true)
        val kitchen = room("Kitchen")
        val hall = room("Hall", on = true)
        val garden = room("Garden")
        val h = lights.tabHost(lounge, kitchen, hall, garden)
        compose.setContent {
            CulveryTheme(dark = true) { Box(Modifier.size(1116.dp, 690.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) } }
        }
        compose.waitFor { exists("lights_room_Kitchen-id") }
        val before = bounds("lights_room_Kitchen-id")
        assertThat(before.left).isWithin(dp).of(bounds("lights_room_Lounge-id").left)
        assertThat(before.top).isGreaterThan(bounds("lights_room_Hall-id").bottom)
        h.system.rooms.value = listOf(lounge, kitchen.copy(on = true), hall, garden)
        compose.waitFor {
            compose.onNodeWithTag("lights_room_Kitchen-id_switch").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)?.single() == "Kitchen, on, 70 percent"
        }
        assertThat(bounds("lights_room_Kitchen-id").topLeft).isEqualTo(before.topLeft)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the level a slider is held at passes to the card that takes the removed room's place. */
    @Test
    fun aHeldSliderDoesNotPassToAnotherCard() {
        compose.mainClock.autoAdvance = false
        rooms = numbered(4, on = setOf(1, 2, 3, 4))
        show()
        compose.onNodeWithTag("lights_room_Room 1-id_brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("lights_room_Room 1-id_brightness")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "90 percent"))
        rooms = rooms.drop(1)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("lights_room_Room 2-id_brightness")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "70 percent"))
    }

    /** Fails if a room paired while the tab is open goes anywhere but after the others, even when it is on. */
    @Test
    fun aNewRoomGoesAtTheEnd() {
        rooms = listOf(room("Lounge", on = true), room("Kitchen"))
        show()
        rooms = listOf(room("Garden", on = true)) + rooms
        compose.waitForIdle()
        val lounge = bounds("lights_room_Lounge-id")
        assertThat(bounds("lights_room_Kitchen-id").top).isWithin(dp).of(lounge.top)
        assertThat(bounds("lights_room_Kitchen-id").left).isGreaterThan(lounge.right)
        assertThat(bounds("lights_room_Garden-id").left).isWithin(dp).of(lounge.left)
        assertThat(bounds("lights_room_Garden-id").top).isGreaterThan(lounge.bottom)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if Home's › lands on the room's place in Hue's order rather than the opened one, or isn't handed back once. */
    @Test
    fun theFocusedRoomIsScrolledToInTheOpenedOrder() {
        // On first: 7, 8, 9, then 1…6; Room 8 is in the first row, where Hue's order would put it in the third.
        rooms = numbered(9, on = setOf(7, 8, 9))
        focus = "Room 8-id"
        show()
        compose.waitFor { focused == 1 }
        assertWhollyShown(card(8))
        compose.onNodeWithText("Lights").assertIsNotDisplayed()
        compose.waitForIdle()
        assertThat(focused).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a focus arriving before the first read is dropped, or handed back before the order is set. */
    @Test
    fun aFocusBeforeTheFirstReadStillLands() {
        focus = "Room 8-id"
        show()
        compose.waitForIdle()
        assertThat(focused).isEqualTo(0)
        rooms = numbered(9, on = setOf(7, 8, 9))
        compose.waitFor { focused == 1 }
        assertWhollyShown(card(8))
        compose.onNodeWithText("Lights").assertIsNotDisplayed()
        compose.waitForIdle()
        assertThat(focused).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a focus naming a room Hue doesn't list is kept for ever instead of dropped. */
    @Test
    fun aFocusOnAnUnknownRoomIsDropped() {
        rooms = numbered(3)
        focus = "gone-id"
        show()
        compose.waitFor { focused == 1 }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Review Focus: Fails if the bridge dropping and coming back re-sorts the cards or rebuilds the grid. */
    @Test
    fun aBridgeDropAndReturnKeepsTheOrderOnScreen() {
        rooms = numbered(9, on = setOf(7, 8, 9))
        show()
        val shown = listOf(7, 8, 9, 1, 2, 3)
        val before = shown.associateWith { bounds(card(it)).topLeft }
        val grid = compose.onNodeWithTag("lights_grid").fetchSemanticsNode().id
        rooms = emptyList()
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("lights_grid").fetchSemanticsNode().id).isEqualTo(grid)
        rooms = numbered(9, on = setOf(1))
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("lights_grid").fetchSemanticsNode().id).isEqualTo(grid)
        assertThat(shown.associateWith { bounds(card(it)).topLeft }).isEqualTo(before)
        compose.onNodeWithTag("${card(1)}_switch").assertContentDescriptionEquals("Room 1, on, 70 percent")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §2.5. Fails if, in rows that share a fixed height at a large font, a scene chip is cut at the side or the last can't be reached. */
    @Test
    fun sceneChipsAreNotCutInTheSharedRows() {
        rooms = numbered(6, on = setOf(1, 2, 3), scenes = (1..8).map { LightScene("s$it-id", "Scene number $it") })
        show(fontScale = 1.3f)
        (1..6).forEach { n ->
            val card = bounds(card(n))
            val row = bounds("${card(n)}_scenes")
            assertThat(row.bottom).isAtMost(card.bottom)
            (1..8).map { "${card(n)}_scene_s$it-id" }.forEach { chip ->
                assertThat(unclipped(chip).left).isAtLeast(row.left)
                assertThat(unclipped(chip).right).isAtMost(row.right)
            }
        }
        val last = "${card(6)}_scene_s8-id"
        compose.onNodeWithTag("${card(6)}_scenes").performScrollToNode(hasTestTag(last))
        compose.onNodeWithTag(last).assertIsDisplayed()
        assertThat(bounds(last).bottom).isAtMost(bounds(card(6)).bottom)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
