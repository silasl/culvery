package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.shopping.ProviderAccounts
import uk.co.siland.culvery.capability.shopping.SampleShoppingProvider
import uk.co.siland.culvery.capability.shopping.ShoppingHealth
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

private val CANVAS_W = 1280.dp
private val CANVAS_H = 800.dp
private const val FAMILY = "family@example.com"
private const val SAM = "sam@example.com"

/** Settings › Shopping in each state, inside Settings' own frame (10 §4.10). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShoppingSettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val provider = SampleShoppingProvider(
        clock = WallClock { now },
        descriptor = GOOGLE_NAMED,
        accountGoneLine = "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars.",
    )

    private class StillPage(override val id: String, override val title: String, override val order: Int, val content: @Composable () -> Unit) : SettingsPage {
        @Composable
        override fun Content() = content()
    }

    private fun on(health: ShoppingHealth = ShoppingHealth.OK) = SettingsState.On(provider, FAMILY, health, now - 2 * 60_000)

    private fun snap(name: String, dark: Boolean, state: SettingsState) {
        val pages = listOf(
            StillPage("location", "Home location", 0) {},
            StillPage("people", "People", 100) {},
            StillPage("calendars", "Calendars", 400) {},
            StillPage("shopping", "Shopping", 420) { ShoppingSettings(state, now, false, ShoppingSettingsActions()) },
            KioskPage(object : HomeApp { override val isDefault = MutableStateFlow(true) }),
        )
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                    Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) { SettingsScreen(pages, onClose = {}) }
                }
            }
        }
        compose.onNodeWithTag("settings_page_shopping").performClick()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    private val blocked = SettingsState.Off(provider, ProviderAccounts.Blocked("Connect Google Calendar first. Shopping uses the same account."))
    private val off = SettingsState.Off(provider, ProviderAccounts.Ready(listOf(FAMILY)))
    private val choose = SettingsState.ChoosingAccount(provider, listOf(FAMILY, SAM), SAM)

    @Test fun blockedDark() = snap("settings_shopping_blocked", true, blocked)
    @Test fun blockedLight() = snap("settings_shopping_blocked", false, blocked)
    @Test fun offDark() = snap("settings_shopping_off", true, off)
    @Test fun offLight() = snap("settings_shopping_off", false, off)
    @Test fun chooseDark() = snap("settings_shopping_choose", true, choose)
    @Test fun chooseLight() = snap("settings_shopping_choose", false, choose)
    @Test fun onDark() = snap("settings_shopping_on", true, on())
    @Test fun onLight() = snap("settings_shopping_on", false, on())
    @Test fun permissionDark() = snap("settings_shopping_permission", true, on(ShoppingHealth.NEEDS_PERMISSION))
    @Test fun permissionLight() = snap("settings_shopping_permission", false, on(ShoppingHealth.NEEDS_PERMISSION))
    @Test fun listGoneDark() = snap("settings_shopping_list_gone", true, on(ShoppingHealth.LIST_GONE))
    @Test fun listGoneLight() = snap("settings_shopping_list_gone", false, on(ShoppingHealth.LIST_GONE))
    @Test fun accountGoneDark() = snap("settings_shopping_account_gone", true, on(ShoppingHealth.ACCOUNT_GONE))
    @Test fun accountGoneLight() = snap("settings_shopping_account_gone", false, on(ShoppingHealth.ACCOUNT_GONE))
    @Test fun stopDark() = snap("settings_shopping_stop", true, SettingsState.ConfirmingStop(on(), 2))
    @Test fun stopLight() = snap("settings_shopping_stop", false, SettingsState.ConfirmingStop(on(), 2))
}
