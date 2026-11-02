package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HouseRig
import uk.co.siland.culvery.capability.holiday.TestAccess
import uk.co.siland.culvery.core.plugin.HolidayLightsSection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.test.snap

/** Holiday mode's own parts of its page in Settings › Modes (15b §4.2): the turn button, Lights while away and the fixed lines. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h700dp-land-hdpi")
class HolidayModePageScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun stop() = scope.cancel()

    /** Stands in for Lights' automations, which live in another module. */
    private val lights = object : HolidayLightsSection {
        @Composable
        override fun Content(modifier: Modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
                HhChoiceChip("Mimic presence", selected = true, tag = "mimic", onClick = {})
                HhChoiceChip("Away from home", selected = false, tag = "away", onClick = {})
            }
        }
    }

    private fun page(name: String, dark: Boolean) {
        val rig = HouseRig(scope)
        runBlocking { rig.loaded() }
        val page = HolidayModePage(HolidayActions(TestAccess(null), rig.house, null, emptySet(), { HouseRig.TODAY }), rig.house, setOf(lights))
        compose.snap(name, dark, Modifier.size(720.dp, 560.dp)) {
            CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 720.dp)) {
                    page.TurnButton()
                    page.TurnLine()
                    page.Sections()
                }
            }
        }
    }

    @Test fun pageDark() = page("holiday_mode_parts_dark", true)

    @Test fun pageLight() = page("holiday_mode_parts_light", false)
}
