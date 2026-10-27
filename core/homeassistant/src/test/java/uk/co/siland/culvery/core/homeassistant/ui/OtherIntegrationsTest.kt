package uk.co.siland.culvery.core.homeassistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.homeassistant.FakeDevice
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLinking
import uk.co.siland.culvery.core.homeassistant.HomeAssistantSettings
import uk.co.siland.culvery.core.homeassistant.MemoryStore
import uk.co.siland.culvery.core.homeassistant.SAVED
import uk.co.siland.culvery.core.homeassistant.TestAccess
import uk.co.siland.culvery.core.homeassistant.homeAssistantUrl
import uk.co.siland.culvery.core.homeassistant.hoover
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant
import uk.co.siland.culvery.core.homeassistant.thermostat
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

private const val HELP = "Give the NAS a fixed address on the router. Then sign in to Home Assistant as the Culvery user (not an administrator), " +
    "open Profile › Security › Long-lived access tokens › Create token, and paste the token here."

private val NOTHING_LINKED = OtherIntegrationsUi(
    linked = false, status = null, checking = false, checkLine = null, devices = emptyList(),
)

/** 11 §4.1, §6: Settings › Other integrations: the address and the token, Check, and the two pickers. */
@RunWith(AndroidJUnit4::class)
class OtherIntegrationsTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = FakeHomeAssistantApi()
    private val settings = HomeAssistantSettings(MemoryStore())
    private val access = TestAccess(Role.ADMIN)
    private val toaster = RecordingToaster()
    private val hoover = hoover()
    private val thermostat = thermostat()
    private lateinit var ha: HomeAssistant

    @After
    fun tearDown() = scope.cancel()

    private fun oneOfEach() {
        api.states["vacuum.hoover"] = entityState("vacuum.hoover", "docked", mapOf("friendly_name" to "Robot"))
        api.states["climate.hall"] = entityState("climate.hall", "heat", mapOf("friendly_name" to "Hall"))
    }

    private fun setPage(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp)) { content() }
        }
    }

    private fun showHost(saved: Boolean = true, devices: List<FakeDevice> = listOf(hoover, thermostat), shown: () -> Boolean = { true }) {
        runBlocking { if (saved) settings.save(SAVED) }
        ha = testHomeAssistant(api, settings.link, MutableStateFlow(true), scope)
        val linking = HomeAssistantLinking(ha, settings, access, devices.toSet()) { homeAssistantUrl(it) }
        setPage { if (shown()) OtherIntegrationsHost(ha, linking, devices.toSet(), toaster) }
        if (saved) compose.waitFor { ha.link.value != null }
    }

    private fun typeAddressAndToken(address: String = "192.168.1.20", token: String = "test-token") {
        compose.onNodeWithTag("ha_address").performTextInput(address)
        compose.onNodeWithTag("ha_token").performTextInput(token)
    }

    private fun clipboard() = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)

    private fun linkedWithTokenOnTheClipboard(held: String) {
        clipboard().setPrimaryClip(ClipData.newPlainText("copied", held))
        showHost(saved = false)
        typeAddressAndToken()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitFor { ha.link.value != null }
        compose.waitFor { compose.onAllNodes(hasText("Linked.")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun notLinkedSaysHow() {
        setPage { OtherIntegrations(NOTHING_LINKED, "", {}, "", {}, {}, { _, _ -> }) }
        compose.onNodeWithTag("settings_other").assertExists()
        compose.onNodeWithText("Devices Culvery reaches through Home Assistant.").assertExists()
        compose.onNodeWithTag("ha_status")
            .assertTextEquals("Not linked yet. Enter Home Assistant’s address and a token for Culvery, then Check.")
        compose.onNodeWithTag("ha_address")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Home Assistant’s address")))
        compose.onNodeWithText("http://192.168.1.20:8123").assertExists()
        compose.onNodeWithTag("ha_token")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Token for Culvery")))
        compose.onNodeWithText("Paste the token here").assertExists()
        compose.onNodeWithTag("ha_help").assertTextEquals(HELP)
        compose.onNodeWithTag("ha_check").assertTextEquals("Check")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSavedTokenSaysSoInItsPlaceholder() {
        setPage { OtherIntegrations(NOTHING_LINKED.copy(linked = true), "http://192.168.1.20:8123/", {}, "", {}, {}, { _, _ -> }) }
        compose.onNodeWithTag("ha_status").assertTextEquals("Linked.")
        compose.onNodeWithText("Saved. Paste a new token to replace it.").assertExists()
        compose.onNodeWithText("http://192.168.1.20:8123/").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun checkingShowsCheckingAndChecksOnce() {
        oneOfEach()
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        showHost(saved = false)
        typeAddressAndToken()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitFor { api.calls.contains("check") }
        compose.onNodeWithTag("ha_check").assertTextEquals("Checking…").assertIsNotEnabled()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitForIdle()
        assertThat(api.calls.count { it == "check" }).isEqualTo(1)
        gate.complete(Unit)
        compose.waitFor { compose.onAllNodes(hasText("Check")).fetchSemanticsNodes().isNotEmpty() }
        assertThat(api.calls.count { it == "check" }).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun linkedShowsThePickers() {
        oneOfEach()
        thermostat.lineText.value = "Hall · 20.8 °C · heating to 21.2 °C"
        showHost()
        compose.waitFor { compose.onAllNodes(hasText("Hall")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ha_device_vacuum").assertExists()
        assertThat(api.calls.count { it == "states" }).isEqualTo(1)
        compose.onNodeWithTag("ha_choice_vacuum_0").assertTextEquals("Robot")
        compose.onNodeWithTag("ha_none_vacuum").assertTextEquals("None")
        compose.onNodeWithTag("ha_choice_climate_0").assertTextEquals("Hall")
        compose.onNodeWithTag("ha_none_climate").assertTextEquals("None")
        compose.onAllNodes(hasContentDescription("Hoover")).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Thermostat")).assertCountEquals(1)
        compose.onNodeWithTag("ha_line_climate").assertTextEquals("Hall · 20.8 °C · heating to 21.2 °C")
        compose.onNodeWithTag("ha_choice_vacuum_0").performScrollTo().performClick()
        compose.waitFor { hoover.picks.isNotEmpty() }
        assertThat(hoover.picks).containsExactly("vacuum.hoover")
        compose.onNodeWithTag("ha_none_vacuum").performScrollTo().performClick()
        compose.waitFor { hoover.picks.size == 2 }
        assertThat(hoover.picks).containsExactly("vacuum.hoover", null).inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSameLinkCheckReadsTheListsAgain() {
        oneOfEach()
        showHost()
        compose.waitFor { compose.onAllNodes(hasText("Hall")).fetchSemanticsNodes().isNotEmpty() }
        assertThat(api.calls.count { it == "states" }).isEqualTo(1)
        api.states["climate.den"] = entityState("climate.den", "off", mapOf("friendly_name" to "Den"))
        compose.onNodeWithTag("ha_address").performTextInput("")
        compose.onNodeWithTag("ha_check").performScrollTo().performClick()
        compose.waitFor { compose.onAllNodes(hasText("Den")).fetchSemanticsNodes().isNotEmpty() }
        assertThat(api.calls.count { it == "states" }).isEqualTo(2)
        assertThat(access.asked).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: linking reads the lists once (Robolectric folds the link appearing and Check ending into one frame, so the doubled read does not show here). */
    @Test
    fun aFirstLinkReadsTheListsOnce() {
        oneOfEach()
        showHost(saved = false)
        typeAddressAndToken()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitFor { compose.onAllNodes(hasText("Hall")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitFor { compose.onAllNodes(hasText("Check")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitFor { hoover.picks.isNotEmpty() }
        compose.waitForIdle()
        // One read for Check's automatic picks, one for the lists.
        assertThat(api.calls.count { it == "states" }).isEqualTo(2)
    }

    @Test
    fun noThermostatSaysSo() {
        api.states["vacuum.hoover"] = entityState("vacuum.hoover", "docked", mapOf("friendly_name" to "Robot"))
        showHost()
        compose.waitFor { compose.onAllNodes(hasText("Home Assistant has no thermostat.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ha_none_found_climate").assertTextEquals("Home Assistant has no thermostat.")
        compose.onAllNodes(hasText("Home Assistant has no hoover.")).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theTokenIsNeverShownOrKept() {
        oneOfEach()
        showHost(saved = false)
        typeAddressAndToken()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitFor { compose.onAllNodes(hasText("Linked.")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitFor { compose.onAllNodes(hasText("Saved. Paste a new token to replace it.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("test-token", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("test-token", substring = true)).assertCountEquals(0)
        val typed = compose.onNodeWithTag("ha_token").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertThat(typed).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theClipboardIsClearedWhenItHoldsTheToken() {
        oneOfEach()
        linkedWithTokenOnTheClipboard("test-token")
        assertThat(clipboard().primaryClip).isNull()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theClipboardKeepsAnythingElse() {
        oneOfEach()
        linkedWithTokenOnTheClipboard("Milk")
        assertThat(clipboard().primaryClip?.getItemAt(0)?.text?.toString()).isEqualTo("Milk")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aProblemShowsInTheStatus() {
        api.failAll = HaProblem.TOKEN_REFUSED
        showHost()
        compose.waitFor { ha.problem.value == HaProblem.TOKEN_REFUSED }
        compose.onNodeWithTag("ha_status")
            .assertTextEquals("Home Assistant no longer accepts Culvery’s token. Paste a new one below, then Check.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** With a link-wide problem the page says it once, in the status, and the device sections stay quiet. */
    @Test
    fun aLinkWideProblemIsNotRepeatedUnderTheDevices() {
        oneOfEach()
        val sentence = "Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations."
        hoover.lineText.value = sentence
        thermostat.lineText.value = sentence
        api.failAll = HaProblem.TOKEN_REFUSED
        showHost()
        compose.waitFor { ha.problem.value == HaProblem.TOKEN_REFUSED }
        compose.onNodeWithTag("ha_device_vacuum").assertExists()
        compose.onNodeWithTag("ha_line_vacuum").assertDoesNotExist()
        compose.onNodeWithTag("ha_line_climate").assertDoesNotExist()
        compose.onAllNodes(hasText("Add a new one in Settings", substring = true)).assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aRefusedTokenShowsItsLineAndKeepsTheField() {
        api.failAll = HaProblem.TOKEN_REFUSED
        showHost(saved = false)
        typeAddressAndToken()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitFor { compose.onAllNodes(hasText("Home Assistant didn’t accept that token. Create a new one and paste it here.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ha_check_line").assertTextEquals("Home Assistant didn’t accept that token. Create a new one and paste it here.")
        assertThat(access.asked).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aRefusedPinShowsNoLineAndKeepsTheLinkUnsaved() {
        access.role = Role.ADULT
        showHost(saved = false)
        typeAddressAndToken()
        compose.onNodeWithTag("ha_check").performClick()
        compose.waitFor { access.asked.isNotEmpty() }
        compose.waitFor { compose.onAllNodes(hasText("Check")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ha_check_line").assertTextEquals("")
        assertThat(ha.link.value).isNull()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun thePageWatchesWhileShown() {
        var shown by mutableStateOf(true)
        showHost(shown = { shown })
        compose.waitFor { ha.watching == 1 }
        shown = false
        compose.waitFor { ha.watching == 0 }
        assertThat(ha.watching).isEqualTo(0)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
