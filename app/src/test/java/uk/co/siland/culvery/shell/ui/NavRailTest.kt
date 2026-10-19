package uk.co.siland.culvery.shell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.isReadAsOne
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.TabItem

@RunWith(AndroidJUnit4::class)
class NavRailTest {
    @get:Rule val compose = createComposeRule()
    private var selected by mutableStateOf(HOME_TAB_ID)
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val inAGroup = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            NavRail(listOf(TabItem("calendar", "Calendar", Icons.CALENDAR_MONTH)), selected, onSelect = { selected = it }, onOpenSettings = {})
        }
    }

    /** Read through on its own: by position its tabs fell between the clock and the date. */
    @Test
    fun theRailIsReadThroughBeforeThePage() {
        show()
        compose.onNodeWithTag("rail_settings").onParent().assert(isReadAsOne)
    }

    @Test
    fun theRailsItemsAreTabsInOneGroupAndSettingsIsAButtonOutsideIt() {
        show()
        compose.onNodeWithTag("rail_home").assert(tab).assertIsSelected()
        compose.onNodeWithTag("rail_home").onParent().assert(inAGroup)
        compose.onNodeWithTag("rail_calendar").assert(tab).assertIsNotSelected().performClick()
        compose.onNodeWithTag("rail_calendar").assertIsSelected()
        compose.onNodeWithTag("rail_home").assertIsNotSelected()
        compose.onNodeWithTag("rail_settings").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("rail_settings").onParent().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.SelectableGroup))
        compose.assertAccessible()
    }

    @Test
    fun theRailDrawsTheMark() {
        show()
        compose.onNodeWithTag("culvery_mark", useUnmergedTree = true).assertExists()
    }

    /** The count sits in a `danger` pill inside a 2 dp ring in the page colour: at least 19 dp and the ring (9 §4.1). */
    @Test
    fun theDoorBadgeIsAPillWithABackgroundRing() {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(listOf(TabItem("door", "Door", Icons.DOORBELL, RailBadge.Count(2, "2 unseen rings"))), HOME_TAB_ID, onSelect = {}, onOpenSettings = {})
            }
        }
        val badge = compose.onNodeWithTag("rail_door_badge", useUnmergedTree = true)
        badge.assertHeightIsAtLeast(23.dp)
        badge.onChild().assertTextEquals("2")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 5 §5.3: a count or a dot on the icon, and its words after the tab's name. */
    @Test
    fun aBadgeIsDrawnAndAddsItsWordsToTheTabsName() {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(
                    listOf(
                        TabItem("door", "Door", Icons.CALENDAR_MONTH, RailBadge.Count(2, "2 unseen rings")),
                        TabItem("lights", "Lights", Icons.HOME, RailBadge.Dot("new motion")),
                        TabItem("music", "Music", Icons.HOME, RailBadge.Count(12, "12 unseen rings")),
                    ),
                    HOME_TAB_ID, onSelect = {}, onOpenSettings = {},
                )
            }
        }
        compose.onNodeWithTag("rail_door").assert(hasContentDescription("Door, 2 unseen rings"))
        compose.onNodeWithTag("rail_door_badge", useUnmergedTree = true).onChild().assertTextEquals("2")
        compose.onNodeWithTag("rail_lights").assert(hasContentDescription("Lights, new motion"))
        compose.onNodeWithTag("rail_lights_badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("rail_music_badge", useUnmergedTree = true).onChild().assertTextEquals("9+")
        compose.onNodeWithTag("rail_home").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.assertAccessible()
    }
}
