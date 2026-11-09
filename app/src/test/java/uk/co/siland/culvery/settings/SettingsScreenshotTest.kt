package uk.co.siland.culvery.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.test.AfterTheScreen

/** Settings with its eight pages (15b §3), on App settings and its sub-pages, at the tablet's 1280 × 800. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule(order = 0) val closing = AfterTheScreen { rig.close() }
    @get:Rule(order = 1) val compose = createComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val rig by lazy { AppSettingsRig(folder.root) }

    private class Still(override val id: String, override val title: String, override val order: Int) : SettingsPage {
        @Composable
        override fun Content() = HhPageTitle(title)
    }

    private fun pages() = listOf(
        rig.page,
        Still("calendars", "Calendars", 400),
        Still("shopping", "Shopping", 420),
        Still("music", "Music", 450),
        Still("lights", "Lights", 470),
        Still("modes", "Modes", 475),
        Still("door", "Door", 500),
        Still("other_integrations", "Home Assistant", 900),
    )

    private fun snap(name: String, dark: Boolean, start: String? = null, fontScale: Float = 1f, beforeShot: () -> Unit = {}) {
        rig.household()
        val pages = pages()
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(
                    LocalShellNavigator provides RecordingNavigator(),
                    LocalOverlayHost provides TestOverlay(),
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(Modifier.testTag("shot").size(1280.dp, 800.dp).background(Culvery.colors.bg)) { SettingsScreen(pages, onClose = {}, initialPage = start) }
                }
            }
        }
        compose.awaitAppSettings()
        compose.waitForIdle()
        beforeShot()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun toTabletLock() {
        compose.onNodeWithTag("tablet_lock_status").performScrollTo()
        compose.waitForIdle()
    }

    @Test fun allPagesDark() = snap("settings_all_pages_dark", true)

    @Test fun allPagesLight() = snap("settings_all_pages_light", false)

    /** 15b §3: the list and Close at font 1.3. */
    @Test fun allPagesLargeTextDark() = snap("settings_all_pages_large_text_dark", true, fontScale = 1.3f)

    @Test fun appSettingsDark() = snap("settings_app_dark", true, beforeShot = ::toTabletLock)

    @Test fun appSettingsLight() = snap("settings_app_light", false, beforeShot = ::toTabletLock)

    @Test fun homeLocationSubPageDark() = snap("settings_home_location_dark", true, start = "location") {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("location_current")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun brightnessSubPageDark() = snap("settings_brightness_dark", true, start = "display") {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("display_brightness")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun seasonalSubPageDark() = snap("settings_seasonal_dark", true, start = "seasonal") {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("display_seasonal")).fetchSemanticsNodes().isNotEmpty() }
    }
}
