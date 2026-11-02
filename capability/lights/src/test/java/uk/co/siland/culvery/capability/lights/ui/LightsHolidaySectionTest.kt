package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.lights.AWAY
import uk.co.siland.culvery.capability.lights.Automation
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightsPrefs
import uk.co.siland.culvery.capability.lights.MIMIC
import uk.co.siland.culvery.capability.lights.TestLightsProvider
import uk.co.siland.culvery.capability.lights.link
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 15b decision 5: Holiday lights' presence automations, drawn on Holiday mode's page. */
@RunWith(AndroidJUnit4::class)
class LightsHolidaySectionTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val lights = LightsFixture()
    private val calls = mutableListOf<String>()
    private var automations by mutableStateOf<List<Automation>?>(listOf(MIMIC, AWAY))
    private var ticked by mutableStateOf(emptySet<String>())

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            InSettingsPane { HolidayChoice(automations, ticked) { calls += "holiday ${it.sorted()}" } }
        }
    }

    private fun checkbox() = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    /** Fails if the note about Holiday lights is missing under an empty tick, or ticking doesn't save. */
    @Test
    fun holidayAutomationsTicks() {
        show()
        compose.onNodeWithTag("lights_holiday_note").assertTextEquals("Holiday lights turns all lights off and shows On.")
        compose.onNodeWithTag("lights_automation_mimic-id").assert(checkbox()).performClick()
        assertThat(calls).containsExactly("holiday [mimic-id]")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the note stays once an automation is ticked. */
    @Test
    fun theNoteGoesOnceOneIsTicked() {
        ticked = setOf("mimic-id")
        show()
        compose.onNodeWithTag("lights_automation_mimic-id").assertIsSelected()
        compose.onAllNodesWithTag("lights_holiday_note").assertCountEquals(0)
    }

    /** Fails if a bridge with no automations doesn't say how to get one. */
    @Test
    fun noAutomationsYet() {
        automations = emptyList()
        show()
        compose.onNodeWithTag("lights_no_automations").assertTextEquals("No Hue automations yet. Set one up in the Hue app, such as Mimic presence.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a section draws anything before the bridge has answered. */
    @Test
    fun nothingBeforeTheBridgeAnswers() {
        automations = null
        show()
        compose.onAllNodesWithTag("lights_holiday_note").assertCountEquals(0)
        compose.onAllNodesWithTag("lights_no_automations").assertCountEquals(0)
    }

    /** Fails if a ticked automation the bridge dropped shows, or the next save keeps its id. */
    @Test
    fun aGoneAutomationIsNotListed() {
        ticked = setOf("mimic-id", "gone-id")
        show()
        compose.onAllNodesWithTag("lights_automation_gone-id").assertCountEquals(0)
        compose.onNodeWithTag("lights_automation_away-id").performClick()
        assertThat(calls).containsExactly("holiday [away-id, mimic-id]")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the host doesn't list what the bridge has, or a tick isn't saved at once. */
    @Test
    fun theHostListsWhatTheBridgeHasAndSavesATickAtOnce() {
        val hue = TestLightsProvider()
        val bridges = Bridges(setOf(hue), lights.settings, lights.scope)
        val saved = lights.settings.prefs.stateIn(lights.scope, SharingStarted.Eagerly, LightsPrefs())
        compose.setContent {
            CulveryTheme(dark = true) { InSettingsPane { LightsHolidayHost(lights.settings, bridges, RecordingToaster()) } }
        }
        runBlocking { lights.settings.connect("lights.hue", link()) }
        compose.waitFor { compose.onAllNodesWithTag("lights_automation_mimic-id").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("lights_automation_away-id").performClick()

        compose.waitFor { saved.value.holidayAutomations == setOf("away-id") }
    }

    /** Fails if an unpaired bridge leaves the section empty, or shows an automations prompt instead of saying to set up Hue. */
    @Test
    fun saysToSetUpHueWithoutAPairedBridge() {
        val bridges = Bridges(setOf(TestLightsProvider()), lights.settings, lights.scope)
        compose.setContent {
            CulveryTheme(dark = true) { InSettingsPane { LightsHolidayHost(lights.settings, bridges, RecordingToaster()) } }
        }
        // The prefs arrive from DataStore off the main thread, after the first idle.
        compose.waitFor { compose.onAllNodesWithTag("lights_holiday_set_up").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lights_holiday_set_up").assertTextEquals("Set up Hue in Settings › Lights to choose what the lights do while you’re away.")
        compose.onAllNodesWithTag("lights_no_automations").assertCountEquals(0)
        compose.onAllNodesWithTag("lights_holiday_note").assertCountEquals(0)
    }
}
