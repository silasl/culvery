package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.shopping.Learned
import uk.co.siland.culvery.capability.shopping.ShoppingState
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tab in the main area beside the rail, as the shell gives it (10 §4.8, layout B revised). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingTabScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private class Scene(
        val state: ShoppingState,
        val typed: String = "",
        val suggestions: List<Learned> = emptyList(),
        val favourites: List<Learned> = emptyList(),
        val nothingLearned: Boolean = false,
    )

    private val names = listOf("Milk", "Bread", "Eggs", "Butter", "Bananas", "Bin bags", "Tea bags", "Apples")
    private fun learned(text: String, count: Int) = Learned(text.lowercase(), text, count, 0)
    private val favourites = listOf("Bananas", "Butter", "Bin bags", "Tea bags", "Apples").mapIndexed { i, text -> learned(text, 20 - i) }

    private fun items(count: Int, named: Boolean = true) = (1..count).map { row(it, names.getOrNull(it - 1).takeIf { named } ?: "Item $it") }

    private fun snap(name: String, dark: Boolean, scene: Scene) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.size(TAB_WIDTH, TAB_HEIGHT).background(Culvery.colors.bg)) {
                    ShoppingTab(scene.state, scene.typed, {}, {}, scene.suggestions, scene.favourites, {}, {}, {}, focusAdd = false, nowMillis = NOW, nothingLearned = scene.nothingLearned)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    private val empty = Scene(stateOf(emptyList()), nothingLearned = true)
    private val few = Scene(stateOf(items(3)), favourites = favourites)
    private val thirty = Scene(stateOf(items(30, named = false)), favourites = favourites)
    private val typing = Scene(stateOf(items(3)), typed = "bu", suggestions = listOf(learned("Butter", 9)), favourites = favourites.drop(2))
    private val offList = favourites.drop(2)
    private val ticked = Scene(stateOf(items(5).map { if (it.text == "Bananas") it.copy(ticked = true) else it }), favourites = offList)
    private fun problem(line: String) = Scene(stateOf(items(5), problem = line), favourites = offList)
    private val cantReach = problem("Can’t reach Google Tasks. Items you add are kept and sent when it’s back.")
    private val permission = problem("Allow Google Tasks in Settings › Shopping")
    private val listGone = problem("The Shopping list isn’t in Google Tasks any more. Make a new one in Settings › Shopping.")
    private val accountGone = problem("Shopping’s Google account was disconnected. Connect it again in Settings › Calendars.")

    @Test fun emptyDark() = snap("shopping_tab_empty", true, empty)
    @Test fun emptyLight() = snap("shopping_tab_empty", false, empty)
    @Test fun fewDark() = snap("shopping_tab_few", true, few)
    @Test fun fewLight() = snap("shopping_tab_few", false, few)
    @Test fun thirtyDark() = snap("shopping_tab_30", true, thirty)
    @Test fun thirtyLight() = snap("shopping_tab_30", false, thirty)
    @Test fun typingDark() = snap("shopping_tab_typing", true, typing)
    @Test fun typingLight() = snap("shopping_tab_typing", false, typing)
    @Test fun tickedDark() = snap("shopping_tab_ticked", true, ticked)
    @Test fun tickedLight() = snap("shopping_tab_ticked", false, ticked)
    @Test fun cantReachDark() = snap("shopping_tab_cant_reach", true, cantReach)
    @Test fun cantReachLight() = snap("shopping_tab_cant_reach", false, cantReach)
    @Test fun permissionDark() = snap("shopping_tab_permission", true, permission)
    @Test fun permissionLight() = snap("shopping_tab_permission", false, permission)
    @Test fun listGoneDark() = snap("shopping_tab_list_gone", true, listGone)
    @Test fun listGoneLight() = snap("shopping_tab_list_gone", false, listGone)
    @Test fun accountGoneDark() = snap("shopping_tab_account_gone", true, accountGone)
    @Test fun accountGoneLight() = snap("shopping_tab_account_gone", false, accountGone)
}
