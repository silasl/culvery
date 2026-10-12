package uk.co.siland.culvery.shell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.test.assertAccessible
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
}
