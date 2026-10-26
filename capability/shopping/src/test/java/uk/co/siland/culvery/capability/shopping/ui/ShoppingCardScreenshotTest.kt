package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.shopping.ShoppingState
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The card in Home's utility row, on the page background it sits on (10 §4.9). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingCardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val names = listOf("Milk", "Bread", "Eggs", "Butter", "Bananas", "Bin bags", "Tea bags", "Apples")

    private fun items(count: Int) = (1..count).map { row(it, names.getOrNull(it - 1) ?: "Item $it") }

    private fun snap(name: String, dark: Boolean, state: ShoppingState, size: DpSize = CARD_WIDE) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.size(size).background(Culvery.colors.bg)) { ShoppingCard(state, {}, {}, {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    private val wide = stateOf(items(8))
    private val empty = stateOf(emptyList())
    private val overflow = stateOf(items(20))
    private val cantReach = stateOf(
        items(5),
        problem = "Can’t reach Google Tasks. Items you add are kept and sent when it’s back.",
    )

    @Test fun wideDark() = snap("shopping_card_wide", true, wide)
    @Test fun wideLight() = snap("shopping_card_wide", false, wide)
    @Test fun emptyDark() = snap("shopping_card_empty", true, empty)
    @Test fun emptyLight() = snap("shopping_card_empty", false, empty)
    @Test fun overflowDark() = snap("shopping_card_overflow", true, overflow)
    @Test fun overflowLight() = snap("shopping_card_overflow", false, overflow)
    @Test fun tallDark() = snap("shopping_card_tall", true, overflow, CARD_TALL)
    @Test fun tallLight() = snap("shopping_card_tall", false, overflow, CARD_TALL)
    @Test fun besideTrayDark() = snap("shopping_card_beside_tray", true, overflow, CARD_BESIDE_TRAY)
    @Test fun besideTrayLight() = snap("shopping_card_beside_tray", false, overflow, CARD_BESIDE_TRAY)
    @Test fun problemDark() = snap("shopping_card_problem", true, cantReach)
    @Test fun problemLight() = snap("shopping_card_problem", false, cantReach)
}
