package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.DESK
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugCandidate
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugsRig
import uk.co.siland.culvery.capability.plugs.TestAccess
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

private val LAMP_CANDIDATE = PlugCandidate(LAMP, "Lamp")
private val FAN_CANDIDATE = PlugCandidate(FAN, "Fan")

/** 12 §4.2: Settings › Other integrations › Plugs: which plugs show on the tab and which need a PIN to switch. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsSettingsSectionTest {
    @get:Rule val compose = createComposeRule()
    private val rig = PlugsRig()
    private val actions = PlugActions(TestAccess(Role.ADMIN), rig.settings, rig.system, RecordingToaster(), CoroutineScope(Job()))
    private val section = PlugsSettingsSection(setOf(rig.system), actions, rig.settings)

    private fun show(found: PlugCandidates, shown: List<String> = emptyList(), needsPin: Set<String> = emptySet()) {
        rig.system.found = found
        runBlocking {
            rig.configure { prefs -> shown.fold(prefs) { acc, id -> acc.showing(id) }.copy(needsPin = needsPin) }
        }
        compose.setContent {
            CulveryTheme(dark = true) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp)) { section.Content() }
            }
        }
        compose.waitFor { compose.onAllNodesWithTag("plugs_settings").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun found(vararg plugs: PlugCandidate) = PlugCandidates.Found(plugs.toList())

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun listsOnlyTheAvailablePlugsByName() {
        show(found(FAN_CANDIDATE, LAMP_CANDIDATE))
        compose.waitFor { count("plugs_show_1") == 1 }
        compose.onNodeWithTag("plugs_name_0").assertTextEquals("FAN")
        compose.onNodeWithTag("plugs_name_1").assertTextEquals("LAMP")
        compose.onAllNodesWithTag("plugs_show_2").assertCountEquals(0)
        compose.onNodeWithTag("plugs_help")
            .assertTextEquals("Plugs from Home Assistant’s TP-Link integration. Turn one on to show it on the Plugs tab.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun showPutsAPlugOnTheTab() {
        show(found(LAMP_CANDIDATE))
        compose.waitFor { count("plugs_show_0") == 1 }
        compose.onNodeWithTag("plugs_show_0").assertIsOff().performClick()
        compose.waitFor { runBlocking { rig.prefs().plugs } == listOf(LAMP) }
        compose.waitFor { runCatching { compose.onNodeWithTag("plugs_show_0").assertIsOn() }.isSuccess }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun needsPinIsOffUntilShown() {
        show(found(LAMP_CANDIDATE, FAN_CANDIDATE), shown = listOf(LAMP), needsPin = setOf(LAMP))
        compose.waitFor { count("plugs_pin_1") == 1 }
        compose.onNodeWithTag("plugs_pin_1").assertIsNotEnabled()
        compose.onNodeWithTag("plugs_pin_0").assertIsEnabled().assertIsOn().performClick()
        compose.waitFor { runBlocking { rig.prefs().needsPin }.isEmpty() }
        compose.onNodeWithTag("plugs_show_1").performClick()
        compose.waitFor { runCatching { compose.onNodeWithTag("plugs_pin_1").assertIsEnabled() }.isSuccess }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aGoneShownPlugStaysListed() {
        show(found(LAMP_CANDIDATE), shown = listOf(LAMP, DESK))
        compose.waitFor { count("plugs_show_1") == 1 }
        compose.onNodeWithTag("plugs_gone_1").assertTextEquals("Home Assistant has no plug called that any more.")
        compose.onAllNodesWithTag("plugs_gone_0").assertCountEquals(0)
        compose.onNodeWithTag("plugs_show_1").assertIsOn().performClick()
        compose.waitFor { runBlocking { rig.prefs().plugs } == listOf(LAMP) }
        compose.waitFor { count("plugs_show_1") == 0 }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noPlugsSaysSo() {
        show(found())
        compose.waitFor { count("plugs_line") == 1 }
        compose.onNodeWithTag("plugs_line").assertTextEquals("Home Assistant has no TP-Link plugs. Add them in Home Assistant first.")
        compose.onAllNodesWithTag("plugs_show_0").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anUnreadableRegistrySaysSo() {
        show(PlugCandidates.Failed("Can’t list the plugs. Check Home Assistant is up to date."), shown = listOf(LAMP))
        compose.waitFor { count("plugs_line") == 1 }
        compose.onNodeWithTag("plugs_line").assertTextEquals("Can’t list the plugs. Check Home Assistant is up to date.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theSwitchesAreReadOutWithThePlugsName() {
        show(found(LAMP_CANDIDATE))
        compose.waitFor { count("plugs_show_0") == 1 }
        compose.onNodeWithTag("plugs_show_0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Lamp, show on the Plugs tab")))
        compose.onNodeWithTag("plugs_pin_0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Lamp, needs a PIN to switch")))
        assertThat(section.order).isEqualTo(10)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
